# J.A.R.V.I.S. — AI Personal Assistant & Autonomous Agent (Java OOP Mini-Project)

A conversational, voice-enabled personal assistant that **understands requests, plans steps, chooses tools,
executes them, observes the results, asks for approval before risky actions, and reports honestly**.

- **Backend:** Java only (no frameworks). Built-in `HttpServer`, `HttpClient`, JDBC.
- **Database:** SQLite (`data/jarvis.sqlite`) through the `sqlite-jdbc` driver in `lib/`.
- **AI engine:** Ollama — a free, local language model running on this PC. No API key, nothing sent to the cloud.
- **Frontend:** HTML, CSS and JavaScript in `web/`, Iron-Man-style holographic interface.

---

## 1. Set up the free AI engine (one time)

1. Download and install **Ollama** from <https://ollama.com/download>.
2. Open a terminal (Command Prompt) and download the model (≈ 2 GB):
   ```
   ollama pull qwen2.5:3b
   ```
3. Leave Ollama running (it sits in the system tray).

Without Ollama, JARVIS still runs in a clearly-labelled **limited offline mode** (time, date, calculations, notes),
and shows setup steps on the **System** screen. It connects automatically as soon as Ollama is ready.

To use a different model (e.g. `qwen2.5:7b` for better answers on a faster PC), edit `ollama.model` in
`config/jarvis.properties` and restart.

## 2. Run

Double-click **`run.bat`**. It compiles every Java file, starts the server and opens <http://localhost:8080>.
Use **Chrome or Edge** for voice. Keep the black window open; press Ctrl+C there to stop.

## 3. What you can say

| Ability | Example |
|---|---|
| Conversation with follow-ups | “I have an exam next week.” → JARVIS asks which subjects and when |
| Study planning | “Maths and Physics on 12 November, Physics is my weak subject.” |
| Long-term memory (you approve) | “Remember that I prefer studying in the morning.” |
| Notes | “Take a note: OOP has four pillars…” · “What notes do I have about Java?” |
| Reminders (fire on time) | “Remind me in 20 minutes to drink water.” |
| To-do list | “Add a high-priority task to finish the report by Friday.” |
| Research → document → app | “Research black holes and put it in Word.” · “Research the Taj Mahal in Notepad.” |
| Writing in an app | “Write a leave letter for tomorrow in Word.” · “Write a short essay on climate change in Notepad.” |
| Live info | “What’s the weather in Bengaluru?” · “Latest news about cricket.” |
| Computer | “Take a screenshot.” · “Lock my computer.” |
| Documents & files | “Write a summary of recursion to notes/recursion.md.” · “Organise my workspace.” |
| Calculations | “What is 18% of 2450 plus 300?” |
| Programming help | “Explain polymorphism with a Java example.” |
| Open any installed app (you approve) | “Open WhatsApp.” · “Launch Word.” · “Open VS Code.” |
| Tasks in other apps (you approve) | “Play Arijit Singh on YouTube.” · “WhatsApp ‘I’m on my way’.” · “Email my teacher about the project.” · “Directions to the library.” · “Open Bluetooth settings.” · “Write a leave letter in Notepad.” |

### The interface

- **Core** (first page) — voice only: the holographic core, live subtitles of what you said and what JARVIS
  answers, and one mic. Tap the core (or press Space) and talk. The first tap of a session JARVIS greets you.
  **Hands-free** (on by default) keeps listening after every reply; say “stop listening” to pause.
  Talk or press Esc while JARVIS speaks to **interrupt** it. Press **Type** (or `/`) to type instead.
- **Chat** — the full conversation history with formatted answers (tables, code).
- **Activity** — the live agent pipeline, plan and tool calls, plus the execution history.
- **Memory**, **Tools**, **System** — stored data, the tool registry, AI engine and database status.

When JARVIS needs approval it asks out loud — say **“yes”** or **“no”**, or use the buttons.

### Apps

JARVIS indexes every app in your Start menu, including Microsoft Store apps like WhatsApp, so it can open any
installed app by name and **brings its window in front of JARVIS**. It does tasks inside apps through their own
links (YouTube/Google/Spotify search, Maps directions, email and WhatsApp drafts, Windows Settings pages) and by
writing documents and opening them in Notepad or Word. It cannot click buttons inside other apps, and it never
sends messages or emails itself: it prepares them and **you press Send**.

## 4. How the agent works

Every request goes through three layers, fastest first:

```
 request ─► 1. FastRouter (Java) ── clear command? ──► one tool call            ~1 s   "open WhatsApp", "weather in Pune"
            │                     └─ known task?   ──► Workflow (multi-step)    ~4 s   "research black holes and put it in Word"
            └► 2. ToolSelector (Java) picks only the relevant tools (none for questions)
               3. LLM agent loop: decide → call tools → observe results → answer   ~5–20 s
                                   └─► AWAITING_APPROVAL (risky tools pause here)
```

- **Workflows** (`agent/workflow/`): `Workflow` is an abstract class; `ResearchWorkflow` (search Wikipedia →
  read the article → write a structured report → open it in Word/Notepad/browser/VS Code → spoken summary) and
  `WriteWorkflow` (AI writes a letter/essay/notes → saved as a document → opened in the app you named) are
  subclasses. Each step runs through the normal tool system, so it is validated, approved and recorded.
- **Speed**: the system prompt never changes (the clock goes into the message), so Ollama reuses its cached
  work; JARVIS pre-warms the model at startup; knowledge questions get no tools and answer in about 5 seconds.
- **Honesty**: tool success is decided by Java code. Any reply that claims an action when no tool succeeded is
  blocked, and saying "no" to an approval ends with a fixed, accurate reply.

