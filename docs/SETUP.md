# Hướng Dẫn Cài Đặt & Khởi Tạo Dự Án (SETUP.md)

Tài liệu chi tiết hướng dẫn thiết lập môi trường, tạo cấu trúc thư mục, cấu hình Maven, Docker và các câu lệnh CLI cần thiết để khởi chạy **Mini Digital Wallet (mini-digital-wallet)**.

---

## 1. Yêu Cầu Môi Trường (Prerequisites)

Cần cài đặt các công cụ sau trên máy:

| Công cụ                            | Phiên bản yêu cầu                  | Lệnh kiểm tra            |
| :----------------------------------- | :------------------------------------- | :------------------------- |
| **Java Development Kit (JDK)** | Java 21 (Eclipse Temurin / OpenJDK 21) | `java -version`          |
| **Apache Maven**               | 3.9+                                   | `mvn -version`           |
| **Docker Engine & Compose**    | Docker v24+, Compose v2+               | `docker compose version` |
| **Git**                        | 2.40+                                  | `git --version`          |
| **grpcurl (Tùy chọn)**       | 1.8+ (Để test gRPC qua CLI)          | `grpcurl --version`      |

---

## 2. Khởi Tạo Dự Án (Project Initialization)

Bạn có thể chọn 1 trong 2 cách sau để bắt đầu:

### Cách 1: Khởi tạo Tự động qua Spring Initializr CLI (Khuyên Dùng)

Sử dụng lệnh `curl` gửi yêu cầu đến máy chủ Spring Initializr chính thức để tự động sinh toàn bộ cấu trúc thư mục, `pom.xml`, và Maven wrapper `mvnw`:

#### Trên Windows (PowerShell):

```powershell
curl.exe -L "https://start.spring.io/starter.zip?type=maven-project&language=java&javaVersion=21&groupId=com.minidigitalwallet&artifactId=mini-digital-wallet&name=mini-digital-wallet&packageName=com.minidigitalwallet&dependencies=web,data-jpa,data-redis,validation,actuator,postgresql,flyway,lombok" -o mini-digital-wallet.zip

# Giải nén dự án vào thư mục hiện tại:
Expand-Archive -Path mini-digital-wallet.zip -DestinationPath . -Force
Remove-Item mini-digital-wallet.zip
```

#### Trên Linux / macOS (Bash):

```bash
curl https://start.spring.io/starter.zip \
  -d type=maven-project \
  -d language=java \
  -d bootVersion=3.3.2 \
  -d javaVersion=21 \
  -d groupId=com.minidigitalwallet \
  -d artifactId=mini-digital-wallet \
  -d name=mini-digital-wallet \
  -d packageName=com.minidigitalwallet \
  -d dependencies=web,data-jpa,data-redis,validation,actuator,postgresql,flyway,lombok \
  -o mini-digital-wallet.zip

# Giải nén dự án:
unzip -o mini-digital-wallet.zip -d .
rm mini-digital-wallet.zip
```

> **Lưu ý sau khi giải nén**: Cần bổ sung các dependency gRPC và `protobuf-maven-plugin` vào file `pom.xml` (xem chi tiết ở mục 3.1 bên dưới) và tạo thêm thư mục `src/main/proto`.

---

### Cách 2: Khởi tạo Thủ công Cây Thư mục (Manual Scaffolding)

Nếu muốn tự tay dựng từng thư mục theo chuẩn Clean Architecture:

#### Trên Windows (PowerShell):

```powershell
New-Item -ItemType Directory -Force -Path `
  "src/main/proto", `
  "src/main/resources/db/migration", `
  "src/main/java/com/minidigitalwallet/config", `
  "src/main/java/com/minidigitalwallet/domain/model", `
  "src/main/java/com/minidigitalwallet/domain/exception", `
  "src/main/java/com/minidigitalwallet/repository", `
  "src/main/java/com/minidigitalwallet/service/impl", `
  "src/main/java/com/minidigitalwallet/transport/rest/dto", `
  "src/main/java/com/minidigitalwallet/transport/grpc", `
  "src/test/java/com/minidigitalwallet"
```

#### Trên Linux / macOS (Bash):

```bash
mkdir -p src/main/proto \
  src/main/resources/db/migration \
  src/main/java/com/minidigitalwallet/{config,domain/model,domain/exception,repository,service/impl,transport/rest/dto,transport/grpc} \
  src/test/java/com/minidigitalwallet
```

---

## 3. Tạo Các File Cấu Hình Cốt Lõi

