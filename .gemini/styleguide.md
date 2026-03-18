# Spring Batch Worker Code Review Style Guide

이 프로젝트는 **Spring Boot 3.4 / Spring Batch 5 / Java 17** 배치 워커이며, **Chunk 기반 ETL 아키텍처**를 채택한다.
아래 규칙을 기준으로 코드 리뷰를 수행한다.

---

## Review Comment Style & Language (전역 규칙)

- **"왜 문제인지"** + **"어떻게 고치면 좋은지"** 를 함께 제시한다.
- severity가 낮은 사항(nit)은 접두어 `[nit]`을 붙여 구분한다.
- 모든 코드 리뷰 코멘트는 **한국어로 작성한다.**
- 설명 또는 기술 용어가 영어가 자연스러울 경우 한국어 + 영어 병기로 작성할 수 있다.

---

## 1) 아키텍처 원칙

### 의존성 방향

```
Controller → Service → BatchJobLauncher → Job → Step → Reader / Processor / Writer
```

- **단방향 의존만 허용한다.** 하위 계층이 상위 계층을 참조하면 안 된다.
- `Reader/Processor/Writer`는 `Job`이나 `Controller`를 알면 안 된다.
- `BatchJobLauncher`는 `Controller`에 의존하면 안 된다.

### 패키지 구조

```
com.template.worker
├─ api/                          # 배치 실행 트리거 API
│   ├─ controller/               # REST 엔드포인트 (배치 실행 전용)
│   ├─ service/                  # Launcher에 위임만
│   └─ dto/                      # 배치 실행 요청 DTO
├─ common/                       # 배치 인프라 (횡단 관심사)
│   ├─ config/                   # @EnableBatchProcessing 등 배치 설정
│   ├─ launcher/                 # BatchJobLauncher (중앙 Job 실행 엔진)
│   └─ listener/                 # JobResultListener, JobLogger (공통 리스너)
└─ jobs/                         # 배치 Job 정의 (feature별 하위 패키지)
    └─ {feature}/
        ├─ job/                  # @Configuration — Job Bean 정의
        ├─ step/                 # @Configuration — Step Bean 정의
        ├─ reader/               # @Component — ItemReader 구현
        ├─ processor/            # @Component — ItemProcessor 구현
        └─ writer/               # @Component — ItemWriter 구현
```

- `jobs/` 아래는 **feature(업무) 기준**으로 패키지를 나눈다.
- `common/`은 횡단 관심사만 둔다. 특정 Job의 비즈니스 로직을 `common/`에 두지 않는다.

### 도입하지 않는 것

- JPA Entity / Repository (배치는 ETL 패턴)
- Interface + Impl 서비스 구조
- `ApiResponse<T>` 응답 래퍼
- `ApplicationException` / `ErrorCode` 예외 체계
- CQRS, 도메인 이벤트 복잡 설계

---

## 2) Job 규칙

- Job은 `@Configuration` 클래스에서 `@Bean`으로 정의한다.
- Job 이름은 **kebab-case**를 사용한다 (예: `example-job`, `user-sync-job`).
- **모든 Job에 `JobResultListener`를 등록**해야 한다 — 실행 결과 로깅/모니터링의 통일성을 위해 필수.
- Job → Step 연결은 `JobBuilder.start()` / `.next()`로 구성한다.
- 하나의 Job Config 파일에 **하나의 Job만** 정의한다.

```java
@Configuration
@RequiredArgsConstructor
public class ExampleJobConfig {
  private final JobRepository jobRepository;
  private final JobResultListener jobResultListener;
  private final ExampleStepConfig stepConfig;

  @Bean
  public Job exampleJob() {
    return new JobBuilder("example-job", jobRepository)
        .listener(jobResultListener)
        .start(stepConfig.exampleStep())
        .build();
  }
}
```

---

## 3) Step 규칙

