# Execution ledger — plan: 2026-10-21-rabbitmq-topology.md

User approved implementation on 2026-10-21. Working in existing dev/son checkout per approved inline plan; no commits requested.
Ruling: No new worktree or test files — approved minimal runtime plan and user preference govern execution; broker tests use an isolated Compose project/volume.
Pre-flight: Task 1 JSON/conf -> Task 2 mounts -> Task 3 fresh boot/UI; paths and vhost agree.
Task 1: static validation passed: 3 exchange, 30 queues, 30 bindings, 15 policies.
Task 2: Compose config validated without printing secrets; official image tag pulled successfully.
Ruling: Replace await_startup with rabbitmqctl wait --pid 1 --timeout 120 — observed early hook exits 69 before Erlang distribution starts; PID 1 is beam.smp in the native image. Native wait covers startup race without polling script.
Task 3: in progress; isolated project rabbitmq-check-62f83bcd.

Task 1: complete — JSON parsed; Playwright inspected all 30 queues, 30 bindings and 15 effective policies.
Task 2: complete — native boot import + inline account provisioning works on fresh volume and force-recreate; Compose validation rejects missing passwords.
Task 3: fresh isolated start, 5 UI publish cases, 6 payload/ACK checks including reject-to-DLQ and x-death passed. Persistent identity message survived Docker restart and force-recreate; then ACKed.
UI verified three user ACLs/tags, no guest, exchange types, exact policy patterns and priority 10.
Isolated test project/network/volume and temporary override removed after labels verified.
Ruling: Keep local checkout per approved inline plan; no test files and no subagent. Final review performed by author, weaker than independent review. No commits requested.
Ruling: Local native Erlang broker owns 5672/15672 — kept existing process/data, set local env to 5673/15673. Compose/example defaults unchanged. Cost: local users must use the env ports.
Ruling: Redis Compose appeared during execution from another task — preserved its files and included its Compose in README commands to avoid orphan warnings.
Final review: fixed multiline credential validation; six direct checks (valid, guest, multiline username, multiline password, CR, empty password) passed with no test files. Reran full Compose provisioning afterward.
Known verification limit: UI confirms configured ACLs, not AMQP protocol authorization enforcement, mandatory routing, or publisher confirms. Application flows not implemented/tested.
No deferred code findings; single-node and shared-app account limitations remain per approved scope.
