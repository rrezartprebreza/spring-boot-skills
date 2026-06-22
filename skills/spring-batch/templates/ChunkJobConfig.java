// Copy-paste starting point for a chunk-oriented Spring Batch 5 job (Spring Boot 3.x).
// Replace Order / OrderRow / the reader query and writer SQL with your domain.
// Do NOT add @EnableBatchProcessing — Boot auto-configures JobRepository + transaction manager.

@Configuration
@RequiredArgsConstructor
public class ChunkJobConfig {

    private static final int CHUNK_SIZE = 500;

    @Bean
    public Job exampleJob(JobRepository jobRepository, Step exampleStep) {
        return new JobBuilder("exampleJob", jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(exampleStep)
            .build();
    }

    @Bean
    public Step exampleStep(JobRepository jobRepository,
                            PlatformTransactionManager txManager,
                            ItemReader<Order> reader,
                            ItemProcessor<Order, OrderRow> processor,
                            ItemWriter<OrderRow> writer) {
        return new StepBuilder("exampleStep", jobRepository)
            .<Order, OrderRow>chunk(CHUNK_SIZE, txManager)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .faultTolerant()
            .retry(TransientDataAccessException.class).retryLimit(3) // retry transient failures
            .skip(FlatFileParseException.class).skipLimit(50)        // tolerate bad input rows
            .build();
    }

    @Bean
    @StepScope
    public JpaPagingItemReader<Order> reader(
            EntityManagerFactory emf,
            @Value("#{jobParameters['status']}") String status) {
        return new JpaPagingItemReaderBuilder<Order>()
            .name("exampleReader")
            .entityManagerFactory(emf)
            .queryString("SELECT o FROM Order o WHERE o.status = :status ORDER BY o.id") // unique sort key
            .parameterValues(Map.of("status", OrderStatus.valueOf(status)))
            .pageSize(CHUNK_SIZE)
            .build();
    }

    @Bean
    public ItemProcessor<Order, OrderRow> processor() {
        return OrderRow::from; // return null from a processor to FILTER an item out of the chunk
    }

    @Bean
    public JdbcBatchItemWriter<OrderRow> writer(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<OrderRow>()
            .dataSource(dataSource)
            .sql("INSERT INTO order_export (id, total) VALUES (:id, :total)")
            .beanMapped()
            .build();
    }
}
