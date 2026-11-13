# T1 API probe results

## State

T1 passed for the frozen Phase 1 API and the installed Phase 1 candidate baseline. The probe used Java 17.0.18, Error Prone 2.42.0, and NullAway 0.12.10. This clears the T2 prerequisite; T7 must repeat the probe against the post-T2 candidate. No Java 21 record-pattern result is claimed.

The user explicitly directed the T1 repair on 2026-11-11 so the main implementation can continue. This authorizes the bounded T1 continuation, preserves the three earlier failed attempts, and does not reset the run deadline or iteration budget.

## Failure accounting and root cause

Attempts 1-3 remain recorded in the progress ledger. The first user-authorized retry removed the unsupported RequireExplicitNullMarking option and the non-profile ExhaustiveOverride option. That compile still failed because the processor path lacked org.checkerframework.dataflow-nullaway:3.49.2, reported as NoClassDefFoundError for org/checkerframework/nullaway/dataflow/cfg/node/NodeVisitor. The earlier dependency classpath used includeScope=compile, which excludes that runtime dependency, and the POM reused the SDK source classpath as javac processorpath. The failed javac argument file is retained as attempt4-javac.args.

The corrected POM now reads two classpaths. The SDK source classpath remains isolated from the Error Prone/NullAway processor classpath; generate the latter with includeScope=runtime. The probe still intentionally omits the src/test exclusion because its inputs are under src/main/java.

## Commands and results

1. Runtime processor dependency classpath:

    mvn -B -ntp -f .claude/architecture/a2a-1.1/phase-2/api-probes/pom.xml dependency:build-classpath -Dmdep.outputFile=/tmp/a2a-phase2-probe-runtime.cp -DincludeScope=runtime

   Exit 0; Maven reported BUILD SUCCESS. The classpath includes dataflow-nullaway-3.49.2.jar.

2. Frozen legacy SDK compile:

    mvn -B -ntp -f .claude/architecture/a2a-1.1/phase-2/api-probes/pom.xml -Dphase2.sdk.classpathFile=/tmp/a2a-phase2-legacy.cp -Dphase2.processor.classpathFile=/tmp/a2a-phase2-probe-runtime.cp verify

   Exit 0; Error Prone/NullAway and both probe sources compiled. Java 17 emitted the expected preview-feature note for LegacySwitch.

3. Candidate SDK classpath construction replaced the 11 frozen SDK archive entries with the installed Phase 1 candidate jars under ~/.m2/repository/org/a2aproject/sdk; external dependency entries were retained. Exact command:

    python3 -c 'from pathlib import Path; old=Path("/tmp/a2a-phase2-legacy.cp").read_text().strip().split(":"); suffix="-1.5.1.Final-SNAPSHOT.jar"; new=[]; changed=0
    for x in old:
        if "/legacy-compile-sdk/" in x:
            name=Path(x).name
            artifact=name[:-len(suffix)]
            candidate=Path("/Users/kabir/.m2/repository/org/a2aproject/sdk")/artifact/"1.5.1.Final-SNAPSHOT"/name
            assert candidate.is_file(), candidate
            new.append(str(candidate)); changed+=1
        else: new.append(x)
    assert changed==11, changed
    out=Path("/tmp/a2a-phase2-candidate.cp"); out.write_text(":".join(new)); print(f"candidate SDK jars={changed}; classpath entries={len(new)}; output={out}")'

4. Candidate source compile:

    mvn -B -ntp -f .claude/architecture/a2a-1.1/phase-2/api-probes/pom.xml -Dphase2.sdk.classpathFile=/tmp/a2a-phase2-candidate.cp -Dphase2.processor.classpathFile=/tmp/a2a-phase2-probe-runtime.cp verify

   Exit 0; Error Prone/NullAway and both probe sources compiled.

5. Old and candidate bytecode runtime checks:

    python3 -c 'from pathlib import Path; import subprocess; cp=Path("/tmp/a2a-phase2-candidate.cp").read_text().strip(); target=Path(".claude/architecture/a2a-1.1/phase-2/api-probes/target/classes"); legacy=Path(".claude/architecture/a2a-1.1/phase-2/evidence/legacy-api-probes.jar");
    for label,first in (("old compiled bytecode",legacy),("candidate compiled bytecode",target)):
        print(label+":",flush=True)
        subprocess.run(["java","--enable-preview","-cp",str(first)+":"+cp,"phase2probe.LegacyCaller"],check=True)'

   Both executions exited 0 and printed Task record components=6. The old-compiled caller links against the installed Phase 1 candidate; the current record shape is unchanged at this baseline.

## Retained inputs

- Frozen SDK input jars match the hashes in phase-1/compatibility/legacy-compile-manifest.json.
- Candidate classpath text SHA-256: 969d9f7a914088bf7ba707956c198f45a7e276a7fe9ea92babda240e2612fbdd.
- Runtime processor classpath text SHA-256: ceff66d8fa53971d9fcf683862529e5e6274d56f7edb825abf9a46465400a336.
- Retained old-compiled probe jar: legacy-api-probes.jar, SHA-256 8d03d9ce6004ff9dff7c1ca21657d4523acfaf98f9005aed1650b7d0bb15fe8a; hash is also in legacy-api-probes.jar.sha256.
Candidate SDK artifact SHA-256 values (frozen value → installed Phase 1 candidate):

| Artifact | Frozen | Candidate |
| --- | --- | --- |
| a2a-java-sdk-client | a858d6d1bcb8cf4d7cd89f495f79df6f91d7a294fb15ef7a73bf1237f3776266 | 090b61ce06c921cac555459595b86ceb938742e911ee715cba866baa9552459e |
| a2a-java-sdk-client-transport-spi | 40094e486190c717cea4040bfa0c41afe95e46b25b4365f7e3a6dc2f1190b9fa | 6e8265a7d851486a328597cf58f0e35a7b4554b9bc5011d88c1f0b54c9b64d2e |
| a2a-java-sdk-client-transport-jsonrpc | 83756a3864225621480b5d21f0be81c1d197f51ce6f9eba51e746e34c2dcdc91 | aa3009d1296c46b8c04d7b7dc33a8423d2626378f198e0760f468f93cfdc2e95 |
| a2a-java-sdk-client-transport-rest | 43799ad05f541487d829b8c6598509086be44b9b0abef10eb7595c5445becb3b | cd5d9d4c81abbd4db40083ec37d9fdd0e8a56d1cedb6adc659e48023bafe4f54 |
| a2a-java-sdk-client-transport-grpc | 873e589f4be917143a58005fcb671d16cc3f81469d578e4fbccfc92589e0f6d1 | 4497a2e1d79664b35328cfe6f3a62927f23ed4ac0c43b5576567b21c345fc717 |

The other six SDK artifacts are byte-identical to the frozen inputs listed in
legacy-compile-manifest.json. No frozen input was changed.

## Accounting and handoff

The original T1 attempts 1-3 remain failures. The explicitly user-authorized continuation used two more fix/verify cycles: attempt 4 exposed the processor-classpath issue; attempt 5 passed against both frozen and installed candidate classpaths. Cumulative Phase 2 accounting is iteration 2/10, T1 attempts 5 total (4 failed, 1 passed), no-progress 0. The original conservative stop of 2026-11-11 13:30 UTC remains unchanged; no budget or historical counter was reset.

T1 now passes and T2 may proceed under the existing D1 and Phase 2 authorization. No production Java, generated proto, or runtime module was changed or created by this remediation.
