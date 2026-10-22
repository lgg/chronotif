// An illustrative model of Chronotif's panel, mirroring core/Attention.kt in the app:
// one "last check" watermark splits notifications into NEW (arrived after it) and EARLIER
// (folded into per-app stacks, ordered by their newest member). Ongoing items sit apart.
// Times are in minutes. All content is made up.

const APPS = {
  messages: { name: "Messages", color: "#30a14e", glyph: "M" },
  calendar: { name: "Calendar", color: "#e5484d", glyph: "17" },
  mail: { name: "Mail", color: "#3b82f6", glyph: "@" },
  weather: { name: "Weather", color: "#0e9fb5", glyph: "°" },
  podcasts: { name: "Podcasts", color: "#8e4ec6", glyph: "♪\uFE0E" },
};

const MAYA_MESSAGES = [
  "Found a table by the window.",
  "Ordered you a coffee too.",
  "Are you close?",
];

function initialState() {
  return {
    now: 600,
    lastCheck: 560,
    arrivals: 0,
    ongoing: [{ id: "pod", app: "podcasts", title: "Slow Mornings · Episode 42", body: "Playing" }],
    items: [
      { id: "maya", app: "messages", title: "Maya", body: "Running ten minutes late, save me a seat?", alert: 598 },
      { id: "review", app: "calendar", title: "Design review", body: "In 15 minutes · Room 4", alert: 594 },
      { id: "ship", app: "mail", title: "Order update", body: "Your package has shipped.", alert: 575 },
      { id: "rain", app: "weather", title: "Rain this evening", body: "Light rain from 6 PM.", alert: 520 },
      { id: "ana", app: "messages", title: "Ana", body: "See you tomorrow!", alert: 470 },
      { id: "climb", app: "messages", title: "Climbing group", body: "Leo: Thursday works for me", alert: 410 },
      { id: "digest", app: "mail", title: "Weekly digest", body: "Five things worth reading this week.", alert: 380 },
      { id: "receipt", app: "mail", title: "Receipt", body: "Thanks for your order.", alert: 300 },
    ],
  };
}

function sections(state) {
  const byRecent = [...state.items].sort((a, b) => b.alert - a.alert);
  const fresh = [];
  const stacks = new Map();
  for (const item of byRecent) {
    if (item.alert > state.lastCheck) fresh.push(item);
    else {
      if (!stacks.has(item.app)) stacks.set(item.app, []);
      stacks.get(item.app).push(item);
    }
  }
  return { fresh, earlier: [...stacks.values()] };
}

function ago(state, t) {
  const m = state.now - t;
  if (m < 1) return "now";
  if (m < 60) return `${m}m`;
  return `${Math.floor(m / 60)}h`;
}

const esc = (s) => s.replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[c]);

function card(state, item, extra = "") {
  const app = APPS[item.app];
  return `
    <div class="d-card ${extra}" style="view-transition-name: card-${item.id}">
      <span class="d-icon" style="background:${app.color}" aria-hidden="true">${app.glyph}</span>
      <div>
        <div class="d-row">
          <span class="d-title">${esc(item.title)}</span>
          <span class="d-time">${ago(state, item.alert)}</span>
        </div>
        <div class="d-body">${esc(item.body)}</div>
      </div>
    </div>`;
}

function render(state, list, fresh = new Set()) {
  const { fresh: newItems, earlier } = sections(state);
  let html = "";

  html += `<p class="d-head"><span>New<b>${newItems.length}</b></span></p>`;
  html += `<div class="d-list" role="list">`;
  html += newItems.length
    ? newItems.map((i) => `<div role="listitem">${card(state, i, fresh.has(i.id) ? "d-arrive" : "")}</div>`).join("")
    : `<div role="listitem" class="d-empty">Nothing new.</div>`;
  html += `</div>`;

  html += `<p class="d-head"><span>Ongoing</span></p><div class="d-list" role="list">`;
  html += state.ongoing
    .map((o) => {
      const app = APPS[o.app];
      return `<div role="listitem" class="d-card d-card--ongoing" style="view-transition-name: card-${o.id}">
        <span class="d-icon" style="background:${app.color}" aria-hidden="true">${app.glyph}</span>
        <div><div class="d-title">${esc(o.title)}</div><div class="d-progress" aria-hidden="true"><i></i></div></div>
      </div>`;
    })
    .join("");
  html += `</div>`;

  if (earlier.length) {
    html += `<p class="d-head"><span>Earlier</span><span class="d-clear" aria-hidden="true">Clear</span></p>`;
    html += `<div class="d-list" role="list">`;
    html += earlier
      .map((stack) => {
        const more = stack.length - 1;
        const label = more ? `<span class="visually-hidden">, and ${more} more from ${APPS[stack[0].app].name}</span>` : "";
        return `<div role="listitem" class="d-stack" data-count="${Math.min(stack.length, 3)}">${card(state, stack[0])}${label}</div>`;
      })
      .join("");
    html += `</div>`;
  }

  list.innerHTML = html;
}

export function initDemo(root) {
  const list = root.querySelector("#demo-list");
  const status = root.querySelector("#demo-status");
  if (!list) return;

  let state = initialState();
  const motionOK = () => !window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  const update = (mutate, message) => {
    const before = new Set(sections(state).fresh.map((i) => i.id));
    mutate();
    const arrived = new Set(sections(state).fresh.map((i) => i.id).filter((id) => !before.has(id)));
    const draw = () => render(state, list, motionOK() ? arrived : new Set());
    if (document.startViewTransition && motionOK()) document.startViewTransition(draw);
    else draw();
    status.textContent = message;
  };

  const actions = {
    check() {
      const moved = sections(state).fresh.length;
      update(
        () => {
          state.now += 1;
          state.lastCheck = state.now;
        },
        moved
          ? `Panel closed. ${moved} notification${moved === 1 ? "" : "s"} moved to Earlier.`
          : "Panel closed. Nothing was new.",
      );
    },
    arrive() {
      const maya = state.items.find((i) => i.id === "maya");
      const wasEarlier = maya.alert <= state.lastCheck;
      update(
        () => {
          state.now += 2;
          maya.body = MAYA_MESSAGES[state.arrivals++ % MAYA_MESSAGES.length];
          maya.alert = state.now;
        },
        wasEarlier
          ? "A new message from Maya. Her conversation moved back to New."
          : "A new message from Maya. Her conversation is at the top of New.",
      );
    },
    reset() {
      update(() => {
        state = initialState();
      }, "Illustration reset.");
    },
  };

  root.addEventListener("click", (e) => {
    const button = e.target.closest("[data-demo]");
    if (button) actions[button.dataset.demo]?.();
  });

  render(state, list);
}
