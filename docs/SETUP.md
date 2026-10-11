# Hướng Dẫn Cài Đặt & Khởi Tạo Dự Án (SETUP.md)

Tài liệu chi tiết hướng dẫn thiết lập môi trường, tạo cấu trúc thư mục, cấu hình Maven, Docker và các câu lệnh CLI cần thiết để khởi chạy **Mini Digital Wallet (mini-digital-wallet)**.

---

## 1. Yêu Cầu Môi Trường (Prerequisites)

Cần cài đặt các công cụ sau trên máy:

| Công cụ                            | Phiên bản yêu cầu                  | Lệnh kiểm tra            |
| :----------------------------------- | :------------------------------------- | :------------------------- |
| **Java Development Kit (JDK)** | Java 21 (Eclipse Temurin / OpenJDK 21) | `java -version`          |
| **Apache Maven**               | 3.9+ (hoặc dùng sẵn `mvnw` / `mvnw.cmd`) | `mvn -version`           |
| **Docker Engine & Compose**    | Docker v24+, Compose v2+ (bắt buộc cho test đồng thời Testcontainers) | `docker compose version` |
| **Git**                        | 2.40+                                  | `git --version`          |

---

## 2. Cấu Trúc Dự Án Thực Tế (Package-by-Layer)

```
src/main/java/com/minidigitalwallet/
├── controller/      # REST: Auth, Wallet, PaymentMethod, Transfer, Ledger
├── service/         # Interface nghiệp vụ + LedgerFacade (@Retryable)
│   └── impl/        # WalletServiceImpl, TransferServiceImpl, LedgerServiceImpl, ...
├── repository/      # Spring Data JPA (Wallet, Ledger*, PaymentMethod, User)
├── entity/          # Wallet, LedgerTransaction, LedgerEntry, PaymentMethod, User
│   └── enums/       # WalletStatus/Type, TransactionType, EntryType, ...
├── dto/request/     # CreateWalletRequest, TopUp/Withdraw/Transfer/ReversalRequest, ...
├── dto/response/    # WalletResponse, TransferResponse, LedgerTransactionResponse, ...
├── exception/       # AppException + GlobalExceptionHandler (RFC 7807)
└── security/        # JWT: SecurityConfig, JwtTokenProvider, JwtAuthenticationFilter, ...

src/main/resources/db/migration/
├── V1__init_schema.sql        # wallets + payment_methods + 3 ví SYSTEM
├── V2__init_ledger.sql        # ledger_transactions + ledger_entries
├── V3__init_users.sql         # users (auth)
└── V4__ledger_entry_line_no.sql  # line_no + UNIQUE(transaction_id, line_no)
```

> Các shell trống Phase 3–5 (idempotency/outbox/reconciliation/Kafka/gRPC/Redis) đã bị xóa khỏi code để giữ service tối giản; đặc tả các phase đó vẫn nằm trong `PLAN.md`.

---

## 3. Tạo Các File Cấu Hình Cốt Lõi

### 3.1 File `pom.xml` (tóm tắt — xem file thực tế ở thư mục gốc)

| Nhóm | Artifact |
| :--- | :--- |
| Web/API | `spring-boot-starter-web`, `validation`, `actuator`, `security`, `jjwt-*` (0.12.6) |
| Dữ liệu | `spring-boot-starter-data-jpa`, `postgresql` (runtime), `flyway-core` + `flyway-database-postgresql` |
| Concurrency | `spring-retry` + `spring-boot-starter-aop` (cho `@Retryable` ở `LedgerFacade`) |
| Test | `spring-boot-starter-test`, `security-test`, `h2` (unit test), `testcontainers` `junit-jupiter` + `postgresql` (test đồng thời + migration) |

> Đã loại bỏ so với bản thiết kế cũ: `spring-boot-starter-data-redis`, `spring-kafka`, gRPC/protobuf — không có code nào dùng tới. Thêm lại khi làm Phase 3–4.

<details>
<summary>Bản thiết kế cũ (tham khảo, đã lỗi thời)</summary>

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
    </properties>

    <!-- Xem pom.xml thực tế ở thư mục gốc để có danh sách đầy đủ.
         Đoạn XML dưới đây chỉ còn giá trị lịch sử. -->
    <!--
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

        <!-- gRPC & Protobuf: ĐÃ LOẠI BỎ (không có code nào dùng).
             Giữ lại đoạn dưới để tham khảo khi cần bổ sung ở phase sau.
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
        -->

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
            <!-- Tự động sinh Java code từ file Protobuf khi chạy mvn compile (ĐÃ LOẠI BỎ)
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
            -->
        </plugins>
    </build>
</project>
```
</details>

---

### 3.2 File `docker-compose.yml` (thực tế — chỉ còn PostgreSQL 16)

```yaml
services:
  postgres:
    image: postgres:16-alpine
    container_name: wallet-postgres
    environment:
      POSTGRES_DB: wallet_db
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgrespassword
    ports:
      - "5436:5432"   # host 5436 để tránh đụng Postgres local
    volumes:
      - pgdata:/var/lib/postgresql/data
