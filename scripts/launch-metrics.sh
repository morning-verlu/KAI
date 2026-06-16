#!/usr/bin/env bash
set -euo pipefail

repo="${KAIOS_REPO:-morning-verlu/KAI}"
now_utc="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"

require() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "missing required command: $1" >&2
    exit 1
  fi
}

metric() {
  local fallback="$1"
  shift
  local attempt
  for attempt in 1 2 3; do
    if "$@" 2>/dev/null; then
      return 0
    fi
    sleep "$attempt"
  done
  printf '%s\n' "$fallback"
}

require gh

repo_line="$(metric '{}' gh api "repos/$repo" --jq '{repo:.full_name,description,homepage:.homepage,stars:.stargazers_count,forks:.forks_count,watchers:.subscribers_count}')"
social_preview="$(metric 'null' gh repo view "$repo" --json usesCustomOpenGraphImage --jq '.usesCustomOpenGraphImage')"
repo_line="${repo_line%?},\"socialPreview\":${social_preview}}"
topics="$(metric '[]' gh api -H 'Accept: application/vnd.github+json' "repos/$repo/topics" --jq '.names')"
views="$(metric '{"count":null,"uniques":null}' gh api "repos/$repo/traffic/views" --jq '{count:.count,uniques:.uniques}')"
clones="$(metric '{"count":null,"uniques":null}' gh api "repos/$repo/traffic/clones" --jq '{count:.count,uniques:.uniques}')"
referrers="$(metric '[]' gh api "repos/$repo/traffic/popular/referrers" --jq '[.[] | {referrer,count,uniques}][0:8]')"
paths="$(metric '[]' gh api "repos/$repo/traffic/popular/paths" --jq '[.[] | {path,title,count,uniques}][0:8]')"

cat <<EOF
# KAI OS Launch Metrics Snapshot

Generated: ${now_utc}
Repository: ${repo}

## Repository

\`\`\`json
${repo_line}
\`\`\`

## Topics

\`\`\`json
${topics}
\`\`\`

## GitHub Traffic

Views:

\`\`\`json
${views}
\`\`\`

Clones:

\`\`\`json
${clones}
\`\`\`

Top referrers:

\`\`\`json
${referrers}
\`\`\`

Top paths:

\`\`\`json
${paths}
\`\`\`
EOF

if [[ -n "${X_POST_URL:-}" || -n "${X_IMPRESSIONS:-}" || -n "${X_LINK_CLICKS:-}" ]]; then
  cat <<EOF

## X Post

- url: ${X_POST_URL:-not recorded}
- impressions: ${X_IMPRESSIONS:-not recorded}
- engagements: ${X_ENGAGEMENTS:-not recorded}
- link_clicks: ${X_LINK_CLICKS:-not recorded}
- replies: ${X_REPLIES:-not recorded}
- reposts: ${X_REPOSTS:-not recorded}
- likes: ${X_LIKES:-not recorded}
- bookmarks: ${X_BOOKMARKS:-not recorded}
EOF
fi

cat <<'EOF'

## Decision Rule

- If GitHub views remain 0 after an external post, distribution failed. Change channel before changing product copy.
- If impressions are low but link clicks are nonzero, keep the message and repost through a stronger community surface.
- If impressions are healthy but link clicks are near 0, improve the first sentence and preview image.
- If GitHub views rise but stars do not, improve README first screen, evaluator path, and "why star" rationale.
- If stars rise but issues/questions do not, add more contributor hooks and concrete "help wanted" tasks.
EOF
