# AGENTS.md

## 0. 기본 원칙
- `feat` 커밋과 `test` 커밋은 **쌍(pair)**으로 존재해야 하며, 같은 브랜치/PR 안에서 연속으로 커밋한다.
- 기능 구현 커밋과 테스트 작성 커밋은 **분리**한다. (하나의 커밋에 묶지 않음)

## 1. Project Context

You are working on **귀 편한 금융**, a senior-friendly AI voice banking
service for the **2026 KB IT's Your Life Hackathon**.

The backend is a **classic Spring Framework application, not Spring
Boot**.

Primary project goals:

-   Voice-driven account inquiry and money transfer.
-   Senior-friendly conversational UX.
-   Recipient discovery using contacts and transaction history.
-   Transfer confirmation and read-back before execution.
-   Risk Score based suspicious-transfer detection.
-   Utility bill / financial schedule reminders.
-   Mobile branch / visiting branch information.
-   Integration with a Vue + Capacitor frontend.

When implementing or modifying code, prioritize the hackathon MVP and
the existing project architecture over architectural perfection or
unnecessary abstraction.

------------------------------------------------------------------------

## 2. Technology Stack

### Backend

-   Java 17
-   Spring Framework 5.3.x
-   Spring MVC
-   Spring Security 5.x
-   OAuth2
-   JWT
-   MyBatis
-   MySQL 8
-   Gradle
-   WAR deployment
-   Tomcat 9
-   Jackson
-   SLF4J
-   Logback

### Frontend Integration Context

The frontend uses:

-   Vue 3
-   Capacitor
-   JavaScript unless the existing frontend code says otherwise
-   Native mobile capabilities where required, especially contacts

Backend changes must consider the API contract consumed by this
frontend.

> **Critical:** This project is NOT Spring Boot. Never introduce Spring
> Boot code, dependencies, configuration conventions, or embedded-server
> assumptions unless explicitly requested.

------------------------------------------------------------------------

## 3. Source of Truth Priority

Before implementing a feature, inspect the repository and follow this
priority:

1.  Existing working code and project structure
2.  the active API specification
3.  Existing DTO / VO / Mapper contracts
4.  Existing database schema and Mapper XML
5.  This `AGENTS.md`
6.  User instructions for the current task

Do not guess package names, table names, columns, endpoint paths, DTO
fields, or configuration locations when they can be verified from the
repository.

If documentation conflicts with actual working code, report the conflict
before making a contract-breaking change.

------------------------------------------------------------------------

## 4. API Contract

Before implementing or modifying an API:

- Inspect existing Controller mappings first.
- Inspect existing request/response DTOs.
- Check the current Notion API specification when available.
- Preserve existing frontend-backend compatibility.
- Reuse an existing endpoint when it already represents the required behavior.

Do not:

- Silently rename existing endpoints.
- Change HTTP methods without a clear reason.
- Change request/response DTO shapes without considering frontend impact.
- Create duplicate endpoints for the same behavior.
- Invent API contracts when existing code or documentation can be checked first.

If a new API is required:

1. Confirm that no equivalent endpoint already exists.
2. Design the smallest endpoint needed for the feature.
3. Keep naming consistent with the current API style.
4. Clearly report the new API contract.
5. Update the active API specification/documentation when appropriate.

## 5. Architecture

Use the existing MVC2 layered architecture.

Default backend flow:

`Controller → Service → ServiceImpl → Mapper → Mapper XML → MySQL`

Expected packages:

-   `controller`
-   `service`
-   `service.impl`
-   `mapper`
-   `dto`
-   `vo`
-   `config`
-   `common`

Follow actual repository packages when they are more specific.

### Mandatory boundaries

-   Controller handles HTTP concerns only.
-   Service owns business rules and transaction boundaries.
-   ServiceImpl orchestrates Mappers and external integrations.
-   Mapper interfaces only declare persistence operations.
-   SQL belongs in MyBatis Mapper XML.
-   DTOs represent API/request/response data.
-   VOs represent persistence/domain records where the project already
    uses that convention.

