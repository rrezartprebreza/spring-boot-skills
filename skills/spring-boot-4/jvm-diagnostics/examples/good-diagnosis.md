# GOOD: synthetic offline diagnosis

Input: Boot service, JDK 21, three supplied thread snapshots and executor metrics.
Observed: active workers remain at the configured limit; queue depth increases; samples show
workers waiting on the same outbound dependency. No lock cycle is reported.
Hypothesis: downstream latency is exhausting workers, not a proven JVM deadlock.
Next evidence: correlate dependency latency/error metrics over the same interval.
Proposed fix experiment: bounded client deadline and admission control in staging.
No live attachment, restart, dump upload or pool-size change is authorized by these inputs.
