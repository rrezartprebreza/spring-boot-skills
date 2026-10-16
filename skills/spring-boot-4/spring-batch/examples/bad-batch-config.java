// BAD — every line here is a Spring Batch 4/5 habit that breaks on Boot 4 / Batch 6,
// or a silent data-correctness bug.

@Configuration
@EnableBatchProcessing  // BAD: this DISABLES Boot auto-config — JobRepository/JobOperator vanish
@RequiredArgsConstructor
public class OrderExportJobConfig {

    private final JobBuilderFactory jobBuilderFactory;   // BAD: removed in Batch 5 — won't compile
    private final StepBuilderFactory stepBuilderFactory; // BAD: removed in Batch 5 — won't compile
    private final JobLauncher jobLauncher;               // BAD: consolidated into JobOperator in Batch 6

    @Bean
    public Job orderExportJob(Step exportStep) {
        return jobBuilderFactory.get("orderExportJob")   // BAD: factory API gone
            .start(exportStep)
            .build();
        // BAD: no incrementer → re-running with same params throws JobInstanceAlreadyCompleteException
    }

    @Bean
    public Step exportStep(PlatformTransactionManager txManager,
                           ItemReader<Order> reader,
                           ItemWriter<OrderRow> writer) {
        return stepBuilderFactory.get("exportStep")
            .<Order, OrderRow>chunk(500, txManager)      // BAD: Batch 5 style — Batch 6: .chunk(500) + .transactionManager(txManager)
            .reader(reader)
            .writer(writer)
            .build();
    }

    @Bean
    public JdbcCursorItemReader<Order> reader(DataSource ds) { // BAD: cursor reader is NOT thread-safe
        JdbcCursorItemReader<Order> r = new JdbcCursorItemReader<>();
        r.setDataSource(ds);
        r.setSql("SELECT id, total FROM orders WHERE status = 'PENDING'"); // BAD: no ORDER BY — pages drift
        r.setRowMapper(new OrderRowMapper());
        return r;
        // BAD: not @StepScope, so it can't read jobParameters
    }

    @Bean
    public ItemWriter<OrderRow> writer(EmailService email, OrderExportRepository repo) {
        return items -> {                                // BAD: writer receives Chunk<>, not List<> (since Batch 5)
            repo.saveAll(items);
            email.send("export-done@co", "batch finished"); // BAD: inside chunk TX — fires even if chunk rolls back
        };
    }

    // BAD: also: project only has spring-boot-starter-batch → resourceless in-memory repository,
    //    so the restart-on-failure this job relies on silently doesn't exist.
    //    Use spring-boot-starter-batch-jdbc for persistent BATCH_* metadata.
}
