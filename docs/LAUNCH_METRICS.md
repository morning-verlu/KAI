# Launch Metrics

Use this when posting KAI OS externally. The goal is not to celebrate vanity metrics; it is to decide whether the next bottleneck is distribution, link click-through, README conversion, or contributor intake.

## Snapshot Command

```bash
./scripts/launch-metrics.sh
```

The script records:

- repository stars, forks, watchers, description, homepage, and social preview state
- repository topics
- GitHub traffic views and clones
- top referrers and paths when GitHub exposes them

Use campaign-tagged links in launch posts where possible. The second-wave X link is:

```text
https://morning-verlu.github.io/KAI/evidence-viewer.html?utm_source=x&utm_medium=social&utm_campaign=second_wave
```

The Evidence Viewer links back to GitHub with `utm_source=evidence_viewer`, so a later traffic readout can distinguish direct GitHub clicks from viewer-assisted clicks when the hosting/referrer surface exposes enough detail.

For the response checklist after a post goes live, use
[launch-posts/follow-up-playbook.md](launch-posts/follow-up-playbook.md).

If measuring an X post, pass the visible analytics from the browser:

```bash
X_POST_URL="https://x.com/wurslu/status/..." \
X_IMPRESSIONS=1 \
X_ENGAGEMENTS=0 \
X_LINK_CLICKS=0 \
X_REPLIES=0 \
X_REPOSTS=0 \
X_LIKES=0 \
X_BOOKMARKS=0 \
./scripts/launch-metrics.sh
```

## Cadence

Capture:

- before posting
- roughly 2 hours after posting
- 24 hours after posting
- 72 hours after posting

## Saved Snapshots

- [2026-06-17 launch snapshot](launch-snapshots/2026-06-17.md): distribution still at 0 GitHub views; next action is Kotlin/JVM community posting, not more README copy.

## How To Interpret

- GitHub views stay at 0: distribution failed. Change channel before changing product copy.
- Impressions are low but link clicks exist: the message is plausible, but the channel is weak.
- Impressions are healthy but link clicks are near 0: change the first sentence and preview image.
- GitHub views rise but stars do not: improve README first screen, evaluator path, and why-star rationale.
- Stars rise but issues/questions do not: add more contributor hooks and concrete help-wanted tasks.

## Current Known Baseline

The first text-first X post did not enter distribution:

```text
X impressions: 1
X link clicks: 0
GitHub views: 0
GitHub stars: 0
```

That is why the second wave should lead with the product-proof image and community surfaces.
