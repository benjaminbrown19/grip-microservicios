# Etapa 1: compila y EJECUTA LAS PRUEBAS UNITARIAS con Maven y Java 25.
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
ENV MAVEN_OPTS="-Xmx512m"
COPY pom.xml .
COPY src ./src
RUN mvn -B package

# Etapa 2: imagen liviana que solo ejecuta el JAR.
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-Xmx384m", "-jar", "app.jar"]
