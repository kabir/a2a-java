# Minimal HTTP live-exchange ambiguity

Source pin: `a2aproject/A2A@78e1d6a1f7f118c8e9bc830b04a8579c1119cb78`, docs/specification.md L453–481, specification/a2a.proto L44–54/L788–800/L977–992. #2411 at d318e2e074bcc967ce5f09b746304a72a9794f03 strengthens locality but supplies no HTTP association.

Two tabs/exchanges A and B use the **same principal**, tenant, endpoint and task T. Both are allowed to observe the same task (RC prose L843–858). Distinct names below are explanatory labels, not wire fields.

1. A opens:

```http
POST /message:live HTTP/1.1
A2A-Version: 1.1
Content-Type: application/a2a+json

{"subscribe":{"id":"T"}}
```

Server keeps response DA open:

```http
HTTP/1.1 200 OK
Content-Type: text/event-stream

data: {"task":{"id":"T","status":{"state":"TASK_STATE_WORKING"},"generation":"7"}}
```

2. B sends the identical POST and gets separate open downlink DB with the same Task snapshot.

3. A submits an input that the agent currently declines:

```http
POST /message:live HTTP/1.1
A2A-Version: 1.1
Content-Type: application/a2a+json

{"sendMessage":{"message":{"taskId":"T","messageId":"m-A","role":"ROLE_USER","parts":[{"text":"revise"}]}}}
```

RC says this later uplink is short-lived and returns only current Task acknowledgement, while a per-input rejection goes on **the downlink**:

```text
error payload: {"error":{"taskId":"T","code":"UNAVAILABLE","referenceId":"m-A"}}
```

Which downlink, DA or DB, does the server select? The uplink supplies task T but no exchange selector; same principal/tenant cannot distinguish A from B. `m-A` is new and the downlink subscriptions did not declare message ID membership. JSON-RPC's equivalent uplink request id is new too and does not name either opening call. Neither TCP-connection affinity nor HTTP/2 stream affinity is a normative rule.

Sending to both broadcasts request rejection to another exchange; guessing one may miss the caller. Returning rejection on the short call is a plausible design decision, but conflicts with the RC downlink statement unless explicitly resolved and encoded by a binding. #2411 says caller-only and delegates to the binding; its binding sections still supply no rule. Even deciding whether this POST is a new opening exchange versus the short uplink of A lacks a selector.

Separate uncertainty: if the short call returns a current Task with no m-A in a limited timeline, is that success/commit evidence, or merely an acknowledgement preceding a downlink rejection? RC has no explicit response contract distinguishing these. Do not report COMMITTED solely from an arbitrary current snapshot/generation.

Needed decision: authoritative opening/uplink discrimination and caller-local rejection/positive-ack association for both JSON-RPC and HTTP+JSON, while preserving the task-bound exchange and multiple-observer requirements. This trace proposes **no new field, header, extension, status RPC or receipt**.


## Upstream clarification tracking

User-requested issue filing was checked against existing issues. [A2A #2415](https://github.com/a2aproject/A2A/issues/2415) already covers this HTTP association ambiguity and remains OPEN at the fresh check on 2026-11-10. Its comment links PR2411 without supplying a routing rule. Follow this issue; do not infer resolution from its existence or create a duplicate. Read-only snapshots: [issue](evidence/upstream-issue-2415.json), [comments](evidence/upstream-issue-2415-comments.json). No external GitHub state was changed.
