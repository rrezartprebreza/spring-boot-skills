---
name: spring-batch
description: >
  Use when building batch jobs, ETL pipelines, scheduled imports/exports, or any chunk-oriented
  bulk processing with Spring Batch. Covers the Spring Batch 6 / Boot 4 builder API, resourceless
  vs JDBC job repositories, restartability and idempotent job parameters, reader/writer
  thread-safety, fault tolerance, and chunk transaction boundaries.
---

# Spring Batch

Spring Boot 4.x ships **Spring Batch 6**. The API changed significantly from 5.x (and drastically
from 4.x) — most online examples are wrong. The rules that break the most agent-generated code:

1. **Do NOT add `@EnableBatchProcessing`.** Boot auto-configures the `JobRepository`,
   `JobOperator`, and transaction manager. Adding `@EnableBatchProcessing` **disables** that
   auto-configuration and you lose all the wired beans.
2. **Metadata is in-memory by default.** Batch 6's `JobRepository` is *resourceless* — nothing is
   persisted. Restartability and the `BATCH_*` audit tables require the
   `spring-boot-starter-batch-jdbc` starter (plain `spring-boot-starter-batch` = no restart after
   a crash).
3. **`JobLauncher` and `JobExplorer` are consolidated into `JobOperator`** (which extends both).
   Inject `JobOperator` and call `start(job, params)`.
4. **`JobBuilderFactory`/`StepBuilderFactory` are long gone**, and `chunk(500, txManager)` is the
   old Batch 5 style — Batch 6 takes the size alone, with an optional `.transactionManager(...)`.

## Dependencies

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-batch-jdbc</artifactId> <!-- persistent BATCH_* metadata -->
</dependency>
<!-- spring-boot-starter-batch alone = resourceless in-memory repository:
     fine for run-and-forget jobs, but no restart-on-failure, no audit trail -->
```

## Job & Step (Spring Batch 6 API)

```java
@Configuration
@RequiredArgsConstructor
public class OrderExportJobConfig {

    @Bean
    public Job orderExportJob(JobRepository jobRepository, Step exportStep) {
        return new JobBuilder("orderExportJob", jobRepository)
            .incrementer(new RunIdIncrementer()) // lets the same job be re-run; see "Idempotency"
            .start(exportStep)
            .build();
    }

    @Bean
    public Step exportStep(JobRepository jobRepository,
                           PlatformTransactionManager txManager, // Boot's, injected — do NOT new one up
                           ItemReader<Order> reader,
                           ItemProcessor<Order, OrderRow> processor,
                           ItemWriter<OrderRow> writer) {
        return new StepBuilder("exportStep", jobRepository)
            .<Order, OrderRow>chunk(500)        // chunk size is the commit interval — and a TX boundary
            .transactionManager(txManager)      // optional in Batch 6 — but set it for JDBC-backed steps
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .faultTolerant()
            .skip(FlatFileParseException.class)
            .skipLimit(50)
            .build();
    }
}
```

`chunk(500)` means: read 500 items, process each, hand the list of 500 to the writer,
**commit one transaction**, repeat. The chunk is the unit of restart and the unit of rollback.
The Batch 5 form `chunk(500, txManager)` is deprecated — size and transaction manager are now
separate builder calls.

## Idempotency & Restartability — the #1 operational gotcha

A `JobInstance` is identified by its **identifying** `JobParameters`. Launch the same job with the
same identifying parameters twice and you get:

```
JobInstanceAlreadyCompleteException: A job instance already exists and is complete
```

This is by design — Batch refuses to re-run completed work. Two ways to handle it:

```java
// Option A — RunIdIncrementer on the job (above) + JobLauncherApplicationRunner bumps run.id each launch.
// Option B — add a unique identifying parameter yourself when launching:
JobParameters params = new JobParametersBuilder()
    .addString("status", "COMPLETED")              // identifying — part of the instance key
    .addLong("run.id", System.currentTimeMillis()) // identifying & unique — makes each run a new instance
    .toJobParameters();