Every state change, plan, tool call and result is **saved to SQLite** and **streamed live** to the browser
(Server-Sent Events), so the Activity page shows what really happens — nothing is animated for show.

### Safety

| Risk level | Approval | Tools |
|---|---|---|
| SAFE | no | calculator, notes, tasks, reminders, study plan, read/list/create files, list_applications |
| NETWORK_READ | no | Wikipedia, weather (Open-Meteo), news (Google News RSS) — only the search words are sent |
| SENSITIVE | **yes** | remember_fact, move_file, organize_folder, cancel_reminder |
| EXTERNAL | **yes** | open_application (any installed app), app_action, open_file, open_website |
| DESTRUCTIVE | **yes** | delete_note/task/file, forget_memory, overwriting a document |

File tools can only touch the `workspace/` folder; deleted files go to `workspace/.trash`.

## 5. OOP concepts demonstrated

**See it live:** open the **OOP** page in the app (sidebar). Each of the four ideas is explained in plain
language with a picture of how JARVIS itself uses it. No code is shown. The numbers, family trees and two test buttons are live, read from the running
program with Java reflection (`src/com/jarvis/oop/OopInspector.java`). **Polymorphism** sends the same command
to three different tools. **Encapsulation** tries to misuse a tool four ways and shows every attempt being blocked.

| Concept | Where |
|---|---|
| **Abstraction** | `Tool`, `Skill`, `Entity`, `SqliteRepository` (abstract classes); `LlmClient`, `Repository<T>` (interfaces) |
| **Inheritance** | 42 tools `extends Tool`; `AnswerWorkflow`, `ResearchWorkflow`, `WriteWorkflow` `extends Workflow`; `GoogleSearch`, `BraveSearch`, `WikipediaSearch` `extends HttpSearch`; 9 models `extends Entity`; 9 repositories `extends SqliteRepository` |
| **Polymorphism** | `Agent` calls `tool.run()` / `tool.validate()` on any `Tool`; `OllamaClient` is used only as an `LlmClient` |
| **Encapsulation** | private fields + controlled mutators (`Reminder.markFired()`, `AgentRun.finish()`); synchronized `StateManager` |
| **Template Method** | `Tool.run()` validates and catches errors, subclasses implement `execute()`; `SqliteRepository` writes SQL once |
| **Composition** | `Agent` has an `LlmClient`, a `ToolRegistry`, a `DataStore`; `DataStore` has nine repositories |
| **Enums & records** | `AssistantState`, `RiskLevel`; `Plan`, `ToolCall`, `ChatMessage` |

## 6. Project structure

```
src/com/jarvis/
  Main.java              wiring + web server
  agent/                 Agent (the loop), FastRouter, ToolSelector, Plan, AgentPrompts, OfflineEngine, ReminderService
  ai/                    LlmClient (interface), OllamaClient, ChatMessage, ToolCall, LlmReply, ...
  tools/                 Tool (abstract), ToolRegistry, RiskLevel, ToolParam, Workspace, ...
  tools/builtin/         42 tools: notes, memory, reminders, tasks, files, reports, research, weather, news, apps, autostart
  agent/workflow/        Workflow (abstract), ResearchWorkflow, WriteWorkflow, ResearchRequest, WorkflowState, AppTarget
  db/                    Database, SqliteRepository (abstract), 9 repositories, DataStore, LegacyImporter
  model/                 Entity (abstract) + Conversation, Message, Note, Memory, Reminder, TaskItem,
                         AgentRun, AgentStep, Approval
  core/                  AssistantState, StateManager, EventBus
  server/                ApiHandler (REST), EventStreamHandler (live events), StaticFileHandler
  skills/                original rule-based skills, now the offline fallback
  config/ system/ util/  Config, SystemMonitor, AppCatalog, WindowFocus, DesktopNotifier (tray), Json, DocumentWriter, MathEvaluator, TimeParser, SpokenTime
web/                     index.html, css/style.css, js/ (app, activity, views, voice, reactor, md, api, ...)
lib/                     sqlite-jdbc-3.47.1.0.jar
config/jarvis.properties settings (created on first run)
data/jarvis.sqlite       the database (old data/*.db files are imported once and kept as backup)
workspace/               the only folder file tools may use
```

## 7. Database tables

`conversations`, `messages`, `notes`, `memories`, `reminders`, `tasks`, `agent_runs`, `agent_steps`, `approvals`
— open `data/jarvis.sqlite` with any SQLite viewer (e.g. DB Browser for SQLite) to show your teacher.

## 8. Accurate answers from real sources

Factual questions ("who won…", "what is the population of…", "when did…") are **never answered from the AI's
memory**. `AnswerWorkflow` searches the web, reads the top pages, picks the sentences that answer the question
(rare question words weigh most), and the AI writes the answer **only from those sentences**, listing the sources.
Cricket comes from **ESPNcricinfo live scores** plus Google News; news from **Google News**, newest first, with ages.

**Search engine** (`config/jarvis.properties`, added automatically):

| `search.provider` | Needs | Notes |
|---|---|---|
| `wikipedia` (default) | nothing | Accurate for facts, people, places, history, science |
| `google` | `google.apiKey` + `google.cx` | Google Programmable Search API — create both at programmablesearchengine.google.com and console.cloud.google.com |
| `brave` | `brave.apiKey` | Brave Search API — brave.com/search/api |

`SearchProvider` is an interface; `GoogleSearch`, `BraveSearch` and `WikipediaSearch` extend the abstract
`HttpSearch` — swapping the engine is one line of configuration.
