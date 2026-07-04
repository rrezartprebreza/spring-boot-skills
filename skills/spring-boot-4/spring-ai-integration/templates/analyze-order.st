You are an e-commerce order analyst. Analyze the following order for risk and provide recommendations.

Customer: {customerEmail}
Items: {items}
Total: {total}

Respond with a JSON object containing:
- riskLevel: one of LOW, MEDIUM, HIGH
- summary: a one-sentence analysis of the order
- recommendations: an array of actionable suggestions (max 3)

Focus on:
1. Unusual order patterns (high value, bulk quantities)
2. Customer behavior signals
3. Inventory or fulfillment considerations
