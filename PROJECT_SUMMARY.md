# deBS — Project Summary

A ClojureScript multi-target application for analyzing and refuting collectivist
content (focused on X / Twitter posts) from a libertarian perspective
(NAP, self-ownership, Rothbard/Hoppe-aligned reasoning). It ships as **three
deployed targets** from one source tree:

1. **Chrome MV3 extension** — injects a ✍️ "Reply Helper" button on X/Twitter
   tweets, opens a side panel with the tweet card and an LLM-generated
   refutation, plus a popup.
2. **PWA** — standalone web app where the user pastes a tweet URL and gets a
   refutation back. The PWA is now the primary client for the in-repo server.
3. **Node.js HTTP server** — caches tweets in SQLite, proxies calls to
   `twitterapi.io`, and forwards prompts to **OpenRouter** for generation.

## Repository layout

```
debs/
├── shadow-cljs.edn         # build config (server + 4 ext + pwa + test)
├── package.json            # shadow-cljs, react, better-sqlite3
├── README.md               # dev workflow
├── TODO.md                 # outstanding features + known bugs
├── .env                    # TWITTER_API_KEY, OPENROUTER_*, DEBS_API_ALLOWED_DOMAINS
├── src/debs/
│   ├── ext/                # Chrome extension targets
│   │   ├── content_script.cljs         + content_script/message_handlers.cljs
│   │   ├── content_helpers.cljs        # DOM injection of ✍️ button on tweets
│   │   ├── messaging.cljs              # chrome.runtime message bus wrapper
│   │   ├── service_worker.cljs         + service_worker/{message_handlers,responders}.cljs
│   │   ├── side_panel.cljs             + side_panel/{ui,message_handlers}.cljs
│   │   └── popup.cljs
│   ├── pwa/                # PWA target
│   │   ├── main.cljs                   # re-frame boot, dev hot-reload hooks
│   │   ├── ui.cljs                     # root view
│   │   └── ui/{events,subscriptions}.cljs
│   ├── server/             # Node.js HTTP server
│   │   ├── main.cljs                   # entry: init-db! + http.createServer (~17 lines)
│   │   ├── handlers.cljs               # route table + linear-pipeline handlers
│   │   ├── http.cljs                   # CORS, JSON, body reader, fetch-json (retry/backoff)
│   │   ├── db.cljs                     # better-sqlite3 wrapper (tweets table)
│   │   ├── openrouter.cljs             # OpenRouter chat-completion client
│   │   ├── twitter.cljs                # twitterapi.io tweet lookup
│   │   └── macros.clj                  # inline-resource macro (reads classpath files at compile time)
│   └── shared/             # code used by multiple targets
│       ├── validations.cljs            # tweet-id regex
│       ├── x_adapters.cljs             # oEmbed-based tweet text extraction
│       └── ui/{components,browser_helpers}.cljs
├── resources/              # classpath assets inlined at compile time
│   └── prompts/
│       └── system.md                   # 12 KB libertarian system prompt (Rothbard/Hoppe)
├── ext/                    # static assets loaded by the Chrome extension
│   ├── manifest.json                   # MV3 manifest (permissions, sw, side panel)
│   ├── popup.html, side-panel.html
│   ├── js/initial-content-script.js    # thin ESM that imports the real bundle
│   ├── css/{content,side_panel}.css
│   ├── icons/                          # 16/32/48/128 png
│   └── vendor/                         # bulma.css, datastar.js
├── pwa/                    # static assets for the PWA
│   ├── index.html
│   ├── manifest.json                   # PWA manifest with share_target
│   ├── css/styles.css
│   └── icons/
├── public/test/            # browser-test target output (see :test build)
├── test/
│   ├── fixtures/                       # cached twitterapi.io + syndication + qwen3.7 JSON
│   └── debs/shared/                    # cljs test namespaces live here (empty)
├── server/debs-server.js   # shadow-cljs :simple build output (gitignored upstream)
└── debs.db                 # sqlite cache (gitignored upstream)
```

## Key files & their roles

