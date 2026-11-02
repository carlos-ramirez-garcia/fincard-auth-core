# ============================================================
# STAGE 1 - BUILD: compila el multi-modulo (espejo del core:
# misma receta, distinto modulo empaquetado)
# ============================================================
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Poms primero: capa cacheada de dependencias
COPY pom.xml .
COPY authorization-core/pom.xml authorization-core/
COPY transaction-audit/pom.xml transaction-audit/
RUN mvn -B -q dependency:go-offline

# Fuente despues
COPY authorization-core/src authorization-core/src
COPY transaction-audit/src transaction-audit/src

# Solo el audit (+ su parent)
RUN mvn -B -q package -DskipTests -pl transaction-audit -am

# ============================================================
# STAGE 2 - RUNTIME: JRE + jar (puerto 8081)
# ============================================================
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /workspace/transaction-audit/target/transaction-audit-1.0.0.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