Never:

-   Access a Mapper directly from a Controller.
-   Put SQL in Controller or Service classes.
-   Use a VO directly as an API request or response.
-   Introduce a new architectural layer unless it solves a concrete
    existing need.

------------------------------------------------------------------------

## 6. Change Strategy

This is a hackathon project. Prefer **small, complete, demonstrable
changes**.

Always:

-   Inspect related files before editing.
-   Reuse existing patterns.
-   Modify the minimum number of files necessary.
-   Preserve backward compatibility where practical.
-   Complete all required layers for the requested feature.
-   Keep changes scoped to the task.
-   Point out required DB/config/API work that cannot safely be
    inferred.

Do not:

-   Rewrite unrelated files.
-   Perform broad refactors during feature work.
-   Rename packages/classes merely for style.
-   Add libraries for functionality already available in the project.
-   Create speculative abstractions for possible future requirements.
-   replace working project conventions with generic best practices
    without a concrete reason.

------------------------------------------------------------------------

## 7. Spring Configuration

All **new Spring bean configuration** must use Java Config.

Use:

-   `@Configuration`
-   `@Bean`
-   component scanning where already configured
-   `DelegatingFilterProxy` / existing servlet-container integration
    where applicable

Existing XML configuration may remain and may be edited only when the
existing application requires it.

Do not:

-   Create new Spring XML bean configuration for new features.
-   Convert working Java Config to XML.
-   Introduce Spring Boot auto-configuration.

MyBatis Mapper XML is still required for SQL and is not considered
Spring bean configuration.

------------------------------------------------------------------------

## 8. Dependency Injection

Prefer constructor injection.

If Lombok is already configured in the repository, prefer:

``` java
@RequiredArgsConstructor
```

with `final` dependencies.

If Lombok is not already configured, use an explicit constructor instead
of adding Lombok solely for dependency injection.

Never use field injection for new code.

------------------------------------------------------------------------

## 9. Controllers

Controller responsibilities:

-   Request mapping
-   Request/path/query parameter binding
-   Bean validation
-   Authentication context extraction
-   Service delegation
-   HTTP status and response generation
-   Swagger documentation when Swagger is already configured

Controllers must not contain business logic.

Prefer `ResponseEntity<T>` when the existing API style uses it.

### Swagger

When Swagger 2 / Springfox annotations are already available:

-   `@Api(tags = "...")` on controller classes.
-   `@ApiOperation` on endpoint methods.
-   `@ApiResponses` for meaningful alternative responses.
-   `@ApiParam` where parameter meaning/example improves documentation.
-   `@ApiIgnore` for framework-only parameters such as `Authentication`.

Keep Swagger text concise, user-facing, and in Korean.

Do not introduce a Swagger/OpenAPI dependency only to satisfy these
annotations.

------------------------------------------------------------------------

## 10. Services

Create a Service interface and ServiceImpl when that matches the
existing domain pattern.

Service responsibilities include:

-   Business validation
-   Transaction boundaries
-   Mapper orchestration
-   Authorization/domain security checks
-   Risk Score orchestration
-   Transfer workflow state
-   External API orchestration

Use `@Transactional` at the service layer when multiple persistence
operations must succeed or fail atomically.

Do not create transactions around read-only/external-only operations
without a reason.

------------------------------------------------------------------------

## 11. MyBatis

Mapper interface location:

`src/main/java/**/mapper`

Mapper XML location:

`src/main/resources/mapper`

Always:

-   Keep SQL in Mapper XML.
-   Use explicit column lists.
-   Define `parameterType` when useful/consistent with existing XML.
-   Define `resultType` or `resultMap` explicitly.
-   Prefer `resultMap` when column/property mapping is non-trivial.
-   Keep Mapper interfaces limited to method declarations.

Never:

-   Use `SELECT *`.
-   Use `SqlSession` directly in application code.
-   Use `@Select`, `@Insert`, `@Update`, or `@Delete`.
-   Duplicate SQL logic unnecessarily.

