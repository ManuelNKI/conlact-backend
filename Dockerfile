# ==========================================
# ETAPA 1: Construcción (Build Stage)
# ==========================================
FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /app

# Copiar Maven wrapper y descriptor de dependencias
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw

# Descargar dependencias en caché de Docker
RUN ./mvnw dependency:go-offline -B

# Copiar código fuente y compilar omitiendo pruebas para agilizar el build
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# ==========================================
# ETAPA 2: Ejecución ligera (Runtime Stage)
# ==========================================
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Crear usuario sin privilegios por seguridad
RUN addgroup -S conlact && adduser -S conlact -G conlact
USER conlact

# Copiar el artefacto empaquetado desde la etapa de compilación
COPY --from=builder /app/target/*.jar app.jar

ENV PORT=8080
ENV SPRING_PROFILES_ACTIVE=local

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
