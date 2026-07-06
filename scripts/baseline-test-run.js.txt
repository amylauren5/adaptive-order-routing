const { spawn } = require('child_process');
const { ChartJSNodeCanvas } = require('chartjs-node-canvas');
const fs = require('fs');
const path = require('path');
const { PDFDocument } = require('pdf-lib');

const runCount = 30;
const testDurations = [];

const scaleFactor = 3;
const width = 800 * scaleFactor;
const height = 600 * scaleFactor;
const chartJSNodeCanvas = new ChartJSNodeCanvas({ width, height, backgroundColour: 'white' });

const outputDir = path.resolve(__dirname, './baseline-graph');
if (!fs.existsSync(outputDir)) {
    fs.mkdirSync(outputDir, { recursive: true });
}

function logWithTimestamp(message) {
    console.log(`[${new Date().toISOString()}] ${message}`);
}

function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

function runPostmanTests() {
    return new Promise((resolve, reject) => {
        const newman = spawn('newman', [
            'run', '../postman/pharma_producer_app.postman_collection.json',
            '--environment', '../postman/pharma_environment.postman_environment.json'
        ]);
        newman.on('close', code => code === 0 ? resolve() : reject(new Error(`Newman exited with code ${code}`)));
    });
}

function calculateStandardDeviation(values) {
    if (values.length === 0) return 0;
    const mean = values.reduce((a, b) => a + b, 0) / values.length;
    const variance = values.reduce((sum, val) => sum + Math.pow(val - mean, 2), 0) / values.length;
    return Math.sqrt(variance);
}

async function generateGraph(data) {
    const labels = data.map((_, i) => `Run ${i + 1}`);
    const configuration = {
        type: 'line',
        data: {
            labels,
            datasets: [{
                label: 'Postman Test Duration (seconds)',
                data,
                borderColor: 'blue',
                backgroundColor: 'rgba(0, 0, 255, 0.3)',
                fill: false,
                tension: 0.1
            }]
        },
        options: {
            responsive: false,
            plugins: {
                title: {
                    display: true,
                    text: 'Postman Test Durations (Baseline)',
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
                        text: 'Duration (s)',
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
    const timestamp = now.toISOString().replace(/[:.]/g, '-'); // sanitize timestamp

    const pngBuffer = await chartJSNodeCanvas.renderToBuffer(configuration, 'image/png');
    const pngFilename = path.join(outputDir, `baseline_test_graph_${timestamp}.png`);
    fs.writeFileSync(pngFilename, pngBuffer);
    logWithTimestamp(`PNG graph saved to ${pngFilename}`);

    return { pngBuffer, pngFilename };
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
        logWithTimestamp(`Starting baseline Postman tests (${runCount} runs)...`);
        for (let i = 0; i < runCount; i++) {
            logWithTimestamp(`Run ${i + 1}/${runCount} started...`);
            const start = new Date();
            try {
                await runPostmanTests();
            } catch (err) {
                logWithTimestamp(`Run ${i + 1} failed: ${err.message}`);
            }
            const end = new Date();
            const duration = (end - start) / 1000;
            testDurations.push(duration);
            logWithTimestamp(`Run ${i + 1} duration: ${duration.toFixed(2)}s`);
            await sleep(5000);
        }

        const mean = testDurations.reduce((a, b) => a + b, 0) / testDurations.length;
        const stdDev = calculateStandardDeviation(testDurations);
        logWithTimestamp(`All runs completed. Mean: ${mean.toFixed(2)}s, Std Dev: ${stdDev.toFixed(2)}s`);

        const { pngBuffer, pngFilename } = await generateGraph(testDurations);
        const pdfFilename = pngFilename.replace(/\.png$/, '.pdf');
        await convertPNGtoPDF(pngBuffer, pdfFilename);

        logWithTimestamp('Graph generation complete.');
    } catch (err) {
        console.error('Fatal error:', err);
    }
}

main();
