# ============================================================
# STAGE 1 - BUILD: compila el multi-modulo dentro de un
# contenedor con Maven+JDK (nadie necesita Maven instalado
# en la maquina - el build es reproducible en cualquier PC)
# ============================================================
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Poms primero: esta capa cachea las dependencias
COPY pom.xml .
COPY authorization-core/pom.xml authorization-core/
COPY transaction-audit/pom.xml transaction-audit/
RUN mvn -B -q dependency:go-offline

# Fuente despues: si solo cambias .java, las deps no se re-descargan
COPY authorization-core/src authorization-core/src
COPY transaction-audit/src transaction-audit/src

# Solo el core (+ su parent); los tests van aparte (Fase 7)
RUN mvn -B -q package -DskipTests -pl authorization-core -am

# ============================================================
# STAGE 2 - RUNTIME: imagen final con SOLO el JRE + el jar
# ============================================================
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /workspace/authorization-core/target/authorization-core-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