- Step은 `@Configuration` 클래스에서 `@Bean`으로 정의한다.
- **Chunk 기반** 구성을 기본으로 한다 (`Reader → Processor → Writer`).
- chunk size는 명시적으로 지정한다 (예: `.chunk(100, transactionManager)`).
- Step 이름은 **kebab-case**를 사용한다 (예: `example-step`).
- 하나의 Step Config 파일에 **하나의 Step만** 정의한다.

```java
@Configuration
@RequiredArgsConstructor
public class ExampleStepConfig {
  private final JobRepository jobRepository;
  private final PlatformTransactionManager transactionManager;
  private final ExampleItemReader reader;
  private final ExampleItemProcessor processor;
  private final ExampleItemWriter writer;

  @Bean
  public Step exampleStep() {
    return new StepBuilder("example-step", jobRepository)
        .<String, String>chunk(100, transactionManager)
        .reader(reader)
        .processor(processor)
        .writer(writer)
        .build();
  }
}
```

---

## 4) Reader / Processor / Writer 규칙

### 공통

- 각각 `ItemReader<T>`, `ItemProcessor<I, O>`, `ItemWriter<T>`를 구현한다.
- `@Component`로 등록한다.
- **단일 책임 원칙**: Reader는 읽기만, Processor는 가공만, Writer는 저장만 수행한다.
- 비즈니스 로직이 섞이지 않도록 역할을 명확히 분리한다.

### Reader

- DB, S3, API 등에서 데이터를 읽어오는 역할만 한다.
- 데이터가 없으면 `null`을 반환하여 Step 종료를 알린다.

```java
@Component
public class ExampleItemReader implements ItemReader<String> {
  @Override
  public String read() throws Exception {
    return null; // null 반환 시 Step 종료
  }
}
```

### Processor

- 읽어온 데이터를 가공/변환하는 역할만 한다.
- 필터링이 필요하면 `null`을 반환하여 해당 아이템을 건너뛴다.

```java
@Component
public class ExampleItemProcessor implements ItemProcessor<String, String> {
  @Override
  public String process(String item) throws Exception {
    return item; // null 반환 시 해당 아이템 스킵
  }
}
```

### Writer

- 처리된 결과를 DB, S3, 파일 등으로 저장한다.
- `Chunk<? extends T>`를 파라미터로 받아 벌크 처리한다.

```java
@Component
public class ExampleItemWriter implements ItemWriter<String> {
  @Override
  public void write(Chunk<? extends String> chunk) throws Exception {
    // 벌크 저장 로직
  }
}
```

---

## 5) API 계층 규칙 (배치 트리거 전용)

- Controller는 **배치 실행 트리거만** 담당한다. 비즈니스 로직을 두지 않는다.
- Controller → Service → `BatchJobLauncher` 순서로 위임한다.
- 반환 타입은 `void`로 충분하다 (배치 실행은 비동기 특성).

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/batch")
public class BatchAdminController {
  private final BatchAdminService service;

  @PostMapping("/run")
  public void run(@RequestBody RunBatchRequest request) throws Exception {
    service.run(request);
  }
}
```

### Service

- Service는 **단순 클래스**로 작성한다 (Interface + Impl 구조 사용하지 않음).
- Service의 역할은 `BatchJobLauncher`에 위임하는 것뿐이다.

```java
@Service
@RequiredArgsConstructor
public class BatchAdminService {
  private final BatchJobLauncher launcher;

  public void run(RunBatchRequest request) throws Exception {
    launcher.run(request.getJobName(), request.getParams());
  }
}
```

---

## 6) Common 계층 규칙 (배치 인프라)

### config/

- `@EnableBatchProcessing`을 포함하는 배치 핵심 설정.
- DataSource, TransactionManager 등 배치 인프라 설정.

### launcher/

- `BatchJobLauncher`: `JobRegistry`에서 Job을 조회하고 `JobLauncher`로 실행하는 **중앙 실행 엔진**.
- 모든 배치 실행은 이 클래스를 통해서만 이루어져야 한다.

```java
@Component
@RequiredArgsConstructor
public class BatchJobLauncher {
  private final JobLauncher jobLauncher;
  private final JobRegistry jobRegistry;