```

Mark a parameter **non-identifying** with the `false` flag when it's metadata that shouldn't change
the instance identity (e.g. a request id you log but don't key on):

```java
.addString("requestId", requestId, false) // non-identifying — excluded from the instance key
```

(In Batch 6 `JobParameter` is an immutable record that carries its own name — `JobParameters` holds
a `Set<JobParameter>` — but the builder above is unchanged.)

A **failed** job, by contrast, is *resumed* when relaunched with the **same** parameters — it skips
completed steps and restarts the failed step from the last committed chunk. That is the point of the
metadata tables — and it **only works with the JDBC job repository**; the default resourceless
repository forgets everything when the JVM exits. Don't defeat it by always passing a unique
parameter if you want resume-on-failure.

## ItemReader — sort key and thread-safety

```java
@Bean
@StepScope // required: late-binds jobParameters at step execution, not context startup
public JpaPagingItemReader<Order> orderReader(
        EntityManagerFactory emf,
        @Value("#{jobParameters['status']}") String status) {
    return new JpaPagingItemReaderBuilder<Order>()
        .name("orderReader")
        .entityManagerFactory(emf)
        .queryString("SELECT o FROM Order o WHERE o.status = :status ORDER BY o.id") // ORDER BY is MANDATORY
        .parameterValues(Map.of("status", OrderStatus.valueOf(status)))
        .pageSize(500) // keep pageSize == chunk size
        .build();
}
```

- **Paging readers require a deterministic `ORDER BY`** on a unique column. Without it the DB returns
  rows in arbitrary order across pages → rows get **skipped or processed twice**. This is silent
  data corruption, not an error.
- **`JdbcCursorItemReader` is NOT thread-safe.** `JdbcPagingItemReader` / `JpaPagingItemReader` are
  safe for multi-threaded steps. For a non-thread-safe reader in a multi-threaded step, wrap it in
  `SynchronizedItemStreamReader`.
- **Don't mutate the column you page on inside the same job.** If the writer flips `status` from
  `PENDING` to `DONE` while the reader pages `WHERE status = 'PENDING' ORDER BY id`, the result set
  shifts under you and pages are missed. Read into a stable snapshot, page by immutable `id`, or use
  a cursor reader.

## ItemProcessor — returning null filters

```java
@Component
public class OrderProcessor implements ItemProcessor<Order, OrderRow> {
    @Override
    public OrderRow process(Order order) {
        if (order.getTotal().isZero()) {
            return null; // ⚠️ null = FILTER this item; it is NOT written and NOT an error
        }
        return OrderRow.from(order);
    }
}
```

Returning `null` silently drops the item from the chunk. That's a feature (filtering) but a footgun
if you returned `null` by accident expecting it to pass through.

## ItemWriter — Chunk, not List

Since Batch 5 the writer receives a `Chunk<? extends T>`, **not** `List<? extends T>`:

```java
@Override
public void write(Chunk<? extends OrderRow> chunk) { // was List<? extends T> in 4.x
    repository.saveAll(chunk.getItems());
}
```

For SQL writes, prefer the batched JDBC writer over per-row saves — it uses one `addBatch()`:

```java
@Bean
public JdbcBatchItemWriter<OrderRow> orderWriter(DataSource dataSource) {
    return new JdbcBatchItemWriterBuilder<OrderRow>()
        .dataSource(dataSource)
        .sql("INSERT INTO order_export (id, total) VALUES (:id, :total)")
        .beanMapped()
        .build();
}
```

The writer runs **inside the chunk transaction**. Never fire emails, publish to Kafka, or call
webhooks from a writer — if the chunk rolls back you've already sent it. Bind side effects to the
job completion instead (see [[transactional-patterns]] and the listener below).

## Launching jobs

Boot runs every `Job` bean on startup by default. For scheduled or on-demand jobs, **turn that off**
and launch explicitly:

```yaml
spring:
  batch:
    job:
      enabled: false           # don't run jobs on app startup; we trigger them ourselves
    jdbc:
      initialize-schema: never # (batch-jdbc starter) manage BATCH_* tables with Flyway in prod
```

```java
@Component
@RequiredArgsConstructor
public class OrderExportScheduler {

    private final JobOperator jobOperator; // Batch 6: replaces JobLauncher AND JobExplorer
    private final Job orderExportJob;

