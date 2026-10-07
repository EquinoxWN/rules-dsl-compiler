# OSV-Scanner (https://google.github.io/osv-scanner/) checks every resolved Maven dependency.
OSV ?= osv-scanner

.PHONY: setup lint test demo bench audit ci

# MVNLOCAL (set by the author's repoenv helper) keeps the Maven repository inside the repo; empty in CI.
MVN = mvn -B -q $(MVNLOCAL)
RULES = java -cp target/classes portfolio.rules.Main

setup:
	$(MVN) -DskipTests dependency:resolve

# Compile with every javac warning enabled and treated as an error.
lint:
	$(MVN) -DskipTests compile

# 165 tests: lexer, Pratt parser, round-trip and span properties, type checker, diagnostics
# snapshots, CLI, schema, and 28,000 random inputs that must never crash the checker.
test:
	$(MVN) verify

# Check the example rules with the command-line tool (the last one fails on purpose).
demo: lint
	$(RULES) check --schema examples/pricing.schema --expect number examples/tier-discount.rule
	$(RULES) check --schema examples/pricing.schema --expect bool examples/eligible.rule
	$(RULES) ast examples/gold-discount.rule
	-$(RULES) check --schema examples/pricing.schema examples/errors/several-errors.rule

bench:
	@echo "M3: tree-walking interpreter vs bytecode VM throughput, and fuzzing hours with zero crashes"

# Known vulnerabilities in every resolved Maven dependency, test scope included.
audit:
	mvn -B -q org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom -DoutputFormat=json -DoutputName=bom -DincludeTestScope=true
	$(OSV) scan source -L target/bom.json

ci: setup lint test demo
