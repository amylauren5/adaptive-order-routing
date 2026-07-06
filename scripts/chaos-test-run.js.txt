const Docker = require('dockerode');
const http = require('http');
const { spawn } = require('child_process');
const { ChartJSNodeCanvas } = require('chartjs-node-canvas');
const fs = require('fs');
const path = require('path');
const { PDFDocument } = require('pdf-lib');

const docker = new Docker();

const targetContainers = ['rabbit-broker', 'axon-server', 'consumer-app'];
const recoveryTimes = {
    'rabbit-broker': [],
    'axon-server': [],
    'consumer-app': []
};
const postmanDurations = {
    'rabbit-broker': [],
    'axon-server': [],
    'consumer-app': []
};

const scaleFactor = 3;
const width = 800 * scaleFactor;
const height = 600 * scaleFactor;
const chartJSNodeCanvas = new ChartJSNodeCanvas({ width, height, backgroundColour: 'white' });

const outputDir = path.resolve(__dirname, './chaos-graph');
if (!fs.existsSync(outputDir)) {
    fs.mkdirSync(outputDir, { recursive: true });
}

function logWithTimestamp(message) {
    console.log(`[${new Date().toISOString()}] ${message}`);
}

function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

async function isContainerRunning(containerName) {
    try {
        const container = docker.getContainer(containerName);
        const data = await container.inspect();
        return data.State.Running;
    } catch (err) {
        if (err.statusCode === 404) return false;
        throw err;
    }
}

function injectFailure(containerName) {
    return new Promise((resolve, reject) => {
        logWithTimestamp(`Injecting failure into ${containerName}...`);
        const args = [
            'run', '--rm',
            '--network', 'ict3914-final-year-project_rabbit-network',
            '-v', '/var/run/docker.sock:/var/run/docker.sock',
            'gaiaadm/pumba', 'kill', '--signal', 'SIGKILL', containerName
        ];
        const proc = spawn('docker', args, { stdio: 'inherit' });
        proc.on('error', reject);
        proc.on('close', (code) => code === 0 ? resolve() : reject(new Error(`Failure injection failed on ${containerName} (code ${code})`)));
    });
}

async function restartContainer(containerName) {
    try {
        const container = docker.getContainer(containerName);
        await container.start();
        logWithTimestamp(`[${containerName}] restarted.`);
    } catch (err) {
        console.error(`[${containerName}] Restart error:`, err.message);
    }
}

async function isAxonReady() {
    return new Promise(resolve => {
        http.get('http://localhost:8024/actuator/health', res => {
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => {
                try {
                    const json = JSON.parse(data);
                    resolve(json.status === 'UP');
                } catch {
                    resolve(false);
                }
            });
        }).on('error', () => resolve(false));
    });
}

async function isContainerUp(containerName) {
    if (containerName === "rabbit-broker") {
        const url = new URL('http://localhost:15672/api/overview');
        const auth = Buffer.from('guest:guest').toString('base64');
        const options = {
            hostname: url.hostname,
            port: url.port,
            path: url.pathname,
            headers: { 'Authorization': `Basic ${auth}` }
        };
        return new Promise(resolve => {
            const req = http.get(options, res => resolve(res.statusCode >= 200 && res.statusCode < 400));
            req.on('error', () => resolve(false));
        });
    } else if (containerName === "axon-server") return await isAxonReady();
    else if (containerName === "consumer-app") return await waitForSpringReady(containerName);
    else return false;
}

async function waitForSpringReady(containerName, timeoutMs = 120000) {
    return new Promise(resolve => {
        const container = docker.getContainer(containerName);
        const streamOpts = { follow: true, stdout: true, stderr: true };
        let timer;
        container.logs(streamOpts, (err, stream) => {
            if (err) return resolve(false);
            timer = setTimeout(() => {
                if (stream.destroy) stream.destroy();
                resolve(false);
            }, timeoutMs);
            stream.on('data', chunk => {
                const log = chunk.toString();
                if (log.includes('Started') && log.includes('seconds')) {
                    clearTimeout(timer);
                    stream.destroy();
                    resolve(true);
                }
            });
            stream.on('end', () => { clearTimeout(timer); resolve(false); });
            stream.on('error', () => { clearTimeout(timer); resolve(false); });
        });
    });
}

async function monitorRecovery(containerName) {
    while (!(await isContainerRunning(containerName))) await sleep(1000);
    while (!(await isContainerUp(containerName))) await sleep(1000);
}

function runPostmanTestsWithTiming(containerName) {
    return new Promise((resolve, reject) => {
        const start = Date.now();
        const newman = spawn('newman', [
            'run', '../postman/pharma_producer_app.postman_collection.json',
            '--environment', '../postman/pharma_environment.postman_environment.json'
        ]);
        newman.on('close', code => {
            const end = Date.now();
            const duration = (end - start) / 1000;
            postmanDurations[containerName].push(duration);
            logWithTimestamp(`[${containerName}] Postman test duration: ${duration.toFixed(2)}s`);
            if (code === 0) resolve();
            else reject(new Error(`Newman exited with code ${code}`));
        });
    });
}

function calculateStandardDeviation(values) {
    if (values.length === 0) return 0;
    const mean = values.reduce((a, b) => a + b, 0) / values.length;
    const variance = values.reduce((sum, val) => sum + Math.pow(val - mean, 2), 0) / values.length;
    return Math.sqrt(variance);
}

