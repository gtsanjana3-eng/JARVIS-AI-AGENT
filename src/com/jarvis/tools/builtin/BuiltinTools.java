package com.jarvis.tools.builtin;

import com.jarvis.tools.ToolRegistry;

/** Registers every built-in tool. Add a new capability by adding one line here. */
public final class BuiltinTools {

    private BuiltinTools() {
    }

    public static ToolRegistry createRegistry() {
        return createRegistry(new com.jarvis.search.WikipediaSearch());
    }

    /** @param search the web search engine chosen in the configuration (Google, Brave or Wikipedia) */
    public static ToolRegistry createRegistry(com.jarvis.search.SearchProvider search) {
        return new ToolRegistry()
                .register(new UtilityTools.Calculator())
                .register(new UtilityTools.DateTime())
                .register(new UtilityTools.SystemStatus())
                .register(new NoteTools.SaveNote())
                .register(new NoteTools.SearchNotes())
                .register(new NoteTools.DeleteNote())
                .register(new MemoryTools.Remember())
                .register(new MemoryTools.Recall())
                .register(new MemoryTools.Forget())
                .register(new ReminderTools.CreateReminder())
                .register(new ReminderTools.ListReminders())
                .register(new ReminderTools.CancelReminder())
                .register(new TaskTools.AddTask())
                .register(new TaskTools.ListTasks())
                .register(new TaskTools.CompleteTask())
                .register(new TaskTools.DeleteTask())
                .register(new StudyPlanTool())
                .register(new FileTools.ListFiles())
                .register(new FileTools.ReadFile())
                .register(new FileTools.WriteDocument())
                .register(new FileTools.CreateFolder())
                .register(new FileTools.MoveFile())
                .register(new FileTools.DeleteFile())
                .register(new FileTools.OrganizeFolder())
                .register(new ResearchTools.SearchWikipedia())
                .register(new ResearchTools.ReadWikipedia())
                .register(new DesktopTools.OpenApplication())
                .register(new DesktopTools.ListApplications())
                .register(new DesktopTools.AppAction())
                .register(new DesktopTools.OpenFile())
                .register(new DesktopTools.OpenWebsite())
                .register(new DesktopTools.Screenshot())
                .register(new DesktopTools.LockScreen())
                .register(new ReportTools.CreateReport())
                .register(new ReportTools.WriteWithAi())
                .register(new InfoTools.Weather())
                .register(new InfoTools.News())
                .register(new InfoTools.Cricket())
                .register(new AutostartTool())
                .register(new WebTools.WebSearch(search))
                .register(new WebTools.ReadWebpage())
                .register(new WebTools.AnswerFromSources());
    }
}
