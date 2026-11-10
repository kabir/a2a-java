# Upstream clarification issue follow-up

User authorization: after the clarified same-version/two-exchange question, user requested a spec issue if the RC lacks the rule. Exact duplicate found; no new issue/comment created. Read-only check 2026-11-10 around14:06UTC.

```sh
gh api -X GET search/issues -f q='repo:a2aproject/A2A is:issue "live" "HTTP"' -f per_page=30 --jq '.items[] | {number,title,state,html_url}'
gh api -X GET search/issues -f q='repo:a2aproject/A2A is:issue "exchange" "uplink"' -f per_page=30 --jq '.items[] | {number,title,state,html_url}'
gh issue view 2415 --repo a2aproject/A2A --json number,title,state,url,body,author,createdAt,updatedAt,comments
gh api repos/a2aproject/A2A/issues/2415
gh api repos/a2aproject/A2A/issues/2415/comments
```

First gh issue view failed network inside sandbox; escalated read succeeded. API issue/comments reads succeeded. Existing issue2415 is OPEN and addresses the exact opening/uplink/downlink rejection ambiguity. Its one comment links PR2411; no solution is supplied. Keep G5 blocked pending authoritative resolution.
