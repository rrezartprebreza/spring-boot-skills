// Copy-paste starting point for triggering a Spring Batch job on a schedule.
// Pair with application.yml:
//   spring.batch.job.enabled: false      # don't auto-run jobs on startup
//   spring.batch.jdbc.initialize-schema: never   # manage BATCH_* tables via Flyway in prod
//
// The default JobLauncher is SYNCHRONOUS — run(...) blocks until the job finishes.
// Inject an async TaskExecutor-backed launcher if you need fire-and-forget.

@Component
@RequiredArgsConstructor
@Slf4j
public class JobScheduler {

    private final JobLauncher jobLauncher;
    private final Job exampleJob;

    @Scheduled(cron = "0 0 2 * * *") // 02:00 daily
    public void run() {
        JobParameters params = new JobParametersBuilder()
            .addString("status", "COMPLETED")               // identifying — part of the JobInstance key
            .addLong("run.id", System.currentTimeMillis())  // identifying + unique — forces a fresh instance
            // .addString("requestId", id, false)           // non-identifying — logged, not part of the key
            .toJobParameters();

        try {
            JobExecution execution = jobLauncher.run(exampleJob, params);
            log.info("Job {} finished with status {}", exampleJob.getName(), execution.getStatus());
        } catch (JobExecutionException e) {
            // JobInstanceAlreadyCompleteException, JobRestartException, etc.
            log.error("Failed to launch {}", exampleJob.getName(), e);
        }
    }
}
