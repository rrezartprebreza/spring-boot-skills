// ✅ GOOD — Spring Batch 5 / Boot 3: no @EnableBatchProcessing, builders take JobRepository,
// chunk takes a transaction manager, reader is @StepScope with a deterministic sort, job is restartable.

@Configuration
@RequiredArgsConstructor
public class OrderExportJobConfig {

    @Bean
    public Job orderExportJob(JobRepository jobRepository, Step exportStep) {
        return new JobBuilder("orderExportJob", jobRepository)
            .incrementer(new RunIdIncrementer())          // re-runnable; resumes a failed run on same params
            .listener(new ExportCompletionListener())     // side effects AFTER the job, never in the writer
            .start(exportStep)
            .build();
    }

    @Bean
    public Step exportStep(JobRepository jobRepository,
                           PlatformTransactionManager txManager,   // injected — Boot already created it
                           ItemReader<Order> orderReader,
                           ItemProcessor<Order, OrderRow> orderProcessor,
                           ItemWriter<OrderRow> orderWriter) {
        return new StepBuilder("exportStep", jobRepository)
            .<Order, OrderRow>chunk(500, txManager)        // commit interval == transaction boundary
            .reader(orderReader)
            .processor(orderProcessor)
            .writer(orderWriter)
            .faultTolerant()
            .skip(FlatFileParseException.class)
            .skipLimit(50)                                 // tolerate up to 50 bad rows, then fail the step
            .build();
    }

    @Bean
    @StepScope                                             // late-binds jobParameters at execution time
    public JpaPagingItemReader<Order> orderReader(
            EntityManagerFactory emf,
            @Value("#{jobParameters['status']}") String status) {
        return new JpaPagingItemReaderBuilder<Order>()
            .name("orderReader")
            .entityManagerFactory(emf)
            .queryString("SELECT o FROM Order o WHERE o.status = :status ORDER BY o.id") // unique sort key
            .parameterValues(Map.of("status", OrderStatus.valueOf(status)))
            .pageSize(500)                                 // matches chunk size
            .build();
    }

    @Bean
    public ItemProcessor<Order, OrderRow> orderProcessor() {
        return order -> order.getTotal().isZero() ? null   // null = filter; documented intent
                                                  : OrderRow.from(order);
    }

    @Bean
    public JdbcBatchItemWriter<OrderRow> orderWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<OrderRow>()
            .dataSource(dataSource)
            .sql("INSERT INTO order_export (id, total) VALUES (:id, :total)")
            .beanMapped()
            .build();
    }
}
