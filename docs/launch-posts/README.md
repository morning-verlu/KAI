# Launch Posts

Copy-ready drafts for external launch channels.

Status: drafts only. Nothing in this directory proves a post has been published.

## Preflight

Before posting externally:

1. Verify the first-run path:

```bash
./scripts/repository-ci-smoke.sh
```

2. Confirm the GitHub social preview image is uploaded in repository settings:

```text
docs/assets/kaios-social-card.png
```

For Evidence Viewer-first social posts, use the product-proof image:

```text
https://morning-verlu.github.io/KAI/assets/kaios-evidence-proof.png
```

3. Verify GitHub metadata:

```bash
gh repo view morning-verlu/KAI --json description,usesCustomOpenGraphImage,stargazerCount
```

4. Use the post that matches the channel:

- [First external wave](first-wave.md)
- [Evidence Viewer-first posts](evidence-viewer-first.md)
- [Short social post](short-social.md)
- [Second wave visual post](second-wave.md)
- [X / LinkedIn thread](x-linkedin-thread.md)
- [Kotlin community post](kotlin-community.md)
- [Show HN post](show-hn.md)
- [Reddit post](reddit.md)
- [Why Star KAI OS](../WHY_STAR.md)
- [Trust Matrix](../TRUST_MATRIX.md)

## Core Message

KAI OS is a local-first Evidence OS for AI agents in Kotlin.

```text
Agent    = Process
Workflow = Scheduler
Tool     = Syscall
Memory   = Process state
```

Lead with product evidence, not installation mechanics:

```text
https://morning-verlu.github.io/KAI/evidence-viewer.html
```

For cold audiences, lead with the Evidence Viewer first. It shows the product surface without requiring Java, Gradle, Docker, Codespaces, or an API key. Follow with `kaios tour` when someone wants the hands-on path:

```bash
curl -fsSL https://morning-verlu.github.io/KAI/install.sh | sh
export PATH="$HOME/.kaios/bin:$PATH"
kaios tour
```

Use the Evidence Artifact Map when the channel supports images:

```text
https://morning-verlu.github.io/KAI/assets/kaios-evidence-map.png
```

Use the product-proof image for cold social feeds:

```text
https://morning-verlu.github.io/KAI/assets/kaios-evidence-proof.png
```
