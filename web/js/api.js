// Thin wrapper around the Java backend REST API.

async function request(method, url, body) {
  let res;
  try {
    res = await fetch(url, {
      method,
      headers: body ? { "Content-Type": "application/json" } : undefined,
      body: body ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new Error("Cannot reach the JARVIS server. Is run.bat still running?");
  }
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    const err = new Error(data.error || `HTTP ${res.status}`);
    err.status = res.status;
    throw err;
  }
  return data;
}

export const api = {
  status: () => request("GET", "/api/status"),
  chat: (text, conversationId) => request("POST", "/api/chat", { text, conversationId }),
  approve: (approvalId, approved) => request("POST", "/api/agent/approve", { approvalId, approved }),
  cancel: () => request("POST", "/api/agent/cancel"),
  pendingApproval: () => request("GET", "/api/agent/pending"),
  setState: (state) => request("POST", "/api/state", { state }),
  conversations: () => request("GET", "/api/conversations"),
  messages: (id) => request("GET", `/api/conversations/messages?id=${encodeURIComponent(id)}`),
  deleteConversation: (id) => request("DELETE", `/api/conversations?id=${encodeURIComponent(id)}`),
  runs: (limit = 30) => request("GET", `/api/runs?limit=${limit}`),
  tools: () => request("GET", "/api/tools"),
  database: () => request("GET", "/api/database"),
  oop: () => request("GET", "/api/oop"),
  oopPolymorphism: () => request("POST", "/api/oop/polymorphism"),
  oopEncapsulation: () => request("POST", "/api/oop/encapsulation"),
  list: (kind) => request("GET", `/api/data/${kind}`),
  addNote: (content) => request("POST", "/api/data/notes", { content }),
  completeTask: (id) => request("POST", `/api/data/tasks/complete?id=${encodeURIComponent(id)}`),
  remove: (kind, id) => request("DELETE", `/api/data/${kind}?id=${encodeURIComponent(id)}`),
};

/** Live agent events over Server-Sent Events. Reconnects automatically. */
export function connectEvents(onEvent, onOpen, onError) {
  const es = new EventSource("/api/events");
  es.onmessage = (e) => {
    try {
      onEvent(JSON.parse(e.data));
    } catch (err) {
      console.error("Bad event", err);
    }
  };
  es.onopen = onOpen;
  es.onerror = onError;
  return es;
}