Avoid dynamic SQL unless the query actually requires optional
conditions.

------------------------------------------------------------------------

## 12. Database and SQL

Target MySQL 8.

Prefer:

-   Index-friendly predicates.
-   `JOIN` or `EXISTS` based on the query semantics.
-   Pagination for potentially large result sets.
-   Batch operations when processing multiple rows.
-   Explicit ordering when API behavior depends on order.

Avoid:

-   N+1 query patterns.
-   Repeated DB calls for data already loaded in the same service flow.
-   Functions on indexed columns in `WHERE` clauses when avoidable.
-   Schema changes unrelated to the requested feature.

When changing a query, briefly consider expected row count and index
usage.

Do not invent tables or columns. Verify them from schema, migration/DDL
files, Mapper XML, or existing VOs first.

------------------------------------------------------------------------

## 13. DTO and VO Rules

### DTO

Use DTOs for:

-   Request bodies
-   API responses
-   Service boundary data when appropriate

Do not expose persistence VOs directly through REST APIs.

### VO

Use VOs for persistence records according to the project's existing
MyBatis convention.

### Lombok

Use Lombok only if it is already a project dependency.

When available:

-   `@Getter`, `@Setter` for mutable DTO/VO classes.
-   `@NoArgsConstructor` / `@AllArgsConstructor` when required.
-   `@Builder` only when construction complexity justifies it.
-   `@RequiredArgsConstructor` for constructor injection.

Do not mix generated and handwritten boilerplate without a concrete
reason.

------------------------------------------------------------------------

## 14. Validation

Use `javax.validation` for Spring Framework 5.3 unless the repository
already uses another compatible convention.

Validate at the API boundary where possible.

Examples:

-   Required recipient
-   Positive transfer amount
-   Required account identifiers
-   Valid enum/status values

Business validation still belongs in the Service layer.

Never rely only on frontend validation for financial operations.

------------------------------------------------------------------------

## 15. Exceptions

Common exception infrastructure belongs under:

`common.exception`

Domain-specific exceptions should remain close to their domain when the
repository follows domain packages.

Prefer:

-   `BusinessException`
-   Domain `ErrorCode`
-   A single global exception handling mechanism
-   A consistent API error response

Use the project's existing global handler/resolver implementation. Do
not create a second competing exception framework.

Never throw a raw `RuntimeException` for expected business errors.

Important financial/domain failures should have distinguishable error
codes, especially:

-   Invalid transfer amount
-   Recipient not found/ambiguous
-   Insufficient transfer information
-   Transfer confirmation mismatch
-   Risk check failure
-   Suspicious transaction hold
-   Account/transaction not found
-   External API failure

------------------------------------------------------------------------

## 16. Enums and Constants

Use enums for stable status/type values when the repository already
models them as enums.

Default enum package:

`common.enums`

Domain-private enums may live with their domain if that improves
cohesion.

Use `UPPER_SNAKE_CASE` enum constants.

Do not:

-   Duplicate enum definitions.
-   Scatter status/type string literals through business code.
-   Move substantial service business logic into enums.

------------------------------------------------------------------------

## 17. Security

This project uses Spring Security 5.x without Spring Boot.

Security changes must remain compatible with the existing:

-   Servlet container
-   `DelegatingFilterProxy`
-   `web.xml` integration if present
-   Java Security configuration
-   JWT authentication flow
-   OAuth2 flow

### JWT

When working on JWT, preserve or implement the existing equivalents of:

-   Token provider
-   Authentication filter
-   Token validation
-   Authentication creation
-   `SecurityContext` integration
-   `AuthenticationEntryPoint`
-   `AccessDeniedHandler`

Never hardcode JWT secrets.

### OAuth2

OAuth2 providers may include:

-   Google
-   Kakao
-   Naver
-   GitHub

Do not assume every provider is currently enabled. Inspect configuration
first.

Do not generate Spring Boot OAuth2 auto-configuration.

------------------------------------------------------------------------

## 18. Voice Banking Domain Rules

