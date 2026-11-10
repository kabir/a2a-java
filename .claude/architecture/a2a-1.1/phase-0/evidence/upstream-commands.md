# Exact evidence commands

Metadata initially queried around 13:18 UTC on 2026-11-10, then freshly rechecked between the exact UTC timestamps in `query-start-utc.txt` and `query-end-utc.txt`. Snapshot retrieval preceded the final metadata recheck; unchanged PR head and RC pin identify those snapshots.

All commands are read-only against GitHub. Local files are under `/tmp/a2a-phase0-spec` only. Initial redirected commands failed in sandbox with `error connecting to api.github.com`; they were rerun with `sandbox_permissions=require_escalated` and succeeded (exit 0). GitHub operations have no external mutations.

```sh
mkdir -p /tmp/a2a-phase0-spec
gh api repos/a2aproject/A2A/tags --paginate
gh pr view 2411 --repo a2aproject/A2A --json number,state,title,headRefOid,baseRefOid,mergedAt,updatedAt,url,body

gh api repos/a2aproject/A2A/releases --paginate > /tmp/a2a-phase0-spec/releases.json
gh api repos/a2aproject/A2A/tags --paginate > /tmp/a2a-phase0-spec/tags.json
gh pr view 2411 --repo a2aproject/A2A --json number,state,title,headRefOid,baseRefOid,mergedAt,updatedAt,url,body > /tmp/a2a-phase0-spec/pr2411.json
gh pr diff 2411 --repo a2aproject/A2A > /tmp/a2a-phase0-spec/pr2411.diff
gh api -H 'Accept: application/vnd.github.raw+json' 'repos/a2aproject/A2A/contents/docs/specification.md?ref=78e1d6a1f7f118c8e9bc830b04a8579c1119cb78' > /tmp/a2a-phase0-spec/rc-specification.md
gh api -H 'Accept: application/vnd.github.raw+json' 'repos/a2aproject/A2A/contents/specification/a2a.proto?ref=78e1d6a1f7f118c8e9bc830b04a8579c1119cb78' > /tmp/a2a-phase0-spec/rc-a2a.proto
gh api -H 'Accept: application/vnd.github.raw+json' 'repos/a2aproject/A2A/contents/docs/specification.md?ref=d318e2e074bcc967ce5f09b746304a72a9794f03' > /tmp/a2a-phase0-spec/pr-specification.md
gh api -H 'Accept: application/vnd.github.raw+json' 'repos/a2aproject/A2A/contents/specification/a2a.proto?ref=d318e2e074bcc967ce5f09b746304a72a9794f03' > /tmp/a2a-phase0-spec/pr-a2a.proto
gh api 'repos/a2aproject/A2A/git/trees/78e1d6a1f7f118c8e9bc830b04a8579c1119cb78?recursive=1' --jq '.tree[] | select(.path | test("(adr-002|binding|live)")) | .path'
gh api -H 'Accept: application/vnd.github.raw+json' 'repos/a2aproject/A2A/contents/adrs/adr-002-coherent-task-history.md?ref=78e1d6a1f7f118c8e9bc830b04a8579c1119cb78' > /tmp/a2a-phase0-spec/rc-adr002.md

# Exact-timestamped final metadata recheck, all exit 0:
date -u '+%Y-%m-%dT%H:%M:%SZ' > /tmp/a2a-phase0-spec/query-start-utc.txt
gh api repos/a2aproject/A2A/tags --paginate > /tmp/a2a-phase0-spec/tags.json
gh api repos/a2aproject/A2A/releases --paginate > /tmp/a2a-phase0-spec/releases.json
gh pr view 2411 --repo a2aproject/A2A --json number,state,title,headRefOid,baseRefOid,mergedAt,updatedAt,url,body > /tmp/a2a-phase0-spec/pr2411.json
date -u '+%Y-%m-%dT%H:%M:%SZ' > /tmp/a2a-phase0-spec/query-end-utc.txt
```

Local analysis uses `rg -n` and `sed -n` on saved snapshots. GitHub anchors in findings use the saved-file line numbers, not rendered website-generated tables.

SHA256SUMS is generated using Python hashlib over the saved evidence files. `pr2411.diff` is a current gh diff verified against unchanged pinned head/base metadata; pinned source files are the authoritative reproducible comparison.
