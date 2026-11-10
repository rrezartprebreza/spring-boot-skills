# Prompt-only boundary

Expose fetchDocument(tenantId, documentId) to the model and accept any tenantId because
the system prompt says "only access the current user's data".

Then hide forbidden text from the final answer after fetching it. Both controls act too
late or rely on model compliance: enforce identity and access in the retrieval/tool layer.
