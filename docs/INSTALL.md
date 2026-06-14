# Install KAI OS

KAI OS is distributed as a JVM CLI. Java 17+ is required.

Check the installed version at any time:

```bash
kaios --version
```

Running `kaios` with no arguments prints the quick start and exits successfully, so it is safe to use as a first smoke test.
Common aliases execute directly: `kaios start --no-ci`, `kaios status`, `kaios ls`, `kaios proc`, and `kaios audit`.
Mistyped commands still show a suggestion when there is a clear match.
After a saved run exists, `kaios ps`, `kaios inspect`, `kaios trace`, `kaios capsule`, `kaios evidence`, `kaios report`, and `kaios export` default to the newest run. When no snapshots exist yet, the CLI points back to `kaios quickstart`, `kaios demo`, `kaios setup --ci`, and `kaios gate`.
If `kaios.json` is missing, `kaios config show` and `kaios config validate` point back to `kaios setup --ci`; use `kaios config templates` when you want to choose a different workflow template before setup.

Every core command also supports local help with examples and notes:

```bash
kaios
kaios quickstart --help
kaios setup --help
kaios gate --help
kaios verify --help
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

kaios quickstart
```

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
If your project has no `README.md`, omit `--context README.md`.

## Hosted Installer

The installer downloads the release ZIP, verifies the published SHA256 checksum, and links `kaios` under `$HOME/.kaios/bin`.

```bash
curl -fsSL https://morning-verlu.github.io/KAI/install.sh | sh
export PATH="$HOME/.kaios/bin:$PATH"
kaios quickstart
```

Set `KAIOS_INSTALL_DIR` to install somewhere else:

```bash
curl -fsSL https://morning-verlu.github.io/KAI/install.sh | KAIOS_INSTALL_DIR="$HOME/.local/kaios" sh
```

## Download ZIP

```bash
curl -L -o kaios-0.1.76.zip https://github.com/morning-verlu/KAI/releases/download/v0.1.76/kaios-0.1.76.zip
unzip kaios-0.1.76.zip
./kaios-0.1.76/bin/kaios quickstart
```

## Build From Source

```bash
git clone https://github.com/morning-verlu/KAI.git
cd KAI
./gradlew test installDist
build/install/kaios-cli/bin/kaios quickstart
```

Useful next commands after the first artifact:

```bash
kaios analyze . --format json --out artifacts/analysis.json --force
kaios quickstart
kaios doctor --json
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
