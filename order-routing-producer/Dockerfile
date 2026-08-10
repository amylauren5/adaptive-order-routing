# Use the eclipse-temurin image as a base
FROM eclipse-temurin:21

# Set the working directory inside the container
WORKDIR /app

# Install the GNU OpenMP runtime required by XGBoost4J
RUN apt-get update \
    && apt-get install -y --no-install-recommends libgomp1 \
    && rm -rf /var/lib/apt/lists/*

# Copy the Maven or Gradle build artifact to the container
COPY target/producer-0.0.1-SNAPSHOT.jar producer-0.0.1-SNAPSHOT.jar

# Expose the application port (default is 8080)
EXPOSE 8080

# Command to run the application
CMD ["java", "-jar", "producer-0.0.1-SNAPSHOT.jar"]