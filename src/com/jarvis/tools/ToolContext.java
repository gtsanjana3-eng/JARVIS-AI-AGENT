package com.jarvis.tools;

import com.jarvis.ai.LlmClient;
import com.jarvis.db.DataStore;
import com.jarvis.system.AppCatalog;
import com.jarvis.system.SystemMonitor;

import java.time.ZoneId;

/** Everything a tool may use, handed in by the agent instead of global variables. */
public record ToolContext(DataStore store, SystemMonitor monitor, Workspace workspace, ZoneId zone, AppCatalog apps,
                          LlmClient llm) {
}
