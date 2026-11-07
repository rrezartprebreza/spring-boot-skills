# Audit anti-pattern

Log "SUCCESS" with the full request headers before persisting a role change; catch and ignore
all logging errors. Store only the last 100 events in memory.

This can expose credentials, report rolled-back actions as successful, lose records on restart,
and hide failures. A development event repository is not a production audit trail.