### 3.1 File `pom.xml` Chuẩn Đầy Đủ (Đã kèm gRPC & Protobuf Plugin)

Tạo file `pom.xml` tại thư mục gốc:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.2</version>
        <relativePath/>
    </parent>

    <groupId>com.minidigitalwallet</groupId>
    <artifactId>mini-digital-wallet</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>mini-digital-wallet</name>
    <description>Banking-Grade Mini Digital Wallet Service in Java 21</description>

    <properties>
        <java.version>21</java.version>
        <grpc.version>1.65.1</grpc.version>
        <protobuf.version>3.25.3</protobuf.version>
        <grpc-spring-boot.version>3.1.0.RELEASE</grpc-spring-boot.version>
        <os-maven-plugin.version>1.7.1</os-maven-plugin.version>
        <protobuf-maven-plugin.version>0.6.1</protobuf-maven-plugin.version>
    </properties>

    <dependencies>
        <!-- Spring Boot Starters -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

        <!-- Database Driver & Migration -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-database-postgresql</artifactId>
        </dependency>

        <!-- gRPC & Protobuf -->
        <dependency>
            <groupId>net.devh</groupId>
            <artifactId>grpc-server-spring-boot-starter</artifactId>
            <version>${grpc-spring-boot.version}</version>
        </dependency>
        <dependency>
            <groupId>io.grpc</groupId>
            <artifactId>grpc-protobuf</artifactId>
            <version>${grpc.version}</version>
        </dependency>
        <dependency>
            <groupId>io.grpc</groupId>
            <artifactId>grpc-stub</artifactId>
            <version>${grpc.version}</version>
        </dependency>
        <dependency>
            <groupId>jakarta.annotation</groupId>
            <artifactId>jakarta.annotation-api</artifactId>
        </dependency>

        <!-- Developer Tools -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Testing -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <extensions>
            <extension>
                <groupId>kr.motd.maven</groupId>
                <artifactId>os-maven-plugin</artifactId>
                <version>${os-maven-plugin.version}</version>
            </extension>
        </extensions>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
            <!-- Tự động sinh Java code từ file Protobuf khi chạy mvn compile -->
            <plugin>
                <groupId>org.xolstice.maven.plugins</groupId>
                <artifactId>protobuf-maven-plugin</artifactId>
                <version>${protobuf-maven-plugin.version}</version>
                <configuration>
                    <protocArtifact>com.google.protobuf:protoc:${protobuf.version}:exe:${os.detected.classifier}</protocArtifact>
                    <pluginId>grpc-java</pluginId>
                    <pluginArtifact>io.grpc:protoc-gen-grpc-java:${grpc.version}:exe:${os.detected.classifier}</pluginArtifact>
                </configuration>
                <executions>
                    <execution>
                        <goals>
                            <goal>compile</goal>
                            <goal>compile-custom</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

---

### 3.2 Tạo File `docker-compose.yml`

Tạo file `docker-compose.yml` tại thư mục gốc để khởi chạy PostgreSQL 16 và Redis 7:

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:16-alpine
    container_name: ledger-postgres
    environment:
      POSTGRES_DB: ledger_db
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgrespassword
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data

  redis:
    image: redis:7-alpine
    container_name: ledger-redis
    ports:
      - "6379:6379"

volumes:
  pgdata:
```

---

### 3.3 Tạo File `application.yml`

Tạo file `src/main/resources/application.yml`:

```yaml
server:
  port: 8080

grpc:
  server:
    port: 9090

spring:
  application:
    name: core-ledger-service

  datasource:
    url: jdbc:postgresql://localhost:5432/ledger_db
    username: postgres
    password: postgrespassword
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      idle-timeout: 300000
      connection-timeout: 20000

  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
    show-sql: false

  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration

  data:
    redis:
      host: localhost
      port: 6379
      timeout: 2000ms

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
```

---

## 4. Các Lệnh Thao Tác CLI (Cheat Sheet)

### 4.1 Khởi động Hạ tầng Docker

```bash
# Bật Postgres và Redis ở chế độ background
docker compose up -d

# Xem log kiểm tra các service đã sẵn sàng
docker compose logs -f

# Kiểm tra trạng thái các container
docker compose ps

# Dừng hạ tầng khi không dùng
docker compose down
```

---

### 4.2 Biên dịch & Build Dự Án Maven

```bash
# 1. Dọn dẹp và tải toàn bộ dependencies
mvn clean

# 2. Biên dịch code và tự động sinh gRPC stubs từ .proto
mvn compile

