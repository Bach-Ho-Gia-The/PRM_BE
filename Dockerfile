# ==========================================
# STAGE 1: Build JAR & Tận dụng Docker Cache
# ==========================================
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Copy các file cấu hình trước để cache dependencies
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

RUN chmod +x mvnw

# Tải trước thư viện (nếu pom.xml không đổi thì bước này dùng cache)
RUN ./mvnw dependency:go-offline -B

# Copy mã nguồn và đóng gói
COPY src ./src
RUN ./mvnw clean package -DskipTests

# ==========================================
# STAGE 2: Runtime Image (Siêu nhẹ & An toàn)
# ==========================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Tạo user không có đặc quyền root để chạy container
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=builder --chown=spring:spring /app/target/*.jar app.jar

ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -Duser.timezone=Asia/Ho_Chi_Minh \
               -Djava.net.preferIPv4Stack=true \
               -Djava.net.preferIPv4Addresses=true"

EXPOSE 8088

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]