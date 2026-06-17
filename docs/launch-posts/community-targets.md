# Community Targets

Status: operating notes, not proof that anything has been posted.

Use this after `post-now.md` when choosing where to publish the next KAI OS update. The current launch diagnosis is still distribution failure, so the goal is to reach Kotlin/JVM and infrastructure audiences without posting the same copy everywhere.
After any post goes live, use [follow-up-playbook.md](follow-up-playbook.md) to decide whether the next action is replying, switching channel, or improving conversion.

## Priority Order

| Priority | Channel | Why it fits | Use | Risk |
| --- | --- | --- | --- | --- |
| 1 | Kotlin Slack / Kotlin community | Official Kotlin community surface; best chance of Kotlin API feedback | Use the Kotlin/JVM version in `post-now.md` | Requires joining or having access |
| 2 | r/Kotlin | Public Kotlin-specific audience; open-source library posts appear when useful and on-topic | Use the Reddit discussion version from `community-wave.md` | Must avoid self-promo tone |
| 3 | Show HN | Good fit only because KAI OS is runnable and no-key; HN expects discussion and feedback | Use the Show HN version from `post-now.md` | Needs maintainer available to answer comments |
| 4 | LinkedIn engineering audience | Useful if the maintainer account has JVM/backend followers | Use the visual version from `post-now.md` | Often weaker for open-source stars |

## Source Notes

- Kotlin's official community page links Slack, Reddit, StackOverflow, YouTube, LinkedIn, the Kotlin blog, and issue tracker as "Keep in Touch" surfaces: https://kotlinlang.org/community/
- Kotlin Discussions points people to the Kotlin Slack sign-up link from the community page: https://discuss.kotlinlang.org/t/how-to-join-kotlinlang-slack-com/29771
- r/Kotlin rules emphasize being civil, avoiding spam, staying on Kotlin topics, and avoiding low-effort fluff: https://www.reddit.com/r/Kotlin/
- Show HN is for something people can try, and the guidelines ask for a title beginning with `Show HN`: https://news.ycombinator.com/showhn.html

## Recommended Next Post

Post the Kotlin/JVM community version first:

```text
https://morning-verlu.github.io/KAI/evidence-viewer.html?utm_source=kotlin_community&utm_medium=community&utm_campaign=post_now
```

Use this opening angle:

```text
I am building KAI OS, a Kotlin/JVM runtime for AI agent evidence.

It is not trying to be a Kotlin LangChain clone. The runtime model is closer to OS infrastructure:

Agent = Process
Workflow = Scheduler
Tool = Syscall
Memory = Process state
```

Ask for specific feedback:

```text
I would especially like feedback on whether the Kotlin runtime API feels idiomatic, whether the Agent = Process model is useful for JVM teams, and whether replayable capsules / CI evidence gates would help maintainers trust agent reviews.
```

## Post-Publish Loop

After any post:

1. Save the URL in issue #7.
2. Run `./scripts/launch-metrics.sh`.
3. Use [follow-up-playbook.md](follow-up-playbook.md) at roughly +2h, +24h, and +72h.
4. If GitHub views stay at 0, switch channel before changing the project page again.
5. If views rise but stars stay flat, inspect the README first screen and Evidence Viewer conversion path.

## Do Not Do

- Do not post the same text to all communities.
- Do not ask for upvotes.
- Do not claim production maturity; say it is early and local-first.
- Do not make the post about installation mechanics. Lead with inspectable runtime evidence.
- Do not post Show HN unless the maintainer can stay around to answer questions.
