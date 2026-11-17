import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.repository.support.JdbcJobRepositoryFactoryBean;
import org.springframework.batch.core.launch.support.TaskExecutorJobOperator;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import static org.junit.jupiter.api.Assertions.*;

class BatchRestartTest {
    /** A failure after writing must roll back, while restart retains a previously committed step. */
    @Test void restartRetainsCompletedStepAndRollsBackFailedStep() throws Exception {
        var database = new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .setName("batch_" + UUID.randomUUID())
            .addScript(new ClassPathResource("org/springframework/batch/core/schema-h2.sql").getPath())
            .build();
        try {
            var jdbc = new JdbcTemplate(database);
            jdbc.execute("CREATE TABLE effects (id INT PRIMARY KEY)");
            var transactionManager = new DataSourceTransactionManager(database);
            var factory = new JdbcJobRepositoryFactoryBean();
            factory.setDataSource(database);
            factory.setTransactionManager(transactionManager);
            factory.afterPropertiesSet();
            var repository = factory.getObject();
            assertNotNull(repository);
            var failOnce = new AtomicBoolean(true);
            var first = new StepBuilder("first", repository).tasklet((contribution, context) -> {
                jdbc.update("INSERT INTO effects VALUES (1)");
                return RepeatStatus.FINISHED;
            }, transactionManager).build();
            var second = new StepBuilder("second", repository).tasklet((contribution, context) -> {
                jdbc.update("INSERT INTO effects VALUES (2)");
                if (failOnce.getAndSet(false)) throw new IllegalStateException("injected step failure");
                return RepeatStatus.FINISHED;
            }, transactionManager).build();
            var job = new JobBuilder("restart-proof", repository).start(first).next(second).build();
            var runner = new TaskExecutorJobOperator();
            var registry = new org.springframework.batch.core.configuration.support.MapJobRegistry();
            registry.register(job);
            runner.setJobRegistry(registry);
            runner.setJobRepository(repository);
            runner.setTaskExecutor(new SyncTaskExecutor());
            runner.afterPropertiesSet();
            var parameters = new JobParametersBuilder().addString("business-date", "2026-11-17").toJobParameters();

            var failed = runner.start(job, parameters);
            assertEquals(BatchStatus.FAILED, failed.getStatus());
            assertEquals(java.util.List.of(1), jdbc.queryForList("SELECT id FROM effects ORDER BY id", Integer.class));

            var restarted = runner.restart(failed);
            assertEquals(BatchStatus.COMPLETED, restarted.getStatus());
            assertEquals(failed.getJobInstance().getInstanceId(), restarted.getJobInstance().getInstanceId());
            assertNotEquals(failed.getId(), restarted.getId());
            assertEquals(java.util.List.of(1, 2), jdbc.queryForList("SELECT id FROM effects ORDER BY id", Integer.class));
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM BATCH_STEP_EXECUTION WHERE STEP_NAME='first'", Integer.class));
            assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM BATCH_STEP_EXECUTION WHERE STEP_NAME='second'", Integer.class));
            assertThrows(JobInstanceAlreadyCompleteException.class, () -> runner.start(job, parameters));
        } finally {
            database.shutdown();
        }
    }
}
