// BAD — every line here is a Spring Batch 4.x habit that breaks on Boot 3 / Batch 5,
// or a silent data-correctness bug.

@Configuration
@EnableBatchProcessing  // BAD: on Boot 3 this DISABLES auto-config — JobRepository/JobLauncher vanish
@RequiredArgsConstructor
public class OrderExportJobConfig {

    private final JobBuilderFactory jobBuilderFactory;   // BAD: removed in Batch 5 — won't compile
    private final StepBuilderFactory stepBuilderFactory; // BAD: removed in Batch 5 — won't compile

    @Bean
    public Job orderExportJob(Step exportStep) {
        return jobBuilderFactory.get("orderExportJob")   // BAD: factory API gone
            .start(exportStep)
            .build();
        // BAD: no incrementer → re-running with same params throws JobInstanceAlreadyCompleteException
    }

    @Bean
    public Step exportStep(ItemReader<Order> reader,
                           ItemWriter<OrderRow> writer) {
        return stepBuilderFactory.get("exportStep")
            .<Order, OrderRow>chunk(500)                 // BAD: Batch 5 needs chunk(500, txManager)
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
        return items -> {                                // BAD: Batch 5 passes Chunk<>, not List<>
            repo.saveAll(items);
            email.send("export-done@co", "batch finished"); // BAD: inside chunk TX — fires even if chunk rolls back
        };
    }
}
