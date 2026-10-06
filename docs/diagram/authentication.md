# Kiến Trúc & Luồng Tương Tác Module Authentication & JWT

Tài liệu thiết kế chi tiết kiến trúc tầng, mô hình dữ liệu và sequence diagram luồng xác thực Stateless JWT trong hệ thống.

---

## 1. Kiến Trúc Bộ Lọc Bảo Mật (Spring Security & JWT Filter Chain)

Hệ thống áp dụng mô hình bảo mật không lưu trạng thái phiên (stateless), xác thực qua Bearer Token:

```mermaid
flowchart TD
    subgraph Client["HTTP Client"]
        REQ["Request (Authorization: Bearer &lt;token&gt;)"]
    end

    subgraph SecurityFilterChain["Spring Security Filter Chain"]
        JAF["JwtAuthenticationFilter (OncePerRequestFilter)"]
        JTP["JwtTokenProvider (HMAC-SHA256)"]
        SCH["SecurityContextHolder (UserPrincipal)"]
        JAEP["JwtAuthenticationEntryPoint (RFC 7807)"]
        AUTHZ["AuthorizationFilter (Permit / Authenticated)"]
    end

    subgraph ControllerLayer["Controller Layer"]
        AC["AuthController (/api/v1/auth/**)"]
        WC["Protected Controllers (/api/v1/wallets, etc.)"]
    end

    subgraph ServiceLayer["Service Layer"]
        AS["AuthService / AuthServiceImpl"]
        WS["WalletService (Auto-provision)"]
    end

    subgraph DatabaseLayer["Database (PostgreSQL)"]
        UDB[(users)]
        WDB[(wallets)]
    end

    REQ --> JAF
    JAF -->|Validate Token| JTP
    JTP -->|Token Valid| SCH
    JTP -.->|Token Invalid / Missing| AUTHZ
    SCH --> AUTHZ
    AUTHZ -->|Public Endpoint| AC
    AUTHZ -->|Protected & Valid Principal| WC
    AUTHZ -.->|Protected & Anonymous| JAEP
    JAEP -.->|HTTP 401 ProblemDetail| REQ
    AC --> AS
    AS --> WS
    AS --> UDB
    WS --> WDB
```

---

## 2. Mô Hình Thực Thể Người Dùng & Quan Hệ Ví (ERD)

```mermaid
erDiagram
    users ||--o{ wallets : "owns (1:N)"
    users {
        uuid id PK "gen_random_uuid()"
        varchar username UK "Unique, 3-50 chars"
        varchar email UK "Unique, RFC 5322"
        varchar password_hash "BCrypt (cost=10)"
        varchar full_name "Legal name (UTF-8)"
        varchar status "ACTIVE, SUSPENDED, DELETED"
        timestamptz created_at "Creation timestamp"
        timestamptz updated_at "Update timestamp"
    }

    wallets {
        uuid id PK "gen_random_uuid()"
        varchar user_id FK "References users.id"
        varchar wallet_type "USER or SYSTEM"
        varchar currency "ISO 4217 (USD, VND)"
        bigint available_balance "Balance in minor units"
        bigint ledger_balance "Settled ledger balance"
        varchar status "ACTIVE, FROZEN, CLOSED"
        bigint version "Optimistic Locking"
        timestamptz created_at "Creation timestamp"
        timestamptz updated_at "Update timestamp"
    }
```

---

## 3. Luồng Đăng Ký Tài Khoản (Registration Sequence)

Người dùng đăng ký tài khoản mới; hệ thống băm mật khẩu, lưu thông tin và tự động mở một ví USER mặc định (USD) trong cùng một transaction:

```mermaid
sequenceDiagram
    autonumber
    actor Client as HTTP Client
    participant AC as AuthController
    participant AS as AuthServiceImpl
    participant UR as UserRepository
    participant PE as PasswordEncoder (BCrypt)
    participant WS as WalletService
    participant JTP as JwtTokenProvider
    participant DB as PostgreSQL

    Client->>AC: POST /api/v1/auth/register (username, email, password, currency)
    AC->>AS: register(RegisterRequest)
    AS->>UR: existsByUsername(username)
    UR->>DB: SELECT id FROM users WHERE username = ?
    DB-->>UR: false
    AS->>UR: existsByEmail(email)
    UR->>DB: SELECT id FROM users WHERE email = ?
    DB-->>UR: false
    AS->>PE: encode(rawPassword)
    PE-->>AS: passwordHash
    AS->>UR: save(User)
    UR->>DB: INSERT INTO users VALUES (...)
    DB-->>UR: User persisted (userId)
    AS->>WS: createWallet(userId, currency, USER)
    WS->>DB: INSERT INTO wallets VALUES (...)
    DB-->>WS: Wallet persisted (defaultWalletId)
    AS->>JTP: generateToken(userId, username)
    JTP-->>AS: accessToken (JWT)
    AS-->>AC: AuthResponse (accessToken, userId, defaultWalletId)
    AC-->>Client: HTTP 201 Created (JSON)
```