  public void run(String jobName, Map<String, String> params) throws Exception {
    Job job = jobRegistry.getJob(jobName);
    JobParametersBuilder builder = new JobParametersBuilder();
    params.forEach(builder::addString);
    jobLauncher.run(job, builder.toJobParameters());
  }
}
```

### listener/

- `JobResultListener`: 모든 Job에 등록되는 **공통 리스너**. `@BeforeJob` / `@AfterJob`으로 실행 결과를 수집한다.
- `JobLogger`: 성공/실패를 구조화된 로그로 출력한다.
- 새로운 공통 리스너(알림, 메트릭 등)는 이 패키지에 추가한다.

---

## 7) DTO 규칙

- 배치 실행 요청 DTO는 `@Getter` 클래스로 작성한다 (record 사용하지 않음).
- DTO에 비즈니스 로직을 두지 않는다.
- `from()`, `toEntity()` 같은 변환 메서드를 두지 않는다.

```java
@Getter
public class RunBatchRequest {
  private String jobName;
  private Map<String, String> params;
}
```

---

## 8) 코드 스타일

- **포매팅**: Google Java Format (AOSP, 4-space 인덴트) — `./gradlew spotlessApply`로 자동 적용.
- **Import 순서**: `java` → `javax` → `jakarta` → `org` → `net` → `com` → 기타 → `lombok` (Spotless가 강제).
- **Checkstyle**: Naver Java 컨벤션 적용. `./gradlew checkstyleMain`으로 검증.
- 메서드/변수 이름은 역할이 드러나게 작성하고, 약어를 남발하지 않는다.
- 매직넘버/문자열은 반드시 상수로 추출한다.
- Job/Step 이름 문자열은 **kebab-case**로 작성한다 (예: `"example-job"`, `"user-sync-step"`).

---

## 9) 테스트

- 신규 배치 로직은 최소 단위 테스트 1개 이상 작성한다.
- `spring-batch-test`의 `JobLauncherTestUtils`를 활용하여 Job/Step 단위 테스트를 작성한다.
- Reader/Processor/Writer는 개별 단위 테스트로 검증한다.
- Processor의 경계값과 `null` 반환(스킵) 케이스를 각각 1개 이상 포함한다.

---

## 10) 안티패턴 체크리스트

리뷰 시 아래 패턴이 발견되면 반드시 지적한다:

| 안티패턴 | 설명 |
|----------|------|
| **JobResultListener 미등록** | 모든 Job에 `JobResultListener`를 등록해야 한다 |
| **Reader에서 가공 로직** | Reader는 읽기만 담당, 가공은 Processor에서 수행 |
| **Writer에서 조회 로직** | Writer는 저장만 담당, 조회는 Reader에서 수행 |
| **Controller에 배치 로직** | Controller는 트리거만 담당, 실행은 Launcher에 위임 |
| **Job 직접 실행** | `JobLauncher`를 직접 호출하지 않고 `BatchJobLauncher`를 통해 실행 |
| **하나의 Config에 여러 Job/Step** | Job Config, Step Config 파일은 각각 하나의 Bean만 정의 |
| **common에 Job 비즈니스 로직** | common은 횡단 관심사(config, launcher, listener)만 허용 |
| **Service에 Interface + Impl** | 배치 서비스는 단순 클래스로 작성 (인터페이스 분리 불필요) |
| **chunk size 미지정** | StepBuilder에서 chunk size를 명시적으로 지정해야 한다 |
| **Job/Step 이름 불일치** | Job/Step 이름은 kebab-case를 사용하고 클래스명과 의미가 일치해야 한다 |