Voice banking is a core domain, not a generic chatbot feature.

### Conversation flow

For transfer-related slot filling:

-   Collect missing information one item at a time.
-   Typical critical slots are recipient and amount.
-   Do not execute a transfer from raw STT output alone.
-   Preserve the user's confirmed values across dialogue steps.
-   Support re-question/reconfirmation when recognition is uncertain.

### STT / TTS

Backend responsibilities may include:

-   Processing recognized text.
-   Intent/entity extraction.
-   Dialogue state/slot processing.
-   TTS/STT external API orchestration when assigned to the backend.

Frontend responsibilities include the listening/processing/speaking UI
state and device-level audio interaction unless existing code defines
otherwise.

Do not invent an STT/TTS provider. Inspect project configuration and
existing code first.

### Ambiguous numbers and amounts

Treat similar-sounding or low-confidence amounts as safety-sensitive.

When ambiguity is detected:

1.  Do not proceed silently.
2.  Produce candidate/confirmation information.
3.  Require explicit reconfirmation before transfer execution.

Never "correct" a transfer amount based only on model inference.

------------------------------------------------------------------------

## 19. Recipient Discovery

Recipient candidates may use:

-   Capacitor-provided contacts
-   Existing transaction history
-   User-confirmed relationship/alias information when available

The mobile client owns native contact permission/access unless the
existing implementation says otherwise.

The backend should receive only the contact information required for
candidate matching.

Do not assume access to SMS/message contents or call contents unless the
project explicitly implements and authorizes them.

When multiple candidates match, return enough information for the user
to choose rather than selecting one silently.

------------------------------------------------------------------------

## 20. Transfer Safety and Read-Back

Before executing a transfer, the system must have confirmed critical
transaction information.

At minimum, treat these as safety-critical where available:

-   Sender account
-   Recipient
-   Recipient account/bank
-   Amount
-   Risk check result
-   Final user approval

The final transfer API must not infer approval from an earlier unrelated
dialogue step.

Read-back/confirmation should allow the frontend to present important
information in large text and TTS.

Do not log full sensitive account information or authentication tokens.

------------------------------------------------------------------------

## 21. Risk Score

Risk Score is used to add friction to suspicious transfers, not to
silently make irreversible decisions.

Potential signals defined by the project may include:

-   New recipient
-   Unusual transfer amount
-   Unusual transfer time
-   Repeated transfer attempts
-   Other explicitly implemented transaction-history signals

Rules:

-   Keep scoring logic in the Service/domain layer, not Controller.
-   Keep weights/thresholds centralized rather than scattering magic
    numbers.
-   Return factors/reasons needed by the confirmation UI when
    appropriate.
-   Higher risk may trigger additional questions, reconfirmation,
    authentication, or hold behavior according to the implemented
    policy.
-   Do not claim a fraud transaction is definitively fraudulent based
    only on the score.

If weights or thresholds are not defined in the repository/spec, do not
invent them as final policy. Mark them as requiring product/domain
confirmation.

------------------------------------------------------------------------

## 22. Financial Reminders

For utility bill / financial schedule functionality:

-   Keep reminder calculation/business rules in Service.
-   Keep persistence in Mapper XML.
-   Return explicit due date, payee, amount, and status fields when the
    API contract defines them.
-   Avoid duplicate reminders for the same underlying obligation when
    possible.

Do not implement automatic payment unless it is explicitly part of the
API contract.

------------------------------------------------------------------------

## 23. Mobile Branch / Visiting Branch Information

Branch-related APIs may expose:

-   Location
-   Schedule
-   Available services
-   Required documents
-   Distance or nearby alternatives when supported by available data

Do not fabricate branch schedules, supported tasks, or location data.

If information comes from an external source, isolate that integration
behind a service/client boundary rather than embedding HTTP calls in
Controller code.

------------------------------------------------------------------------

## 24. External APIs

For STT, TTS, banking, branch, or other external APIs:

-   Isolate integration logic from Controllers.
-   Use timeouts.
-   Handle non-2xx responses.
-   Convert provider-specific failures into project error responses.
-   Avoid leaking provider payloads directly to the frontend unless the
    API contract requires it.
-   Never hardcode credentials.
-   Log failures without exposing secrets or sensitive financial data.

Do not add retry behavior to financial execution calls unless
idempotency is guaranteed.

------------------------------------------------------------------------

## 25. Configuration and Secrets

Local application configuration:

`application-local.properties`

Secrets/environment-specific values:

`.env` and environment variables, following the project's existing
loading mechanism.

Never generate unless explicitly requested:

-   `application.yml`
-   root `application.properties`
-   `application-local.properties.example`
-   `.env.example`
-   additional profile property files

Never commit:

-   JWT secrets
-   OAuth client secrets
-   STT/TTS credentials
-   DB passwords
-   banking/external API secrets

Before adding a new configuration mechanism, inspect how the current
project loads `.env` and properties.

------------------------------------------------------------------------

## 26. Logging

Use SLF4J.

Never use:

``` java
System.out.println()
```

Log useful operational context, but avoid sensitive data.

Do not log:

-   JWT/access/refresh tokens
-   Passwords
-   OAuth secrets
-   Full account numbers
-   Full contact lists
-   Raw sensitive voice transcripts unless explicitly required and
    protected

Prefer identifiers and masked values where debugging context is
necessary.

------------------------------------------------------------------------

## 27. Formatting

Java source should follow the formatter already configured in the
repository.

If Google Java Format is configured, generate compliant code and use it.

Before completing a code change:

-   Remove unused imports.
-   Keep imports organized.
-   Reformat modified Java files using the project's configured
    formatter.

Do not introduce a new formatter or Git hook solely because this
document mentions formatting.

------------------------------------------------------------------------

## 28. Git and Collaboration Rules

Follow the team collaboration rules defined in the KB Hackathon Notion workspace.

### Git Flow

Branches:

- `main`: deployment-ready stable code
- `dev`: development integration branch
- `feat/*`: feature development
- `fix/*`: bug fixes
- `hotfix/*`: urgent production/stable fixes

New feature work must normally start from the latest `dev` branch and be merged back into `dev` through Pull Request.

After QA/stabilization, `dev` is merged into `main`.

For a hotfix, apply the fix to the stable branch flow and ensure the same fix is reflected back into the development branch so histories do not diverge.

Do not push directly to `main` or `dev`.

### Branch Naming

Rules:

- lowercase
- feature/task based
- use `/` or `-`

Examples:

```text
feat/voice-transfer
feat/risk-score
feat/recipient-candidate
feat/mobile-branch
fix/transfer-validation
hotfix/oauth
```

Prefer names that describe the actual project domain rather than generic names.

### Development Process

For normal development tasks, follow:

1. Create or identify the Issue.
2. Update local `dev`.
3. Create the task branch.
4. Implement the feature/fix.
5. Run local build/test or the strongest available verification.
6. Apply code formatting.
7. Commit.
8. Push the task branch.
9. Create a Pull Request targeting `dev`.
10. Obtain at least one team review/approval.
11. Resolve conflicts and reflect the latest `dev` before merge when necessary.
12. Ensure configured CI build/format checks pass.
13. Merge only after approval.
14. Delete the merged task branch.

When acting as a coding agent, do not claim these GitHub actions were performed unless you actually performed them.

### Commit Convention

Use these commit types:

```text
Feat: 새로운 기능 추가
Fix: 버그 수정
Docs: 문서 수정
Refactor: 코드 리팩토링
Style: 코드 스타일 변경
Test: 테스트 코드 추가 및 수정
Chore: 빌드, 설정, 라이브러리 변경
Remove: 사용하지 않는 파일 삭제
```

Examples for this project:

```text
Feat: 보이스 송금 슬롯 필링 구현
Feat: Risk Score 산출 로직 추가
Fix: 송금 금액 재확인 오류 수정
Docs: API 명세 갱신
Refactor: 수취인 후보 조회 서비스 분리
Chore: STT API 설정 추가
```