| Path | Role |
|---|---|
| `shadow-cljs.edn` | 7 builds: `:server`, `:content-script`, `:service-worker`, `:side-panel`, `:popup`, `:pwa`, plus `:test`. `:source-paths` includes `["dev" "src" "resources" "test"]` (so `resources/prompts/system.md` is loadable as a classpath resource). `:nrepl {:port 3333}`. `:dev-http {8000 "pwa" 8001 "public/test"}`. |
| `package.json` | Dev: `shadow-cljs ^3.4.11`. Runtime: `react`/`react-dom 19.2.0`, `better-sqlite3 ^12.10.1` (moved from devDependencies — needed at runtime by the Node server). |
| `src/debs/ext/content_script.cljs` | Boot the content script. Inits the DOM `MutationObserver` and starts the message listener. |
| `src/debs/ext/content_helpers.cljs` | `tweet-selector`, `inject-reply-button`, `init-reply-helper!` — the actual X/Twitter DOM scraping + ✍️ button injection. Defines `get-tweet-text` and `get-tweet-data`. |
| `src/debs/ext/messaging.cljs` | Thin wrapper over `chrome.runtime.onMessage` / `sendMessage` with `js->clj` coercion and keyword `:type` normalization. |
| `src/debs/ext/service_worker.cljs` | MV3 background. Configures the side panel per origin (enabled only on `x.com` + `http://localhost:8000`), wires tab-update listener, listens for messages. |
| `src/debs/ext/service_worker/message_handlers.cljs` | `prepare-prompt` builds the Rothbard/Hoppe system prompt; `:select-post` triggers generation. |
| `src/debs/ext/service_worker/responders.cljs` | `generate-response` POSTs to `http://localhost:9621/query` (a separate responder gateway, not the in-repo server). |
| `src/debs/ext/side_panel.cljs` | Reagent root + `start-or-reload!` / `stop!` hooks. |
| `src/debs/ext/side_panel/ui.cljs` | Renders last-selected post + saved-posts list using Bulma cards. |
| `src/debs/ext/side_panel/message_handlers.cljs` | `handle-select-post` / `handle-answer-ready` / `handle-answer-error` mutate the local Reagent atom. |
| `src/debs/server/main.cljs` | Tiny entry point: reads `DEBS_DB_PATH` (default `debs.db`), calls `db/init-db!`, then `http.createServer handlers/router` on port 3000. All real logic lives in sibling namespaces. |
| `src/debs/server/handlers.cljs` | Route table `[[:options :any …] [:get "/health" …] [:get "/tweet-info" …] [:post "/generator" …]]` + `match-route` / `router` dispatch. Each handler is a linear pipeline: read input → call API client → write JSON. `build-prompt` joins `instructions` + `<tweet>…</tweet>`. |
| `src/debs/server/http.cljs` | CORS headers from `DEBS_API_ALLOWED_DOMAINS`, `send-json`, `send-error`, `read-body` (Promise wrapper), and `fetch-json` (Promise wrapper around Node 18+ native `fetch` with abort-based timeout + exponential-backoff retry on `ECONNRESET`/`ETIMEDOUT`/5xx). Replaced the old `https-call` Node-callback wrapper. |
| `src/debs/server/db.cljs` | `better-sqlite3` connection held in a `defonce db` atom; `init-db!` creates the single `tweets (id TEXT PK, data TEXT)` table; `get-tweet` / `save-tweet!` are no-ops when `@db` is nil (lets tests skip init). |
| `src/debs/server/openrouter.cljs` | OpenRouter chat completion. Env: `OPENROUTER_API_KEY`, `OPENROUTER_MODEL` (default `nvidia/nemotron-3-ultra-550b-a55b:free`). Headers: `Authorization: Bearer …`, `HTTP-Referer: http://localhost:3000`, `X-OpenRouter-Title: Debs`. **System prompt is loaded at compile time** from `resources/prompts/system.md` via the `inline-resource` macro. |
| `src/debs/server/twitter.cljs` | `twitterapi.io` `GET /twitter/tweets?tweet_ids=…` with `X-API-Key: TWITTER_API_KEY`. Uses `http/fetch-json` with `:max-retries 2` and `:timeout 10000` (upstream drops keep-alive sockets under load). |
| `src/debs/server/macros.clj` | **Clojure** (not ClojureScript) namespace providing the `inline-resource` macro: `(inline-resource "prompts/system.md")` returns a string with the file contents baked in at compile time. Required because the `:server` build cannot load files at runtime. |
| `src/debs/pwa/main.cljs` | `glogi-console/install!`, sets log levels, creates Reagent root, dispatches `:initialize`, wires `start!` / `stop!` for hot reload. |
| `src/debs/pwa/ui.cljs` | PWA view: pasteable input → conditional `actionable-tweet-card` (rendered only when `::original-tweet` is non-nil). The view itself is the only place that wires subs to the card. |
| `src/debs/pwa/ui/events.cljs` | re-frame events. `:initialize` seeds `{:instructions "The response should be up to 280 characters."}`. Custom fx: `::read-clipboard`. Events: `::paste-from-clipboard`, `::set-tweet-url`, `::fetch-tweet`, `::original-tweet-success` (takes `tweet-id`), `::original-tweet-failure`, `::generate-response` (takes `tweet-id`; sets a 100ms `setInterval` driving `::response-progress-tick`), `::generation-success`, `::generation-failure`. The two `:fetch` calls hit `http://localhost:3000/tweet-info` and `http://localhost:3000/generator`. |
| `src/debs/pwa/ui/subscriptions.cljs` | `::tweet-url`, `::valid-tweet-url?`, `::original-tweet`, `::response` (shape `{:loading? bool :tweet-id str :text str}`), `::response-progress` (shape `{:progress number 0-100 :done? bool}`). |
| `src/debs/shared/validations.cljs` | `tweet-id-from-url` — single regex covering `twitter.com` / `x.com` / `mobile.` / `www.` variants. |
| `src/debs/shared/x_adapters.cljs` | `fetch-tweet` (oEmbed) + `extract-tweet-text` (strip blockquote tags, `<br>` → newline). Currently used for the legacy browser-side path; the PWA now hits the in-repo server. |
| `src/debs/shared/ui/components.cljs` | `pasteable-input` (input + Paste button, Bulma `is-success`/`is-danger` styling on validity), `actionable-tweet-card` (renders the original tweet as a blockquote, a `<progress>` bar while generating, then either a "de-bullshit" button when no response yet, or Copy / Post footer buttons once generated), `prefilled-reply-url` (X intent URL). |
| `src/debs/shared/ui/browser_helpers.cljs` | `copy-to-clipboard`, `paste-from-clipboard`, `build-url` (query-param encoder). |
| `resources/prompts/system.md` | The 12 KB Rothbard/Hoppe system prompt: 10 libertarian principles (individualism, isonomy, negative liberty, private property, contractual autonomy, restitution, voluntary association, free market, limited government, global liberal order) + 15 detection criteria for "socialist/collectivist" tweets + output rules (up to 280 chars, never add hashtags, return `"Prompt not socialist enough"` when criteria don't match). |
| `ext/manifest.json` | MV3: `sidePanel`, `activeTab`, `scripting`, `tabs`, `commands` permissions; `host_permissions: x.com/home` + `localhost:8000`; command `Ctrl+Shift+U` reload-extension. |
| `ext/js/initial-content-script.js` | Tiny ESM that `import()`s the compiled `content-script.js`. Used because MV3 content scripts are not modules natively. |
| `pwa/manifest.json` | PWA with `share_target` (title/text/url), theme color `#FFD700`. |
| `TODO.md` | Working notes. Open features: (1) stack multiple tweets + identify the right tweet/response pair, (2) distinguish users. Known bugs: (1) progress bar keeps rolling after contents ready, (2) the AI system prompt needs refinement — some weaker models emit long, un-tweet-shaped text or include hidden "Master of the Universe" framing from earlier drafts, (3) user should be able to re-generate (add a third button to `card-footer`). |

## Dependencies

From `package.json`:

- **`shadow-cljs` `^3.4.11`** — ClojureScript build tool. Drives all 7 builds.
- **`react` / `react-dom` `19.2.0`** — Reagent's underlying renderer.
- **`better-sqlite3` `^12.10.1`** — synchronous SQLite used at runtime by the `:server` build (lives in `dependencies`, not `devDependencies`).

From `shadow-cljs.edn :dependencies` (Clojure(Script) libs):

- **`reagent` `2.0.1`** + **`re-frame` `1.4.7`** — UI / state for PWA and side panel.
- **`applied-science/js-interop` `0.4.2`** + **`binaryage/oops` `0.7.2`** — JS interop helpers (use one or the other per file; both appear).
- **`lambdaisland/fetch` `1.5.83`** — Promise-based `fetch` wrapper. Used by PWA events.
- **`superstructor/re-frame-fetch-fx` `0.4.0`** — `:fetch` effect handler consumed by re-frame events.
- **`com.lambdaisland/glogi` `1.4.177`** + **`binaryage/devtools` `1.0.7`** — logging and dev preloads.
- **`org.clojure/data.json` `2.4.0`** — JSON read/write on the server side.
- **`cider/cider-nrepl` `0.59.0`** — nREPL middleware for editor integration.

External services (via `.env`):

- `TWITTER_API_KEY` → `twitterapi.io`
- `OPENROUTER_API_KEY`, `OPENROUTER_MODEL` (default `nvidia/nemotron-3-ultra-550b-a55b:free`; `.env` example uses `deepseek/deepseek-v4-flash`)
- `DEBS_API_ALLOWED_DOMAINS` — comma-separated origins for CORS (`*` to allow all)
- `DEBS_DB_PATH` — sqlite file location (default `debs.db`)

## Available APIs & how to call them

### Local server (`http://localhost:3000`)

- `GET /health` → `200 {:status :ok}`
- `GET /tweet-info?tweetId=<id>` (also accepts `id=`)
  - On cache hit → cached blob
  - On miss → `GET https://api.twitterapi.io/twitter/tweets?tweet_ids=<id>` (with 2 retries on `ECONNRESET`/5xx) then `INSERT OR REPLACE` into sqlite, return JSON.
- `POST /generator` body `{:original_text "...", :instructions "..."}` → `{:response "..."}` from OpenRouter.
  - The server injects the system prompt from `resources/prompts/system.md` automatically — callers send only the tweet and per-call instructions.
- CORS preflight: any path returns 204 with `Access-Control-Allow-*` headers.

```clojure
;; from a CLJS REPL, with the server running
(fetch/get "http://localhost:3000/health" {:accept :json})
(fetch/get "http://localhost:3000/tweet-info" {:accept :json :params {:tweetId "1726415665228141028"}})
(fetch/post "http://localhost:3000/generator"
            {:accept :json
             :content-type :json
             :body {:original_text "X is great"
                    :instructions "Reply from a Rothbardian perspective."}})
```

### Re-frame events / subs (PWA)

Events (`debs.pwa.ui.events`):
```clojure
(rf/dispatch [:initialize])                              ;; seeds {:instructions ...}
(rf/dispatch [::ui.events/paste-from-clipboard])         ;; reads navigator.clipboard → ::set-tweet-url
(rf/dispatch [::ui.events/set-tweet-url url])            ;; auto-fetches if URL parses
(rf/dispatch [::ui.events/fetch-tweet tweet-id])
(rf/dispatch [::ui.events/generate-response tweet-id])   ;; starts 30s progress interval
```

Subs:
```clojure
(rf/subscribe [::ui.subs/tweet-url])            ;; raw input string
(rf/subscribe [::ui.subs/valid-tweet-url?])     ;; boolean
(rf/subscribe [::ui.subs/original-tweet])       ;; {:loading? :id :text}
(rf/subscribe [::ui.subs/response])             ;; {:loading? :tweet-id :text} | nil
(rf/subscribe [::ui.subs/response-progress])     ;; {:progress 0-100 :done?}
```

### Extension message bus

Message shape: `{:type <keyword> ...}` — `messaging.cljs` keywordizes the `:type` on receive and `clj->js` on send.

| `:type` | Sender | Receiver | Payload |
|---|---|---|---|
| `:select-post` | content script | service worker | `{:type :select-post :content <tweet text> :meta <get-tweet-data map>}` |
| `:answer-ready` | service worker | side panel | `{:type :answer-ready :meta <…> :data <LLM response>}` |
| `:answer-error` | service worker | side panel | `{:type :answer-error :data <error>}` |
| `:begin-reply` | anywhere | content script | (placeholder, not yet wired to a real flow) |

Helpers:
```clojure
(messaging/send-message {:type :select-post :content t :meta m})
(messaging/send-to-active-tab {:type :begin-reply} tab-id)  ;; optional tab-id
(messaging/start-listening! handler)  ;; handler is (fn [{:type …}]) → any
```

### Shared helpers

```clojure
(require '[debs.shared.validations :as v])
(v/tweet-id-from-url "https://x.com/MadisIT/status/1726415665228141028")
;; => "1726415665228141028"
(v/tweet-id-from-url "not a url") ;; => nil

(require '[debs.shared.ui.browser-helpers :as bh])
(bh/build-url "https://x.com/intent/tweet" {:in_reply_to "123" :text "hello"})
;; => "https://x.com/intent/tweet?in_reply_to=123&text=hello"
(bh/copy-to-clipboard "text")
```

## Architecture

```
   ┌──────────────────────────────────┐    chrome.runtime    ┌──────────────────────────┐
   │  ext/content_script (X page)     │  ──────────────────▶ │  ext/service_worker (MV3) │
   │  • injects ✍️ on every tweet     │   {:type :select-    │  • builds Rothbard prompt │
   │  • MutationObserver for SPA      │     post …}         │  • POSTs to localhost:9621│
   └──────────────────────────────────┘                      └─────────────┬────────────┘
                                                                        │
          ┌──────────────────────────────────────────┐                  │
          │  ext/side_panel (Reagent + Bulma)        │ ◀─── :answer-ready
          │  • renders tweet + response card         │
          │  • Copy / Post / Save actions            │
          └──────────────────────────────────────────┘

   ┌──────────────────────────┐    fetch (:cors)    ┌────────────────────────────┐
   │  pwa (Reagent + re-frame)│ ──────────────────▶ │  server (Node.js, :3000)   │
   │  /index.html + manifest  │                     │  • router (handlers.cljs)  │
   │  share_target supported  │                     │  • /tweet-info (sqlite)    │
   └──────────────────────────┘                     │  • /generator (openrouter) │
                                                   └─────┬───────────────┬──────┘
                                                         │               │
                                                better-sqlite3   fetch (js/fetch,
                                                (debs.db)        AbortController,
                                                                exponential backoff)
                                                         ┌───────┴────────┐
                                                         │ twitterapi.io  │
                                                         │ openrouter.ai  │
                                                         └────────────────┘
```

Notes:
- The service worker's `responders.cljs` currently POSTs to **`http://localhost:9621/query`** (a separate responder gateway, not this repo's server). The in-repo `:server` build listens on **3000** and is what the PWA uses.
- The PWA is the only target currently wired end-to-end against the in-repo server (`/tweet-info` + `/generator`).
- The server's `openrouter.cljs` loads the system prompt at compile time from `resources/prompts/system.md` via the `inline-resource` macro — the `.md` file must be on the classpath (it is, via `shadow-cljs.edn` `:source-paths`).

