package dev.solaris.agent.javaagent;

/** One combat telemetry line: either a single attack or an engagement summary. */
record ScenarioCombatRecord(String line, boolean attack, boolean hitConfirmed) {
}
