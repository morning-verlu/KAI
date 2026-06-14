# Install KAI OS

KAI OS is distributed as a JVM CLI. Java 17+ is required.

Check the installed version at any time:

```bash
kaios --version
```

Running `kaios` with no arguments prints the quick start and exits successfully, so it is safe to use as a first smoke test.
Mistyped commands show a suggestion when there is a clear match, such as `kaios analyse` pointing to `kaios analyze`.
If a run id is missing, `kaios ps`, `kaios inspect`, `kaios trace`, `kaios capsule`, `kaios report`, and `kaios export` point back to `kaios runs`; when no snapshots exist yet, the CLI points back to `kaios demo`, `kaios setup --ci`, and `kaios verify`.
If `kaios.json` is missing, `kaios config show` and `kaios config validate` point back to `kaios init` and `kaios config templates`.

Every core command also supports local help with examples and notes:

```bash
kaios
kaios setup --help
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

kaios demo
kaios setup --ci
kaios verify
```

After `kaios verify` reports `status: ready`, create an artifact with `kaios run --index . --context README.md --out artifacts/project.md --trace-out artifacts/trace.json --force "summarize this project"`.
If your project has no `README.md`, omit `--context README.md`.

## Hosted Installer

The installer downloads the release ZIP, verifies the published SHA256 checksum, and links `kaios` under `$HOME/.kaios/bin`.

```bash
curl -fsSL https://morning-verlu.github.io/KAI/install.sh | sh
export PATH="$HOME/.kaios/bin:$PATH"
kaios demo
kaios setup --ci
kaios verify
```

Set `KAIOS_INSTALL_DIR` to install somewhere else:

```bash
curl -fsSL https://morning-verlu.github.io/KAI/install.sh | KAIOS_INSTALL_DIR="$HOME/.local/kaios" sh
```

## Download ZIP

```bash
curl -L -o kaios-0.1.52.zip https://github.com/morning-verlu/KAI/releases/download/v0.1.52/kaios-0.1.52.zip
unzip kaios-0.1.52.zip
./kaios-0.1.52/bin/kaios demo
./kaios-0.1.52/bin/kaios setup --ci
./kaios-0.1.52/bin/kaios verify
```

## Build From Source

```bash
git clone https://github.com/morning-verlu/KAI.git
cd KAI
./gradlew test installDist
build/install/kaios-cli/bin/kaios demo
build/install/kaios-cli/bin/kaios setup --ci
build/install/kaios-cli/bin/kaios verify
```

Useful next commands after the first artifact:

```bash
kaios analyze . --format json --out artifacts/analysis.json --force
kaios doctor --json
kaios setup --ci
kaios verify
kaios config show
kaios config validate --json
kaios run --index . --trace-out artifacts/trace.json --force "summarize this project"
kaios runs --json
kaios ps latest
kaios inspect latest
kaios trace latest
kaios trace latest --json
kaios trace latest --json --out artifacts/trace.json --force
kaios capsule latest
kaios capsule latest --check
kaios capsule latest --out artifacts/run.capsule.json --force
kaios capsule --file artifacts/run.capsule.json --check
kaios bug-report --out artifacts/kaios-bug-report.md --force
```