```

> Redis đã bỏ khỏi compose vì chưa có code nào dùng (sẽ thêm lại ở Phase 3 cho rate limiting).

<details>
<summary>Bản thiết kế cũ (tham khảo, đã lỗi thời)</summary>

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:16-alpine
    container_name: wallet-postgres
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
</details>

---

### 3.3 File `application.properties` (thực tế: `src/main/resources/application.properties`)

```properties
server.port=8080
spring.datasource.url=${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5436/wallet_db}
spring.datasource.username=${SPRING_DATASOURCE_USERNAME:postgres}
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD:postgrespassword}
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.open-in-view=false
spring.flyway.enabled=${SPRING_FLYWAY_ENABLED:true}
spring.flyway.baseline-on-migrate=true
spring.flyway.locations=classpath:db/migration
app.jwt.secret=...
app.jwt.expiration-ms=86400000
```

Test dùng H2 in-memory cho unit test (`src/test/resources/application.properties`, `ddl-auto=create-drop`, Flyway tắt); riêng `LedgerConcurrencyTest`, `DoubleEntryInvariantTest`, `FlywayMigrationTest` dùng Testcontainers PostgreSQL 16 qua `@DynamicPropertySource` — yêu cầu Docker đang chạy.

<details>
<summary>Bản `application.yml` thiết kế cũ (tham khảo, đã lỗi thời)</summary>

```yaml

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
</details>

---

## 4. Các Lệnh Thao Tác CLI (Cheat Sheet)

### 4.1 Khởi động Hạ tầng Docker

```bash
# Bật Postgres ở chế độ background (Redis đã bỏ)
docker compose up -d

# Xem log kiểm tra service đã sẵn sàng
docker compose logs -f

# Kiểm tra trạng thái các container
docker compose ps

# Dừng hạ tầng khi không dùng
docker compose down
```

---

### 4.2 Biên dịch & Build Dự Án Maven

Dùng Maven Wrapper (`mvnw.cmd` trên Windows, `./mvnw` trên Linux/macOS):

```bash
# 1. Dọn dẹp và tải toàn bộ dependencies
./mvnw clean

# 2. Biên dịch code
./mvnw compile

# 3. Đóng gói file JAR (Bỏ qua test khi cần build nhanh)
./mvnw package -DskipTests

# 4. Chạy toàn bộ test (55 test: unit H2 + đồng thời/integration Testcontainers)
#    Yêu cầu Docker đang chạy cho LedgerConcurrencyTest, DoubleEntryInvariantTest, FlywayMigrationTest
./mvnw test

# 5. Chỉ chạy riêng Concurrency Stress Test (PostgreSQL thật qua Testcontainers)
./mvnw test -Dtest=LedgerConcurrencyTest
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

## 5. Test API qua CLI (cURL)

> Chi tiết request/response xem `docs/API.md`. Ví dụ nhanh dưới đây (thay UUID ví/thẻ bằng giá trị thực tế):

```bash
# 1. Đăng ký user (tự tạo ví USD + trả JWT)
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username": "alice", "email": "alice@example.com", "password": "Password123!", "full_name": "Alice"}'

# 2. Liên kết thẻ (lưu với TOKEN, thay CARD_ID/WALLET_ID bên dưới)
curl -X POST http://localhost:8080/api/v1/payment-methods \
  -H "Content-Type: application/json" \
  -d '{"owner_id": "alice", "method_type": "DEBIT_CARD", "provider": "VISA", "card_number": "4242424242424242", "holder_name": "ALICE"}'

# 3. Nạp $500 (50000 cents) vào ví
curl -X POST http://localhost:8080/api/v1/transfers/topup \
  -H "Content-Type: application/json" -H "Idempotency-Key: TOPUP-DEMO-001" \
  -d '{"wallet_id": "WALLET_ID", "payment_method_id": "CARD_ID", "amount": 50000, "description": "Nap demo"}'

# 4. Chuyển P2P $150 (15000 cents) sang ví khác
curl -X POST http://localhost:8080/api/v1/transfers/p2p \
  -H "Content-Type: application/json" -H "Idempotency-Key: P2P-DEMO-001" \
  -d '{"source_wallet_id": "WALLET_ID_A", "destination_wallet_id": "WALLET_ID_B", "amount": 15000, "description": "Demo"}'

# 5. Hoàn tiền giao dịch (đảo bút toán append-only)
curl -X POST http://localhost:8080/api/v1/transfers/reversals \
  -H "Content-Type: application/json" -H "Idempotency-Key: REV-DEMO-001" \
  -d '{"original_transaction_id": "TX_ID", "description": "Hoan tien demo"}'

# 6. Xem sao kê ví
curl -X GET http://localhost:8080/api/v1/ledger/wallets/WALLET_ID/entries
```

---

## 6. Xử Lý Sự Cố Thường Gặp (Troubleshooting)

| Vấn đề                                                  | Nguyên nhân                                            | Cách khắc phục                                                                                 |
| :--------------------------------------------------------- | :------------------------------------------------------- | :------------------------------------------------------------------------------------------------ |
| **Lỗi Port `5436` already in use** | Đã có service local chiếm port 5436.       | Đổi port trong`docker-compose.yml` (vd: `5437:5432`) và cập nhật `SPRING_DATASOURCE_URL`.              |
| **Test Testcontainers báo Docker not found**       | Docker daemon chưa chạy.                | Khởi động Docker Desktop rồi chạy lại `./mvnw test`. |
| **Lỗi Java 21 not found**                           | Phiên bản`JAVA_HOME` trỏ về JDK cũ (JDK 8/11/17). | Cài JDK 21 và thiết lập biến môi trường`JAVA_HOME` trỏ tới JDK 21.                    |
| **Lỗi kết nối Postgres trong Docker**     | Container chưa khởi động xong.                       | Kiểm tra`docker compose ps`, xem log bằng `docker compose logs postgres`.                   |
