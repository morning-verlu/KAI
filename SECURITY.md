# Security Policy

KAI OS is an early runtime for agent execution. Treat every new tool integration as a security boundary.

## Supported Versions

Only the latest `main` branch is currently supported.

## Reporting Issues

Please open a private security advisory on GitHub when available, or create a minimal public issue that avoids sensitive details.

## Security Principles

- Tools must declare permissions.
- Agents should receive only the permissions they need.
- Filesystem, shell, network, and database tools should default to scoped access.
- Run snapshots should not contain secrets.
- Real model providers should avoid logging API keys or raw credentials.
- Provider errors must never include authorization headers or environment variable dumps.
