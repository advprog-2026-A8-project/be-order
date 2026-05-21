# JMeter Performance - be-order

## Phase 1 - Baseline (health/docs + read core)
Test plan:
- `order-transaction.jmx`

Isi file token:
- `data/admin_tokens.csv`
- `data/titiper_tokens.csv`

Run before:
`jmeter -n -t performance/jmeter/order-transaction.jmx -l performance/jmeter/results/order-before.jtl -e -o performance/jmeter/results/report-before`

Run after:
`jmeter -n -t performance/jmeter/order-transaction.jmx -l performance/jmeter/results/order-after.jtl -e -o performance/jmeter/results/report-after`

## Phase 2 - Business Flow (checkout/status/cancel/rating + read)
Test plan:
- `order-business-flow.jmx`

Isi semua file data:
- `data/admin_tokens.csv`
- `data/titiper_tokens.csv`
- `data/jastiper_tokens.csv`
- `data/checkout_payloads.csv`
- `data/business_order_ids.csv`

Atau regenerasi otomatis semua data phase-2:
`powershell -ExecutionPolicy Bypass -File performance/jmeter/scripts/regenerate-phase2-data.ps1`

Run before:
`jmeter -n -t performance/jmeter/order-business-flow.jmx -l performance/jmeter/results/order-business-before.jtl -e -o performance/jmeter/results/report-business-before`

Run after:
`jmeter -n -t performance/jmeter/order-business-flow.jmx -l performance/jmeter/results/order-business-after.jtl -e -o performance/jmeter/results/report-business-after`

Lihat APDEX report:
- `performance/jmeter/results/report-before/index.html`
- `performance/jmeter/results/report-after/index.html`
- `performance/jmeter/results/report-business-before/index.html`
- `performance/jmeter/results/report-business-after/index.html`