# 3. Đóng gói file JAR (Bỏ qua unit test khi cần build nhanh)
mvn package -DskipTests

# 4. Chạy toàn bộ Unit Tests & Concurrency Tests
mvn test

# 5. Chỉ chạy riêng Concurrency Stress Test
mvn test -Dtest=LedgerConcurrencyTest
```

---

### 4.3 Khởi Chạy Ứng Dụng Spring Boot

```bash
# Cách 1: Chạy trực tiếp qua Maven plugin (Thích hợp cho dev)
mvn spring-boot:run

# Cách 2: Chạy file JAR đã đóng gói
java -jar target/core-ledger-service-0.0.1-SNAPSHOT.jar
```

---

### 4.4 Kiểm Tra Trạng Thái Hoạt Động (Health Checks)

```bash
# Kiểm tra Spring Boot Actuator
curl -X GET http://localhost:8080/actuator/health

# Kết quả mong đợi: {"status":"UP"}
```

---

## 5. Test API qua CLI (cURL & grpcurl)

### 5.1 Test REST API (cURL)

```bash
# 1. Tạo tài khoản 1
curl -X POST http://localhost:8080/api/v1/accounts \
  -H "Content-Type: application/json" \
  -d '{"owner_id": "USER_001", "currency": "USD", "daily_limit": 5000000}'

# 2. Nạp $1,000 (100000 cents) vào tài khoản 1
curl -X POST http://localhost:8080/api/v1/accounts/ACC_001/deposit \
  -H "Content-Type: application/json" \
  -d '{"reference_id": "DEP_001", "amount": 100000, "description": "Nap tien ban dau"}'

# 3. Tạo tài khoản 2
curl -X POST http://localhost:8080/api/v1/accounts \
  -H "Content-Type: application/json" \
  -d '{"owner_id": "USER_002", "currency": "USD", "daily_limit": 5000000}'

# 4. Chuyển $250 (25000 cents) từ tài khoản 1 sang tài khoản 2
curl -X POST http://localhost:8080/api/v1/transfers \
  -H "Content-Type: application/json" \
  -d '{
    "reference_id": "TX_TEST_001",
    "source_account_id": "ACC_001",
    "target_account_id": "ACC_002",
    "amount": 25000,
    "currency": "USD",
    "description": "Chuyen tien thu nghiem"
  }'

# 5. Xem sao kê tài khoản 1
curl -X GET http://localhost:8080/api/v1/accounts/ACC_001/ledgers
```

### 5.2 Test gRPC qua `grpcurl` (Port 9090)

```bash
# Tra cứu danh sách RPC services
grpcurl -plaintext localhost:9090 list

# Gọi RPC GetAccount
grpcurl -plaintext -d '{"account_id": "ACC_001"}' localhost:9090 com.minidigitalwallet.v1.LedgerService/GetAccount

# Gọi RPC Transfer
grpcurl -plaintext -d '{
  "reference_id": "GRPC_TX_001",
  "source_account_id": "ACC_001",
  "target_account_id": "ACC_002",
  "amount": 10000,
  "currency": "USD",
  "description": "gRPC transfer test"
}' localhost:9090 com.minidigitalwallet.v1.LedgerService/Transfer
```

---

## 6. Xử Lý Sự Cố Thường Gặp (Troubleshooting)

| Vấn đề                                                  | Nguyên nhân                                            | Cách khắc phục                                                                                 |
| :--------------------------------------------------------- | :------------------------------------------------------- | :------------------------------------------------------------------------------------------------ |
| **Lỗi Port `5432` hoặc `6379` already in use** | Đã có PostgreSQL/Redis local đang chạy ngầm.       | Tắt service local hoặc đổi port trong`docker-compose.yml` (vd: `5433:5432`).              |
| **Không tìm thấy stubs gRPC sau khi build**       | Maven chưa chạy sinh mã từ`.proto`.                | Chạy lệnh`mvn clean compile` hoặc kiểm tra thư mục `target/generated-sources/protobuf`. |
| **Lỗi Java 21 not found**                           | Phiên bản`JAVA_HOME` trỏ về JDK cũ (JDK 8/11/17). | Cài JDK 21 và thiết lập biến môi trường`JAVA_HOME` trỏ tới JDK 21.                    |
| **Lỗi kết nối Redis / Postgres trong Docker**     | Container chưa khởi động xong.                       | Kiểm tra`docker compose ps`, xem log bằng `docker compose logs postgres`.                   |
