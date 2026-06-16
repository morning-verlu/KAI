# Launch Plan

The launch goal is to make KAI OS legible as a new infrastructure category: a local-first Evidence OS for AI agents in Kotlin.

## Positioning

Lead with:

```text
Local-first Evidence OS for AI agents in Kotlin
Agent = Process, Workflow = Scheduler, Tool = Syscall
```

Avoid positioning it as:

- a chatbot framework
- a Kotlin LangChain clone
- a thin SDK wrapper
- a Homebrew package

Homebrew, hosted installer, release ZIP, and source builds are adoption paths. The product is KAI OS.

## Primary Demo Hook

Use the first-run tour:

```bash
curl -fsSL https://morning-verlu.github.io/KAI/install.sh | sh
export PATH="$HOME/.kaios/bin:$PATH"
kaios tour
```

The tour is the clearest proof because it shows the full Evidence OS loop without requiring an API key or a real project:

- disposable Git workspace
- current-change review
- process table
- process trace
- replayable capsule
- evidence summary
- recovery dry-run report

## Launch Channels

- GitHub Discussion announcement
- GitHub README and launch site
- Hacker News "Show HN"
- Reddit: r/Kotlin first, then broader programming/LLM communities if feedback is healthy
- Kotlin Slack
- LinkedIn/X thread with the demo GIF
- DEV.to or Medium technical post after the first discussion feedback

## First 100-Star Checklist

- [x] CI/test command is green.
- [x] Hosted install command works.
- [x] GitHub Codespaces path exists for no-local-install trials.
- [x] `kaios tour` works without API keys.
- [x] README has a clear first screen.
- [x] GitHub Pages has a direct launch landing page.
- [x] Social preview image works for shared links.
- [x] Demo GIF shows the CLI flow.
- [x] Release ZIP/TAR assets are attached.
- [x] Roadmap reflects v0.3.1 Evidence OS, not stale milestones.
- [x] Contributing guide points new contributors at tour/review/evidence.
- [x] Repository is pinned on the maintainer profile.
- [ ] Short social post is published.
- [x] GitHub Discussion is updated with v0.3.1 tour CTA.
- [ ] Kotlin community post is published.
- [ ] Show HN post is published.
- [ ] Early questions are answered within 24 hours.

## 1000-Star Reality

Stars should come from real interest, not automation or artificial engagement. The controllable path is:

- sharp concept
- no-key working demo
- strong README first screen
- repeated but respectful launch posts
- fast replies to real questions
- quick follow-up releases from user feedback
- visible issues for contributors

See [LAUNCH_KIT.md](LAUNCH_KIT.md) for copy-paste launch posts, channel-specific drafts, reply guidance, and metrics to capture.
