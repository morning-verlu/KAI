package ai.kaios.cli

import ai.kaios.StoredProcess
import ai.kaios.StoredRunSnapshot
import ai.kaios.StoredRuntimeEvent

class ProcessReportRenderer {
    fun render(snapshot: StoredRunSnapshot, allRuns: List<StoredRunSnapshot>): String {
        val totalTokens = snapshot.processes.sumOf { it.tokens }
        val totalMemory = snapshot.processes.sumOf { it.contextSize }
        val totalSyscalls = snapshot.processes.sumOf { it.syscallCount }
        val totalDuration = snapshot.processes.sumOf { it.durationMillis }
        val totalToolTime = snapshot.processes.sumOf { it.toolTimeMillis }
        val totalCost = snapshot.processes.sumOf { it.estimatedCostMicros }
        val recentRuns = allRuns.take(12)

        return """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>KAI OS Process Manager - ${escape(snapshot.runId)}</title>
              <style>
                :root {
                  color-scheme: light;
                  --bg: #f6f7f9;
                  --surface: #ffffff;
                  --surface-2: #eef2f6;
                  --text: #17202a;
                  --muted: #657386;
                  --border: #d9e0e8;
                  --accent: #0f766e;
                  --accent-2: #334155;
                  --ok: #15803d;
                  --warn: #b45309;
                  --bad: #b91c1c;
                  --shadow: 0 16px 40px rgba(15, 23, 42, 0.08);
                }
                * { box-sizing: border-box; }
                body {
                  margin: 0;
                  background: var(--bg);
                  color: var(--text);
                  font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
                  line-height: 1.45;
                }
                .shell {
                  min-height: 100vh;
                  display: grid;
                  grid-template-columns: 280px 1fr;
                }
                .sidebar {
                  border-right: 1px solid var(--border);
                  background: #111827;
                  color: #f8fafc;
                  padding: 24px 18px;
                }
                .brand {
                  display: flex;
                  align-items: center;
                  gap: 12px;
                  margin-bottom: 28px;
                }
                .mark {
                  width: 34px;
                  height: 34px;
                  display: grid;
                  place-items: center;
                  border: 1px solid rgba(255, 255, 255, 0.22);
                  border-radius: 8px;
                  font-weight: 800;
                  color: #5eead4;
                }
                .brand h1 {
                  margin: 0;
                  font-size: 16px;
                  line-height: 1.1;
                  letter-spacing: 0;
                }
                .brand p {
                  margin: 2px 0 0;
                  color: #a7b1c2;
                  font-size: 12px;
                }
                .side-title {
                  margin: 24px 0 10px;
                  color: #a7b1c2;
                  font-size: 11px;
                  font-weight: 700;
                  letter-spacing: 0.08em;
                  text-transform: uppercase;
                }
                .run-list {
                  display: grid;
                  gap: 8px;
                }
                .run-link {
                  display: block;
                  padding: 10px 11px;
                  border-radius: 8px;
                  border: 1px solid rgba(255, 255, 255, 0.09);
                  color: #e5e7eb;
                  text-decoration: none;
                  background: rgba(255, 255, 255, 0.03);
                }
                .run-link.current {
                  border-color: rgba(94, 234, 212, 0.55);
                  background: rgba(15, 118, 110, 0.28);
                }
                .run-link strong {
                  display: block;
                  font-size: 13px;
                  font-weight: 700;
                }
                .run-link span {
                  display: block;
                  margin-top: 2px;
                  color: #a7b1c2;
                  font-size: 11px;
                  white-space: nowrap;
                  overflow: hidden;
                  text-overflow: ellipsis;
                }
                .main {
                  padding: 28px;
                }
                .hero {
                  display: flex;
                  align-items: flex-start;
                  justify-content: space-between;
                  gap: 24px;
                  margin-bottom: 22px;
                }
                .hero h2 {
                  margin: 0;
                  font-size: 30px;
                  line-height: 1.1;
                  letter-spacing: 0;
                }
                .hero p {
                  max-width: 880px;
                  margin: 10px 0 0;
                  color: var(--muted);
                  font-size: 14px;
                }
                .status {
                  min-width: 130px;
                  padding: 10px 12px;
                  border: 1px solid var(--border);
                  border-radius: 8px;
                  background: var(--surface);
                  color: var(--ok);
                  font-size: 13px;
                  font-weight: 800;
                  text-align: center;
                  box-shadow: var(--shadow);
                }
                .status.failed { color: var(--bad); }
                .metrics {
                  display: grid;
                  grid-template-columns: repeat(4, minmax(0, 1fr));
                  gap: 12px;
                  margin-bottom: 18px;
                }
                .metric {
                  padding: 15px;
                  border: 1px solid var(--border);
                  border-radius: 8px;
                  background: var(--surface);
                  box-shadow: var(--shadow);
                }
                .metric span {
                  color: var(--muted);
                  font-size: 12px;
                  font-weight: 700;
                  text-transform: uppercase;
                  letter-spacing: 0.06em;
                }
                .metric strong {
                  display: block;
                  margin-top: 6px;
                  font-size: 26px;
                  line-height: 1;
                }
                .grid {
                  display: grid;
                  grid-template-columns: minmax(0, 1.35fr) minmax(320px, 0.65fr);
                  gap: 18px;
                  align-items: start;
                }
                .panel {
                  border: 1px solid var(--border);
                  border-radius: 8px;
                  background: var(--surface);
                  box-shadow: var(--shadow);
                  overflow: hidden;
                }
                .panel-head {
                  display: flex;
                  align-items: center;
                  justify-content: space-between;
                  gap: 12px;
                  padding: 15px 16px;
                  border-bottom: 1px solid var(--border);
                  background: #fbfcfd;
                }
                .panel-head h3 {
                  margin: 0;
                  font-size: 15px;
                  letter-spacing: 0;
                }
                .panel-head span {
                  color: var(--muted);
                  font-size: 12px;
                }
                table {
                  width: 100%;
                  border-collapse: collapse;
                  font-size: 13px;
                }
                th, td {
                  padding: 12px 14px;
                  border-bottom: 1px solid var(--border);
                  text-align: left;
                  vertical-align: middle;
                }
                th {
                  color: var(--muted);
                  font-size: 11px;
                  font-weight: 800;
                  text-transform: uppercase;
                  letter-spacing: 0.07em;
                  background: #fbfcfd;
                }
                tr:last-child td { border-bottom: 0; }
                .pid {
                  width: 36px;
                  height: 26px;
                  display: inline-grid;
                  place-items: center;
                  border-radius: 7px;
                  background: var(--surface-2);
                  font-weight: 800;
                }
                .state {
                  display: inline-flex;
                  align-items: center;
                  min-height: 24px;
                  padding: 3px 8px;
                  border-radius: 999px;
                  background: #dcfce7;
                  color: var(--ok);
                  font-size: 11px;
                  font-weight: 800;
                }
                .state.failed { background: #fee2e2; color: var(--bad); }
                .state.running { background: #fef3c7; color: var(--warn); }
                .graph {
                  display: grid;
                  gap: 12px;
                  padding: 16px;
                }
                .graph-node {
                  display: grid;
                  grid-template-columns: 38px 1fr auto;
                  gap: 12px;
                  align-items: center;
                  padding: 12px;
                  border: 1px solid var(--border);
                  border-radius: 8px;
                  background: #fbfcfd;
                }
                .node-index {
                  width: 32px;
                  height: 32px;
                  display: grid;
                  place-items: center;
                  border-radius: 8px;
                  background: #d1fae5;
                  color: #065f46;
                  font-weight: 900;
                }
                .node-name strong {
                  display: block;
                  font-size: 14px;
                }
                .node-name span {
                  color: var(--muted);
                  font-size: 12px;
                }
                .connector {
                  width: 2px;
                  height: 18px;
                  margin-left: 31px;
                  background: var(--border);
                }
                .timeline {
                  max-height: 540px;
                  overflow: auto;
                  padding: 6px 0;
                }
                .event {
                  display: grid;
                  grid-template-columns: 120px 1fr;
                  gap: 12px;
                  padding: 10px 16px;
                  border-bottom: 1px solid var(--border);
                  font-size: 12px;
                }
                .event:last-child { border-bottom: 0; }
                .event time {
                  color: var(--muted);
                  font-variant-numeric: tabular-nums;
                }
                .event strong {
                  color: var(--accent-2);
                }
                .output {
                  padding: 16px;
                  white-space: pre-wrap;
                  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
                  font-size: 12px;
                  color: #243142;
                  background: #fbfcfd;
                }
                @media (max-width: 980px) {
                  .shell { grid-template-columns: 1fr; }
                  .sidebar { display: none; }
                  .main { padding: 18px; }
                  .hero { display: block; }
                  .status { margin-top: 14px; }
                  .metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); }
                  .grid { grid-template-columns: 1fr; }
                }
                @media (max-width: 620px) {
                  .metrics { grid-template-columns: 1fr; }
                  .hero h2 { font-size: 24px; }
                  table { min-width: 720px; }
                  .table-scroll { overflow-x: auto; }
                }
              </style>
            </head>
            <body>
              <div class="shell">
                <aside class="sidebar">
                  <div class="brand">
                    <div class="mark">K</div>
                    <div>
                      <h1>KAI OS</h1>
                      <p>Agent Process Manager</p>
                    </div>
                  </div>
                  <div class="side-title">Recent runs</div>
                  <nav class="run-list">
                    ${recentRuns.joinToString("\n") { runLink(it, snapshot.runId) }}
                  </nav>
                </aside>
                <main class="main">
                  <section class="hero">
                    <div>
                      <h2>${escape(snapshot.runId)}</h2>
                      <p>${escape(snapshot.workflowName)} workflow for "${escape(snapshot.task)}". Agents are shown as OS-style processes with lifecycle, context, token, and syscall telemetry.</p>
                    </div>
                    <div class="${if (snapshot.success) "status" else "status failed"}">${if (snapshot.success) "SUCCEEDED" else "FAILED"}</div>
                  </section>
                  <section class="metrics">
                    ${metric("Processes", snapshot.processes.size.toString())}
                    ${metric("Tokens", totalTokens.toString())}
                    ${metric("Memory", "${totalMemory}b")}
                    ${metric("Syscalls", totalSyscalls.toString())}
                    ${metric("Tool Time", "${totalToolTime}ms")}
                    ${metric("Cost", formatCost(totalCost))}
                  </section>
                  <section class="grid">
                    <div class="panel">
                      <div class="panel-head">
                        <h3>Process Table</h3>
                        <span>${totalDuration}ms total duration</span>
                      </div>
                      <div class="table-scroll">
                        <table>
                          <thead>
                            <tr>
                              <th>PID</th>
                              <th>Agent</th>
                              <th>State</th>
                              <th>Tokens</th>
                              <th>Memory</th>
                              <th>Syscalls</th>
                              <th>Tool ms</th>
                              <th>Cost</th>
                              <th>Duration</th>
                            </tr>
                          </thead>
                          <tbody>
                            ${snapshot.processes.joinToString("\n") { processRow(it) }}
                          </tbody>
                        </table>
                      </div>
                    </div>
                    <div class="panel">
                      <div class="panel-head">
                        <h3>Workflow Graph</h3>
                        <span>${snapshot.processes.size} nodes</span>
                      </div>
                      <div class="graph">
                        ${workflowGraph(snapshot.processes)}
                      </div>
                    </div>
                    <div class="panel">
                      <div class="panel-head">
                        <h3>Lifecycle Events</h3>
                        <span>${snapshot.events.size} events</span>
                      </div>
                      <div class="timeline">
                        ${snapshot.events.joinToString("\n") { eventRow(it) }}
                      </div>
                    </div>
                    <div class="panel">
                      <div class="panel-head">
                        <h3>Final Output</h3>
                        <span>agent result</span>
                      </div>
                      <div class="output">${escape(snapshot.finalOutput)}</div>
                    </div>
                  </section>
                </main>
              </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun runLink(run: StoredRunSnapshot, currentRunId: String): String {
        val current = if (run.runId == currentRunId) " current" else ""
        val status = if (run.success) "succeeded" else "failed"
        return """<a class="run-link$current" href="${escape(run.runId)}.html"><strong>${escape(run.runId)}</strong><span>${escape(status)} - ${escape(run.task)}</span></a>"""
    }

    private fun metric(label: String, value: String): String =
        """<div class="metric"><span>${escape(label)}</span><strong>${escape(value)}</strong></div>"""

    private fun processRow(process: StoredProcess): String {
        val stateClass = when (process.state.lowercase()) {
            "failed", "cancelled" -> "state failed"
            "running", "suspended", "created" -> "state running"
            else -> "state"
        }
        return """
            <tr>
              <td><span class="pid">${process.pid}</span></td>
              <td><strong>${escape(process.agent)}</strong></td>
              <td><span class="$stateClass">${escape(process.state)}</span></td>
              <td>${process.tokens} <span style="color: var(--muted)">(${process.inputTokens}/${process.outputTokens})</span></td>
              <td>${process.contextSize}b</td>
              <td>${process.syscallCount}</td>
              <td>${process.toolTimeMillis}</td>
              <td>${formatCost(process.estimatedCostMicros)}</td>
              <td>${process.durationMillis}ms</td>
            </tr>
        """.trimIndent()
    }

    private fun formatCost(micros: Long): String =
        if (micros == 0L) "0" else "${micros}um"

    private fun workflowGraph(processes: List<StoredProcess>): String =
        processes.mapIndexed { index, process ->
            val connector = if (index < processes.lastIndex) """<div class="connector"></div>""" else ""
            """
                <div class="graph-node">
                  <div class="node-index">${index + 1}</div>
                  <div class="node-name"><strong>${escape(process.agent)}</strong><span>pid ${process.pid} - ${escape(process.state.lowercase())}</span></div>
                  <div class="state">${process.syscallCount} syscalls</div>
                </div>
                $connector
            """.trimIndent()
        }.joinToString("\n")

    private fun eventRow(event: StoredRuntimeEvent): String =
        """
            <div class="event">
              <time>${escape(event.timestamp.substringAfter('T').take(12))}</time>
              <div><strong>pid=${event.pid} ${escape(event.agent)} ${escape(event.type)}</strong><br>${escape(event.message)}</div>
            </div>
        """.trimIndent()

    private fun escape(value: String): String =
        buildString(value.length) {
            value.forEach { char ->
                when (char) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&#39;")
                    else -> append(char)
                }
            }
        }
}