    @Scheduled(cron = "0 0 2 * * *")
    public void runNightly() throws JobExecutionException {
        JobParameters params = new JobParametersBuilder()
            .addString("status", "COMPLETED")
            .addLong("run.id", System.currentTimeMillis())
            .toJobParameters();
        jobOperator.start(orderExportJob, params);
    }
}
```

Use `start(Job, JobParameters)` — the old `start(String jobName, Properties)` overload is
deprecated for removal. The default `JobOperator` is **synchronous** — `start(...)` blocks the
`@Scheduled` thread until the whole job finishes. For fire-and-forget, configure it with an async
`TaskExecutor` (or annotate a `@Bean` method with `@BatchTaskExecutor`), or trigger from a request
thread only if you accept the block.

## Metadata schema in production

With the JDBC repository, Spring Batch needs its `BATCH_JOB_INSTANCE`, `BATCH_JOB_EXECUTION`,
`BATCH_STEP_EXECUTION`, … tables. `initialize-schema: always` is fine for dev/embedded DBs but
**don't let Batch DDL your production database on startup**. Set `initialize-schema: never` and
ship the schema as a versioned [[flyway-migrations]] migration (the canonical DDL lives in
`org/springframework/batch/core/schema-*.sql` inside `spring-batch-core`). Upgrading an existing
Boot 3 database? Batch 6 renamed the `BATCH_JOB_SEQ` sequence to `BATCH_JOB_INSTANCE_SEQ` — the
project ships migration scripts; add one to your Flyway history.

## Listeners — work that must run after the job, not per chunk

```java
@Bean
public Job orderExportJob(JobRepository jobRepository, Step exportStep) {
    return new JobBuilder("orderExportJob", jobRepository)
        .incrementer(new RunIdIncrementer())
        .listener(new JobExecutionListener() {
            @Override public void afterJob(JobExecution exec) {
                if (exec.getStatus() == BatchStatus.COMPLETED) {
                    notifier.notifyExportReady(exec.getJobParameters()); // safe: all chunks committed
                }
            }
        })
        .start(exportStep)
        .build();
}
```

## Don't reach for Batch when you don't need it

Spring Batch earns its complexity (metadata tables, restart, chunking) on **large, restartable,
auditable bulk jobs**. For a quick one-off async task, `@Async` or a `@Scheduled` loop is lighter.
For durable background jobs with retry, a job queue is a better fit. Match the tool to the scale.

## Gotchas
- Agent adds `@EnableBatchProcessing` — on Boot it **disables** auto-config; remove it, just inject `JobRepository`
- Agent uses plain `spring-boot-starter-batch` and expects restart/audit — Batch 6's default repository is resourceless (in-memory); use `spring-boot-starter-batch-jdbc` for the `BATCH_*` tables
- Agent uses `JobBuilderFactory` / `StepBuilderFactory` — removed in Batch 5; use `new JobBuilder(name, repo)` / `new StepBuilder(name, repo)`
- Agent calls `.chunk(500, txManager)` — Batch 5 style, deprecated in 6; use `.chunk(500)` + `.transactionManager(txManager)`
- Agent injects `JobLauncher` or `JobExplorer` — consolidated into `JobOperator` in Batch 6; inject `JobOperator` and call `start(job, params)`
- Agent calls `jobOperator.start("jobName", properties)` — deprecated for removal; use `start(Job, JobParameters)`
- Agent writes manual config `@EnableBatchProcessing(dataSourceRef = ...)` — split in Batch 6: `@EnableBatchProcessing(taskExecutorRef = ...)` + `@EnableJdbcJobRepository(dataSourceRef = ...)`
- Agent reuses a Boot 3 Flyway baseline for `BATCH_*` — Batch 6 renamed `BATCH_JOB_SEQ` to `BATCH_JOB_INSTANCE_SEQ`; add the migration script
- Agent writes `write(List<? extends T> items)` — the signature is `write(Chunk<? extends T> chunk)` since Batch 5
- Agent expects batch metrics to just appear — Batch 6 dropped Micrometer's global static registry; declare an `ObservationRegistry` bean wired to your `MeterRegistry`
- Agent re-runs a job with identical parameters and hits `JobInstanceAlreadyCompleteException` — add `RunIdIncrementer` or a unique identifying param
- Agent adds a unique param every run on a job that should resume-on-failure — kills restartability; only add it when you want a fresh instance
- Agent writes a paging reader query with no `ORDER BY` (or a non-unique one) — pages skip/duplicate rows silently; order by a unique column
- Agent uses `JdbcCursorItemReader` in a multi-threaded step — not thread-safe; use a paging reader or `SynchronizedItemStreamReader`
- Agent pages on a column the writer mutates in the same job — result set shifts; page on an immutable id
- Agent returns `null` from a processor expecting pass-through — `null` filters (drops) the item
- Agent sends email / publishes events from the `ItemWriter` — runs inside the chunk TX; do it in an `afterJob` listener
- Agent forgets `@StepScope` on a reader that reads `jobParameters` — `@Value("#{jobParameters[...]}")` only binds in step scope
- Agent leaves jobs running on startup in a web app — set `spring.batch.job.enabled=false` and launch explicitly
- Agent lets `initialize-schema: always` DDL the prod DB — use `never` + a Flyway migration for the `BATCH_*` tables
