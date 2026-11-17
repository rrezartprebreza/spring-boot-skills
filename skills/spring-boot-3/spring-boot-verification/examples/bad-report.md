# BAD: misleading readiness report

"The build returned zero, so everything is production-ready."
The command used -DskipTests, scanned no dependencies and read yesterday's coverage report.
Missing integration-test infrastructure was silently ignored.
