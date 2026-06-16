# Install KAI OS

KAI OS is distributed as a JVM CLI. Java 17+ is required.

Check the installed version at any time:

```bash
kaios --version
```

Running `kaios` with no arguments prints the product model, concrete use cases, and the three-step Evidence OS path from onboarding to current-change review to evidence gates. It exits successfully, so it is safe to use as a first smoke test.
Run `kaios next` when you want one read-only recommendation for the current workspace before writing config, CI, or evidence files.
Common aliases execute directly: `kaios start --no-ci`, `kaios status`, `kaios ls`, `kaios proc`, and `kaios audit`.
Mistyped commands still show a suggestion when there is a clear match.
After a saved run exists, `kaios ps`, `kaios inspect`, `kaios trace`, `kaios capsule`, `kaios evidence`, `kaios report`, and `kaios export` default to the newest run. When no snapshots exist yet, the CLI points back to `kaios quickstart`, `kaios demo`, `kaios setup --ci`, and `kaios gate`.
If `kaios.json` is missing, `kaios config show`, `kaios config validate`, and `kaios doctor` point back to `kaios doctor --fix --dry-run`, `kaios doctor --fix`, or `kaios setup --ci`; use `kaios config templates` when you want to choose a different workflow template before setup.

## Choose The First Command

| Need | Command |
| --- | --- |
| Experience the full Evidence OS loop in a disposable repo | `kaios tour` |
| See the product model and local entrypoints | `kaios` |
| Get one read-only recommendation | `kaios next` |
| Preview onboarding writes | `kaios quickstart --dry-run` |
| Run the full no-key onboarding path | `kaios quickstart` |
| Run onboarding without writing GitHub Actions | `kaios quickstart --no-ci` |
| Review the current Git change set | `kaios review` |
| Verify an existing `kaios.json` workflow | `kaios gate --config kaios.json` |

Every core command also supports local help with examples and notes:

```bash
kaios
kaios next --help
kaios quickstart --help
kaios setup --help
kaios gate --help
kaios verify --help
kaios review --help
kaios demo --help
kaios run --help
kaios help run
kaios help config show
kaios help capsule
kaios help bug-report
```

## Homebrew

```bash
brew tap morning-verlu/tap
brew install kaios

kaios next
kaios tour
kaios quickstart --dry-run
kaios quickstart
```

`kaios quickstart --dry-run` previews generated files, commands, and CI behavior without writing anything.

Local-only first run:

```bash
kaios quickstart --no-ci
```

Manual path:

```bash
kaios demo
kaios setup --ci
kaios gate
```

After `kaios gate` reports `status: ready`, create an artifact with `kaios run --index . --context README.md --out artifacts/project.md --trace-out artifacts/trace.json --force "summarize this project"`.
If your project uses `README.markdown` or `README`, use that path instead. README matching is case-insensitive, so lowercase variants work too. If it has no README, omit `--context`; `kaios next` chooses the first available README path automatically.
When the workspace has local Git changes, use `kaios review` for the first-class review path that writes `artifacts/change-review.md`, `artifacts/change-review.trace.json`, and `artifacts/change-review.capsule.json`.

## Hosted Installer

The installer downloads the release ZIP, verifies the published SHA256 checksum, and links `kaios` under `$HOME/.kaios/bin`.

```bash
curl -fsSL https://morning-verlu.github.io/KAI/install.sh | sh
export PATH="$HOME/.kaios/bin:$PATH"
kaios tour
kaios next
```

Set `KAIOS_INSTALL_DIR` to install somewhere else:

```bash
curl -fsSL https://morning-verlu.github.io/KAI/install.sh | KAIOS_INSTALL_DIR="$HOME/.local/kaios" sh
```

## GitHub Codespaces

Use Codespaces when you want to try KAI OS without installing Java or Gradle locally:

[Open KAI OS in GitHub Codespaces](https://codespaces.new/morning-verlu/KAI?quickstart=1)

The dev container installs the CLI distribution with:

```bash
./gradlew installDist --no-daemon
```

Then run:

```bash
build/install/kaios-cli/bin/kaios tour
```

See [CODESPACES.md](CODESPACES.md) for the full no-local-install path.

## Docker

Use Docker when you want to try KAI OS without installing Java or Gradle on the host:

```bash
docker build -t kaios:local .
docker run --rm kaios:local tour
```

The Dockerfile uses a prebuilt Java 17 runtime base image and installs the published `v0.3.1` release ZIP by default. Override the release source when testing another published version:

```bash
docker build --build-arg KAIOS_VERSION=0.3.1 -t kaios:local .
```

To keep generated artifacts on the host:

```bash
mkdir -p artifacts/docker-tour
docker run --rm -v "$PWD/artifacts/docker-tour:/work" kaios:local tour --dir /work/tour
```

Verify the Docker path end to end:

```bash
./scripts/docker-smoke.sh
```

To reuse an image that already exists locally:

```bash
./scripts/docker-smoke.sh --no-build
```

If Docker Hub layer downloads are slow, run the lightweight preflight first:

```bash
./scripts/docker-smoke.sh --preflight
```

## Download ZIP

```bash
curl -L -o kaios-0.3.1.zip https://github.com/morning-verlu/KAI/releases/download/v0.3.1/kaios-0.3.1.zip
unzip kaios-0.3.1.zip
./kaios-0.3.1/bin/kaios tour
./kaios-0.3.1/bin/kaios quickstart
```

## Build From Source

```bash
git clone https://github.com/morning-verlu/KAI.git
cd KAI
./gradlew test installDist
build/install/kaios-cli/bin/kaios tour
build/install/kaios-cli/bin/kaios next
build/install/kaios-cli/bin/kaios quickstart
```

Useful next commands after the first artifact:

```bash
kaios analyze . --format json --out artifacts/analysis.json --force
kaios tour
kaios next
kaios quickstart
kaios doctor --json
kaios review
kaios evidence --summary
kaios doctor --fix --dry-run
kaios doctor --fix
kaios doctor --config workflows/research.json --json
kaios setup --ci
kaios gate
kaios config show
kaios config validate --json
kaios run --index . --trace-out artifacts/trace.json --force "summarize this project"
kaios runs --json
kaios ps
kaios inspect
kaios trace
kaios trace --json
kaios trace --json --out artifacts/trace.json --force
kaios gate --baseline artifacts/baseline.capsule.json --check
kaios bug-report --out artifacts/kaios-bug-report.md --force
kaios bug-report --config workflows/research.json --out artifacts/kaios-bug-report.md --force
```
