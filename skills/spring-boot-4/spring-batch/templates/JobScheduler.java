// Copy-paste starting point for triggering a Spring Batch job on a schedule (Batch 6 / Boot 4).
// Pair with application.yml:
//   spring.batch.job.enabled: false      # don't auto-run jobs on startup
//   spring.batch.jdbc.initialize-schema: never   # (batch-jdbc starter) manage BATCH_* tables via Flyway
//
// Batch 6: JobLauncher and JobExplorer are consolidated into JobOperator — inject that.
// The default JobOperator is SYNCHRONOUS — start(...) blocks until the job finishes.
// Configure an async TaskExecutor (@BatchTaskExecutor bean) if you need fire-and-forget.

@Component
@RequiredArgsConstructor
@Slf4j
public class JobScheduler {

    private final JobOperator jobOperator;
    private final Job exampleJob;

    @Scheduled(cron = "0 0 2 * * *") // 02:00 daily
    public void run() {
        JobParameters params = new JobParametersBuilder()
            .addString("status", "COMPLETED")               // identifying — part of the JobInstance key
            .addLong("run.id", System.currentTimeMillis())  // identifying + unique — forces a fresh instance
            // .addString("requestId", id, false)           // non-identifying — logged, not part of the key
            .toJobParameters();

        try {
            JobExecution execution = jobOperator.start(exampleJob, params); // NOT start("name", Properties) — deprecated
            log.info("Job {} finished with status {}", exampleJob.getName(), execution.getStatus());
        } catch (JobExecutionException e) {
            // JobInstanceAlreadyCompleteException, JobRestartException, InvalidJobParametersException, ...
            log.error("Failed to launch {}", exampleJob.getName(), e);
        }
    }
}