async function generateRecoveryGraphPNG(data) {
    const labels = [];
    const datasets = [];
    const maxRuns = Math.max(...Object.values(data).map(arr => arr.length));
    for (let i = 1; i <= maxRuns; i++) labels.push(`Run ${i}`);
    Object.entries(data).forEach(([container, times], idx) => {
        datasets.push({
            label: container,
            data: times,
            borderColor: `hsl(${(idx * 120) % 360}, 70%, 50%)`,
            backgroundColor: `hsla(${(idx * 120) % 360}, 70%, 50%, 0.3)`,
            fill: false,
            tension: 0.1,
        });
    });
    const configuration = {
        type: 'line',
        data: { labels, datasets },
        options: {
            responsive: false,
            plugins: {
                title: {
                    display: true,
                    text: 'Container Recovery Times (seconds)',
                    font: { size: 30 }
                },
                legend: {
                    labels: {
                        font: { size: 25 }
                    }
                }
            },
            scales: {
                y: {
                    beginAtZero: true,
                    title: {
                        display: true,
                        text: 'Recovery Time (s)',
                        font: { size: 26 }
                    },
                    ticks: {
                        font: { size: 22 }
                    }
                },
                x: {
                    title: {
                        display: true,
                        text: 'Test Runs',
                        font: { size: 26 }
                    },
                    ticks: {
                        font: { size: 22 }
                    }
                }
            }
        }
    };

    const now = new Date();
    const timestamp = now.toISOString().replace(/[:.]/g, '-');

    const pngBuffer = await chartJSNodeCanvas.renderToBuffer(configuration, 'image/png');
    const pngFilename = path.join(outputDir, `recovery_times_graph_${timestamp}.png`);
    fs.writeFileSync(pngFilename, pngBuffer);
    logWithTimestamp(`PNG graph saved to ${pngFilename}`);

    // Return buffer and base filename for PDF
    return { pngBuffer, baseFilename: `recovery_times_graph_${timestamp}` };
}

async function convertPNGtoPDF(pngBuffer, pdfPath) {
    const pdfDoc = await PDFDocument.create();
    const pngImage = await pdfDoc.embedPng(pngBuffer);
    const page = pdfDoc.addPage([pngImage.width, pngImage.height]);
    page.drawImage(pngImage, {
        x: 0,
        y: 0,
        width: pngImage.width,
        height: pngImage.height,
    });
    const pdfBytes = await pdfDoc.save();
    fs.writeFileSync(pdfPath, pdfBytes);
    logWithTimestamp(`PDF graph saved to ${pdfPath}`);
}

async function main() {
    try {
        logWithTimestamp('Starting failure injection tests...');
        const maxFailures = 30;

        for (const containerToFail of targetContainers) {
            for (let i = 0; i < maxFailures; i++) {
                logWithTimestamp(`[Run ${i + 1}/${maxFailures}] Testing ${containerToFail}`);

                const postmanTestPromise = runPostmanTestsWithTiming(containerToFail);

                await injectFailure(containerToFail);
                const downTime = new Date();

                await restartContainer(containerToFail);
                try {
                    await postmanTestPromise;
                } catch (err) {
                    logWithTimestamp(`Postman test failed: ${err.message}`);
                }

                await monitorRecovery(containerToFail);
                const upTime = new Date();
                const recoveryDuration = (upTime - downTime) / 1000;
                recoveryTimes[containerToFail].push(recoveryDuration);

                const times = recoveryTimes[containerToFail];
                const mean = times.reduce((a, b) => a + b, 0) / times.length;
                const stdDev = calculateStandardDeviation(times);
                logWithTimestamp(`[${containerToFail}] Recovery times: ${times.map(t => t.toFixed(2)).join(', ')}`);
                logWithTimestamp(`[${containerToFail}] Mean (${times.length} runs): ${mean.toFixed(2)}s, Std Dev: ${stdDev.toFixed(2)}s`);

                // Capture mean Postman test duration every 30 runs
                const runsSoFar = postmanDurations[containerToFail].length;
                if (runsSoFar % 30 === 0) {
                    const last30 = postmanDurations[containerToFail].slice(-30);
                    const meanLast30 = last30.reduce((a, b) => a + b, 0) / last30.length;
                    logWithTimestamp(`[${containerToFail}] Mean Postman test duration for last 30 runs: ${meanLast30.toFixed(2)}s`);

                    const meanFile = path.join(outputDir, `${containerToFail}_mean_postman_duration_last_30.json`);
                    fs.writeFileSync(meanFile, JSON.stringify({ meanLast30, timestamp: new Date().toISOString() }, null, 2));
                }

                await sleep(5000);
            }
        }

        // Generate PNG + get base filename for PDF
        const { pngBuffer, baseFilename } = await generateRecoveryGraphPNG(recoveryTimes);
        const pdfPath = path.join(outputDir, `${baseFilename}.pdf`);
        await convertPNGtoPDF(pngBuffer, pdfPath);

        // Save postman durations
        fs.writeFileSync(
            path.join(outputDir, 'postman_durations_faults.json'),
            JSON.stringify(postmanDurations, null, 2)
        );
        logWithTimestamp('Saved Postman test durations (with faults).');

        logWithTimestamp('All tests completed. Final PDF graph generated.');
    } catch (err) {
        console.error('Fatal error:', err);
    }
}

main();
