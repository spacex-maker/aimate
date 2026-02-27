package com.openforge.aimate.memory;

/**
 * Classifies the nature of a long-term memory entry.
 *
 * EPISODIC    — "What happened": specific events, actions taken, results observed.
 *               Example: "In session abc, I used the search_web tool to find X and got Y."
 *
 * SEMANTIC    — "What I know": facts, rules, domain knowledge extracted from experience.
 *               Example: "The user prefers Python over Node.js for scripts."
 *
 * PROCEDURAL  — "How to do it": reusable strategies and workflows.
 *               Example: "To debug a Java OOM error, first check heap dump, then analyze GC logs."
 */
public enum MemoryType {
    EPISODIC,
    SEMANTIC,
    PROCEDURAL
}
