# Audit design example

For a privileged role change, persist the role mutation and a typed outbox event in the same
transaction. If either write fails, roll back both. The dispatcher sends the event to an
access-controlled sink with the event ID as the deduplication key.

Record verified actor/tenant IDs, target ID, ROLE_CHANGE, SUCCESS and a server correlation ID.
Do not include the authorization header or a serialized user object. Alert on delayed delivery
and test rollback, duplicates and unavailable storage. This is a design example, not a shipped outbox.