## Implementation patterns & conventions

- **Three targets, shared Clojure(Script) source.** `:source-paths` includes `src` and `resources` for all builds; build-specific namespaces live under `debs.{ext,pwa,server}` and cross-target helpers under `debs.shared.*`. Test scaffolding lives under `test/debs/...`.
- **Macro for compile-time resource inlining.** `debs.server.macros/inline-resource` (a `.clj` file) `slurp`s a classpath file and returns the string at macro-expansion time. Use this for any non-code asset that should be baked into the `:simple` server bundle.
- **Promise-based async.** The server uses Node 18+ native `js/fetch` wrapped in `js/Promise.`; clients compose with `.then`/`.catch`. No core.async.
- **JS interop mix.** `applied-science.js-interop` (`j/call`, `j/get`, `j/assoc!`, `j/get-in`) and `oops.core` (`ocall`, `oget`, `oset!`) both appear. Per file, pick one. Reagent uses `.fn`/`.getElementById` directly.
- **State management.** PWA uses re-frame (events/subs in `debs.pwa.ui.{events,subscriptions}`). The side panel uses a single Reagent `defonce state` atom updated by message handlers. Content script uses raw DOM + `MutationObserver`.
- **Progress tracking via re-frame `setInterval`.** The PWA's `::generate-response` stores `{:start-time :counter :interval-id}` in `[:response-progress]` and `::response-progress-tick` recomputes progress from elapsed time / 30s. Both `::generation-success` and `::generation-failure` clear the interval (see `clear-response-progress-interval`).
- **Single-atom DB.** `debs.server.db` holds the `better-sqlite3` instance in a `defonce db` atom; `get-tweet`/`save-tweet!` are no-ops when `@db` is nil (lets tests skip init).
- **Env-driven config.** Server reads `process.env` at namespace load; defaults exist for `OPENROUTER_MODEL` and `DEBS_DB_PATH` but not for API keys — fail fast if missing.
- **CORS.** Single `cors-headers` fn in `debs.server.http` reads `DEBS_API_ALLOWED_DOMAINS` once at load. Use `*` to allow all.
- **Hot reload.** Each target that has UI (`:pwa`, `:side-panel`) defines `^:dev/before-load stop!` and `^:dev/after-load start!`; service worker / content script log on init for visibility.
- **No tests yet.** `test/debs/shared/` exists but is empty. `test/fixtures/` has cached `twitterapi.io.json`, `syndication.twimg.com.json`, and a `qwen3.7-result.json` for future use. The `:test` build is configured (`browser-test`, `public/test`) but unused.
- **Prompt as data.** The Rothbard/Hoppe system prompt is a plain `.md` file in `resources/prompts/`, embedded into the server build at compile time. The per-call `instructions` string comes from the PWA's `[:instructions]` slot in the app-db (default `"The response should be up to 280 characters."`), seeded by `:initialize`.
- **Per-call instructions live in the client, system prompt lives in the server.** The PWA sends `{:original_text :instructions}` and the server composes `<system>` + `<user>` before calling OpenRouter.