Keep each commit focused on one logical change.

### Pull Request Rules

A Pull Request must:

- Target `dev` for normal feature/fix work.
- Be reviewed and approved by at least one teammate before merge.
- Reflect the latest `dev` before merge when required.
- Pass configured CI build/format checks.
- Have conflicts resolved by the PR author.
- Include related issue information.
- Explain implemented/changed behavior.
- Explain how the change was tested.
- Include screenshots when UI changes are involved.
- Mention areas where reviewers should focus.

Before PR completion, verify:

- Local build and execution were checked when possible.
- Relevant tests/verification were completed.
- Formatting was applied.
- Unnecessary logs/comments were removed.
- API specification or related documentation was updated when the API contract changed.
- Latest `dev` changes were considered/reflected.

### Documentation Synchronization

When implementation changes an API contract, DB behavior, or project behavior that is documented:

- Update the relevant repository or Notion API documentation in the same task when available.
- Do not silently change an implemented API while leaving documentation stale.
- If the Notion API specification is the team's active reference but cannot be edited from the current development environment, explicitly report what needs to be updated.


## 29. Gradle

Use the dependency configurations appropriate for the existing Gradle
version, such as:

-   `implementation`
-   `compileOnly`
-   `runtimeOnly`
-   `annotationProcessor`
-   `testImplementation`

When dependency changes are needed:

-   Modify the existing `build.gradle`.
-   Add only the minimum required dependency.
-   Avoid Spring Boot starters.
-   Avoid version duplication when the project already centralizes
    versions.

Do not create an alternative build system.

------------------------------------------------------------------------

## 30. Testing and Verification

Do not automatically add tests unless requested or the repository task
clearly requires them.

When tests are requested, use the testing stack already present. Typical
options are:

-   JUnit 5
-   Mockito
-   Spring Test

For every implementation task, perform the strongest available
verification appropriate to the change, such as:

-   Gradle compile/test task
-   Mapper XML validation through application/test startup
-   Existing targeted tests
-   Static inspection of API mapping and DTO compatibility

Do not claim a build/test passed unless it was actually run
successfully.

If verification cannot be run, state that clearly.

------------------------------------------------------------------------

## 31. Completion Checklist

Before considering a backend feature complete, verify as applicable:

-   Endpoint matches the active API specification.
-   Controller contains no business logic.
-   Service/ServiceImpl owns business behavior.
-   Mapper interface and XML are both present for DB access.
-   DTOs and VOs are not mixed across API/persistence boundaries.
-   SQL uses explicit columns.
-   Validation is present.
-   Expected errors use the project exception mechanism.
-   Financial actions require appropriate confirmation.
-   Sensitive values are not logged.
-   Configuration contains no hardcoded secrets.
-   Existing architecture and unrelated behavior remain intact.
-   Modified code is formatted.
-   Available verification has been run.

------------------------------------------------------------------------

## 32. Response Style for Coding Tasks

Do not force a large boilerplate explanation for every task.

For implementation work, respond concisely with:

1.  What changed
2.  Important implementation decisions
3.  Files changed
4.  Configuration/DB/Gradle changes, only when applicable
5.  Verification performed
6.  Remaining blockers or contract decisions, only when applicable

When the user asks only for analysis, debugging, review, or a small
snippet, answer directly instead of forcing this structure.

------------------------------------------------------------------------

## 33. Forbidden Unless Explicitly Requested

Do not introduce:

-   `@SpringBootApplication`
-   Spring Boot starters
-   Spring Boot auto-configuration
-   Embedded Tomcat
-   `application.yml`
-   New root `application.properties`
-   JPA / Hibernate
-   WebFlux / reactive programming
-   SQL annotations on MyBatis Mapper interfaces
-   Field injection
-   Hardcoded secrets
-   New endpoint contracts that bypass the active API specification

Always treat this repository as a **Spring Framework 5.3 / Spring MVC /
MyBatis / WAR / Tomcat 9** application unless the repository itself
proves otherwise.