---

## 4. Luồng Đăng Nhập & Cấp Phát Token (Login Sequence)

```mermaid
sequenceDiagram
    autonumber
    actor Client as HTTP Client
    participant AC as AuthController
    participant AS as AuthServiceImpl
    participant UR as UserRepository
    participant PE as PasswordEncoder (BCrypt)
    participant WS as WalletService
    participant JTP as JwtTokenProvider

    Client->>AC: POST /api/v1/auth/login (username, password)
    AC->>AS: login(LoginRequest)
    AS->>UR: findByUsername(username)
    alt User not found
        UR-->>AS: Optional.empty()
        AS-->>AC: throw InvalidCredentialsException (401)
        AC-->>Client: HTTP 401 Unauthorized (ProblemDetail)
    else User exists
        UR-->>AS: Optional.of(user)
        AS->>PE: matches(rawPassword, user.passwordHash)
        alt Password does not match
            PE-->>AS: false
            AS-->>AC: throw InvalidCredentialsException (401)
            AC-->>Client: HTTP 401 Unauthorized (ProblemDetail)
        else Password valid
            PE-->>AS: true
            AS->>WS: getWalletsByUserId(userId)
            WS-->>AS: List&lt;WalletResponse&gt;
            AS->>JTP: generateToken(userId, username)
            JTP-->>AS: accessToken
            AS-->>AC: AuthResponse (accessToken, userId, defaultWalletId)
            AC-->>Client: HTTP 200 OK (JSON)
        end
    end
```

---

## 5. Luồng Xác Thực Request Có Bearer Token (Authenticated Request Flow)

Xử lý các request truy cập tài nguyên được bảo vệ (ví dụ: `GET /api/v1/wallets`):

```mermaid
sequenceDiagram
    autonumber
    actor Client as HTTP Client
    participant JAF as JwtAuthenticationFilter
    participant JTP as JwtTokenProvider
    participant SCH as SecurityContextHolder
    participant WC as WalletController
    participant WS as WalletService

    Client->>JAF: GET /api/v1/wallets (Header: Authorization: Bearer &lt;token&gt;)
    JAF->>JAF: Extract Bearer token string
    alt Missing or Malformed Token
        JAF->>Client: Delegated to JwtAuthenticationEntryPoint (HTTP 401)
    else Token Present
        JAF->>JTP: validateToken(token)
        alt Token Valid
            JTP-->>JAF: true
            JAF->>JTP: getUserIdFromToken(token), getUsernameFromToken(token)
            JTP-->>JAF: userId, username
            JAF->>SCH: setAuthentication(UsernamePasswordAuthenticationToken)
            JAF->>WC: Proceed to Controller
            WC->>WC: Extract @AuthenticationPrincipal UserPrincipal
            WC->>WS: getWalletsByUserId(principal.userId)
            WS-->>WC: List&lt;WalletResponse&gt;
            WC-->>Client: HTTP 200 OK (JSON)
        else Token Expired or Invalid Signature
            JTP-->>JAF: false
            JAF->>Client: Delegated to JwtAuthenticationEntryPoint (HTTP 401)
        end
    end
```

---

## 6. Máy Trạng Thái Người Dùng (User Lifecycle State Machine)

```mermaid
stateDiagram-v2
    [*] --> ACTIVE : Register & verify
    ACTIVE --> SUSPENDED : Admin lock / Fraud detected
    SUSPENDED --> ACTIVE : Admin unlock / KYC cleared
    ACTIVE --> DELETED : Account closure requested
    SUSPENDED --> DELETED : Account terminated
    DELETED --> [*] : Terminated (Cannot login or transact)
```

- **ACTIVE**: Đăng nhập, mở ví, thực hiện nạp/rút/chuyển tiền bình thường.
- **SUSPENDED**: Bị khóa đăng nhập, từ chối cấp token mới, API báo `account-disabled`.
- **DELETED**: Tài khoản bị đóng vĩnh viễn, vô hiệu hóa toàn bộ quyền truy cập.