## Development workflow

```bash
# 1. Install
npm install

# 2. Boot the clj REPL (port written to .nrepl-port, target 3333)
npx shadow-cljs clj-repl

# 3. Inside the REPL — start watching all targets
(doseq [build-name [:content-script :service-worker :side-panel :popup :pwa :server]]
  (shadow/watch build-name))

# 4. Run the server (production-like; :simple output)
node server/debs-server.js
# or in REPL: (shadow/watch :server)  → then `(node server/debs-server.js)`

# 5. PWA dev server (shadow-cljs :dev-http on :8000 serves /pwa)
open http://localhost:8000/

# 6. Chrome extension
# Load /ext as an unpacked extension (chrome://extensions → Developer mode → Load unpacked)
# After code changes, hit Ctrl+Shift+U (the manifest command) to reload-extension.
```

Per-target REPL eval namespaces are set on `:pwa` (`debs.pwa.main`); the other
targets don't pin one but their init fns are good entry points.

## Extension points

- **Responder gateway.** `ext/service_worker/responders.cljs` POSTs to `localhost:9621/query`. The in-repo server at `:3000` already exposes `/generator` with the same shape — swap the URL to consolidate.
- **New social platforms.** `content-helpers.cljs` has all the platform-specific DOM logic in one place. Add a sibling namespace + manifest `content_scripts.matches` entry. The `tweet-selector` constant is the only platform-specific part of the injection logic.
- **New re-frame events/subs.** Add to `debs.pwa.ui.events` and `debs.pwa.ui.subscriptions`; the pattern is well-established (event-fx with `:fetch` or custom fx, then a `::set-…-success` / `::set-…-failure` event-db pair). Remember to clear any `setInterval` you started.
- **New server routes.** Add to the `routes` vector in `handlers.cljs`; add handler functions above. Use `http/send-json` and `http/send-error` for response writing; `http/read-body` and `http/fetch-json` for I/O. `fetch-json` takes `:max-retries` and `:timeout` options.
- **Different LLM provider.** Replace `debs.server.openrouter/chat-completion`; the `request-options` / `request-body` / response-extraction pattern is isolated. The `inline-resource` macro can load any `.md`/`.txt` system prompt from `resources/`.
- **Tweaking the system prompt.** Edit `resources/prompts/system.md` directly. The server must be rebuilt (`shadow/watch :server` + restart `node server/debs-server.js`) for changes to take effect.
- **Caching.** `debs.server.db` currently has one table (`tweets (id, data)`). Easy to add more (e.g. `generations`, `users`) by following the `defonce db` + `init-db!` pattern.
- **CORS allowlist.** `.env` `DEBS_API_ALLOWED_DOMAINS` — change from `*` to a comma list for production.
- **PWA share-target.** `pwa/manifest.json` already declares `share_target` (title/text/url). Wire an `?text=…` query parser into `debs.pwa.main/init` to auto-fill the input on share.
- **Re-generate button.** Tracked in `TODO.md` — add a third footer button to `actionable-tweet-card` that re-dispatches `::generate-response`.
- **Tweaking per-call instructions.** Either change the `:initialize` event in `debs.pwa.ui.events`, or add a UI control that updates `[:instructions]` in the app-db.
- **Tests.** `test/debs/shared/` is empty; fixtures are present. Drop a `deftest` using `debs.shared.validations/tweet-id-from-url` and fixtures to bootstrap.
