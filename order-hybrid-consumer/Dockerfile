# Use the eclipse-temurin image as a base
FROM eclipse-temurin:21

# Set the working directory inside the container
WORKDIR /app

# Copy the Maven or Gradle build artifact to the container
COPY target/consumer-0.0.1-SNAPSHOT.jar consumer-0.0.1-SNAPSHOT.jar

# Command to run the application
CMD ["java", "-jar", "consumer-0.0.1-SNAPSHOT.jar"]