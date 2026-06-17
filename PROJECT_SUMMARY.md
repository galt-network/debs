# deBS — Project Summary

A ClojureScript multi-target application for analyzing and refuting collectivist
content (focused on X / Twitter posts) from a libertarian perspective
(NAP, self-ownership, Rothbard/Hoppe-aligned reasoning). It ships as **three
deployed targets** from one source tree:

1. **Chrome MV3 extension** — injects a ✍️ "Reply Helper" button on X/Twitter
   tweets, opens a side panel with the tweet card and an LLM-generated
   refutation, plus a popup.
2. **PWA** — standalone web app where the user pastes a tweet URL and gets a
   refutation back. **Multi-tweet**: a list of cards persists across reloads
   via `localStorage`. The PWA is the primary client for the in-repo server.
3. **Node.js HTTP server** — caches tweets in SQLite, proxies calls to
   `twitterapi.io`, and forwards prompts to **OpenRouter** for generation.
   Routes are mounted under `/api/...`.

Deployed at `https://debs.galt.is` (see `deploy.sh`).

## Repository layout

```
debs/
├── shadow-cljs.edn         # build config (server + 4 ext + pwa + test)
├── package.json            # shadow-cljs, react, better-sqlite3
├── package-lock.json
├── README.md               # dev workflow
├── TODO.md                 # outstanding features + known bugs
├── deploy.sh                # deploy: release pwa+server, rsync to rothbard
├── .env                    # TWITTER_API_KEY, OPENROUTER_*, DEBS_API_*
├── .gitignore              # ignores debs.db, server/debs-server.js
├── src/debs/
│   ├── ext/                # Chrome extension targets
│   │   ├── content_script.cljs         + content_script/message_handlers.cljs
│   │   ├── content_helpers.cljs        # DOM injection of ✍️ button on tweets
│   │   ├── messaging.cljs              # chrome.runtime message bus wrapper
│   │   ├── service_worker.cljs         + service_worker/{message_handlers,responders}.cljs
│   │   ├── side_panel.cljs             + side_panel/{ui,message_handlers}.cljs
│   │   └── popup.cljs
│   ├── pwa/                # PWA target
│   │   ├── main.cljs                   # boot, share-target wiring, hot-reload
│   │   ├── ui.cljs                     # root view (renders all-tweets)
│   │   ├── storage.cljs                # localStorage cofx + persist fx
│   │   └── ui/{events,subscriptions}.cljs
│   ├── server/             # Node.js HTTP server
│   │   ├── main.cljs                   # entry: init-db! + http.createServer
│   │   ├── handlers.cljs               # /api/* route table + pipelines
│   │   ├── http.cljs                   # CORS, JSON, body, fetch-json (retry/backoff)
│   │   ├── db.cljs                     # better-sqlite3 wrapper (tweets table)
│   │   ├── openrouter.cljs             # OpenRouter chat-completion client
│   │   ├── twitter.cljs                # twitterapi.io tweet lookup
│   │   └── macros.clj                  # inline-resource macro (compile-time slurp)
│   └── shared/             # code used by multiple targets
│       ├── validations.cljs            # tweet-id regex
│       ├── time_helpers.cljs           # Intl.RelativeTimeFormat wrapper
│       ├── x_adapters.cljs             # oEmbed-based tweet text extraction
│       └── ui/{components,browser_helpers}.cljs
├── resources/              # classpath assets inlined at compile time
│   └── prompts/
│       └── system.md                   # 12 KB libertarian system prompt (Rothbard/Hoppe)
├── ext/                    # static assets loaded by the Chrome extension
│   ├── manifest.json                   # MV3 manifest (side panel, content script)
│   ├── popup.html, side-panel.html
│   ├── js/initial-content-script.js    # thin ESM that imports the real bundle
│   ├── css/{content,side_panel}.css
│   ├── icons/                          # 16/32/48/128 png
│   └── vendor/                         # bulma.css, datastar.js (datastar unused)
├── pwa/                    # static assets for the PWA
│   ├── index.html
│   ├── manifest.json                   # PWA manifest with share_target
│   ├── css/styles.css
│   ├── js/                             # shadow-cljs :pwa output
│   └── icons/
├── public/test/            # browser-test target output (see :test build)
├── test/
│   ├── fixtures/                       # cached twitterapi.io + syndication + qwen3.7 JSON
│   └── debs/shared/                    # cljs test namespaces live here (empty)
├── server/debs-server.js   # shadow-cljs :simple build output (gitignored)
└── debs.db                 # sqlite cache (gitignored)
```

## Key files & their roles

| Path | Role |
|---|---|
| `shadow-cljs.edn` | 7 builds: `:server`, `:content-script`, `:service-worker`, `:side-panel`, `:popup`, `:pwa`, `:test`. `:source-paths` includes `["dev" "src" "resources" "test"]` so `resources/prompts/system.md` is loadable. `:nrepl {:port 3333}`. `:dev-http {8000 "pwa" 8001 "public/test"}`. The `:pwa` build uses `:closure-defines {debs.pwa.ui.events/DEBS_API_BASE_URL #shadow/env ["DEBS_API_BASE_URL" "http://localhost:3000/api"]}` so the API base URL is baked in at compile time. |
| `package.json` | Dev: `shadow-cljs ^3.4.11`. Runtime deps: `react`/`react-dom 19.2.0`, `better-sqlite3 ^12.10.1` (moved to `dependencies` — needed at runtime by the Node server). |
| `deploy.sh` | `rm -rf pwa/js/cljs-runtime; DEBS_API_BASE_URL=https://debs.galt.is/api shadow-cljs release pwa server; rsync -av package.json server pwa rothbard:~/www/debs.galt.is`. Production PWA uses `/api` path prefix. |
| `src/debs/ext/content_script.cljs` | Boot. Inits DOM `MutationObserver` and starts the message listener. |
| `src/debs/ext/content_helpers.cljs` | `tweet-selector`, `inject-reply-button`, `init-reply-helper!` — DOM scraping + ✍️ button injection. `get-tweet-text` and `get-tweet-data`. |
| `src/debs/ext/messaging.cljs` | Wrapper over `chrome.runtime.onMessage`/`sendMessage` with `js<->clj` coercion and keyword `:type` normalization. |
| `src/debs/ext/service_worker.cljs` | MV3 background. Configures side panel per origin (enabled only on `x.com` + `http://localhost:8000`), wires tab-update listener. |
| `src/debs/ext/service_worker/message_handlers.cljs` | `prepare-prompt` builds Rothbard/Hoppe system prompt; `:select-post` triggers generation. |
| `src/debs/ext/service_worker/responders.cljs` | `generate-response` POSTs to **`http://localhost:9621/query`** (external responder gateway, not the in-repo server). **Extension point** — the in-repo `/api/generator` has the same shape. |
| `src/debs/ext/side_panel.cljs` | Reagent root + `start-or-reload!`/`stop!` hooks. |
| `src/debs/ext/side_panel/ui.cljs` | Renders last-selected post + saved-posts list using Bulma cards. |
| `src/debs/ext/side_panel/message_handlers.cljs` | `handle-select-post`/`handle-answer-ready`/`handle-answer-error` mutate a local Reagent atom. |
| `src/debs/server/main.cljs` | Reads `DEBS_DB_PATH` (default `debs.db`), calls `db/init-db!`, then `http.createServer handlers/router` on port 3000. |
| `src/debs/server/handlers.cljs` | **Routes are now `/api/*`**: `[[:options :any …] [:get "/api/health" …] [:get "/api/tweet-info" …] [:post "/api/generator" …]]`. Linear-pipeline handlers: `health-handler`, `generator-handler`, `tweet-info-handler`. `build-prompt` joins `<instructions>`+`<tweet>`. `generator-input` rejects when `original_text` or `instructions` missing → 400. `fetch-and-cache` errors return 502 with `{:error :message :code}`. |
| `src/debs/server/http.cljs` | `allowed-domains` set parsed from `DEBS_API_ALLOWED_DOMAINS`. `cors-headers` exposes `Content-Type, X-API-Key`. `send-json`/`send-error`/`read-body`/`fetch-json` with `AbortController`-based timeout + exponential backoff (200ms→2s cap) on `ETIMEDOUT`/`ECONNRESET`/5xx. |
| `src/debs/server/db.cljs` | `defonce db` atom holding a `better-sqlite3` connection; `init-db!` creates `tweets (id TEXT PK, data TEXT)`. `get-tweet`/`save-tweet!` no-op when `@db` is nil. |
| `src/debs/server/openrouter.cljs` | Reads `OPENROUTER_API_KEY`, `OPENROUTER_MODEL` (default `nvidia/nemotron-3-ultra-550b-a55b:free`), **`OPENROUTER_API_ENDPOINT`** (no default — must be set, e.g. `https://openrouter.ai/api/v1/chat/completions`). Headers: `Authorization: Bearer …`, `HTTP-Referer: http://localhost:3000`, `X-OpenRouter-Title: Debs`. **`:messages` order is `[user-prompt, system]`** (reversed from typical). `system-prompt` baked in at compile time via `inline-resource` macro from `resources/prompts/system.md`. `:max_tokens 500` is commented out. |
| `src/debs/server/twitter.cljs` | `twitterapi.io` `GET /twitter/tweets?tweet_ids=…` with `X-API-Key`. Uses `http/fetch-json` with `:max-retries 2`, `:timeout 10000`. |
| `src/debs/server/macros.clj` | **Clojure** (not ClojureScript) namespace. `(inline-resource "prompts/system.md")` returns the file's contents at macro-expansion time (`:simple` server bundle can't load files at runtime). |
| `src/debs/pwa/main.cljs` | `glogi-console/install!`, sets log levels, creates Reagent root via `reagent.dom.client`, dispatches `:initialize`, **wires share-target** (`URLSearchParams` parses `?title/text/url`, dispatches `::set-tweet-url`, then `history.replaceState` strips the query). `start!`/`stop!` for hot reload. |
| `src/debs/pwa/ui.cljs` | Single root view: `pasteable-input` + `doall (map tweet-card-with-generate @tweets)`. Each card gets `:generate-response` (a fn that dispatches `::generate-response` with the card's `:tweet-id`) and `:tag-info` (a `::relative-time` sub keyed to `:created-at`). |
| `src/debs/pwa/storage.cljs` | localStorage persistence. `save-db!` `pr-str`s the whole app-db under key `"debs-app-db"`. `load-db` deserializes via `cljs.reader/read-string` (with try/catch fallback to defaults). Registers `::local-storage-db` cofx (read on `:initialize`) and `::persist-db` fx (write on success events). |
| `src/debs/pwa/ui/events.cljs` | re-frame events. **Multi-tweet app-db**: `:tweet-ids` (list, distinct on insert) + `:tweets <id>` map. Module-level `(defonce timer (js/setInterval … 5000))` dispatches `::tick` to refresh `:now` so `::relative-time` re-renders. `:initialize` deep-merges `default-db` with persisted state, stripping `:config` and `:now` from storage. Custom fx: `::read-clipboard` (uses `navigator.clipboard.readText`). Events: `::tick`, `::paste-from-clipboard`, `::set-tweet-url [url share]` (3-arity — auto-fetches when URL parses, accepts optional `share` map for share-target wiring), `::fetch-tweet [tweet-url]` (computes `tweet-id` from URL), `::original-tweet-success [tweet-id tweet-url result]`, `::original-tweet-failure [result]`, `::generate-response [tweet-id]`, `::generation-success [tweet-id result]`, `::generation-failure [tweet-id result]`, `::response-progress-tick [tweet-id]`, `::clipboard-read-failed [err]`. Both fetch URLs are `(str api-base-url "/tweet-info")` and `(str api-base-url "/generator")` where `api-base-url` is `[:config :api-base-url]` in app-db. `clear-response-progress-interval` is a private helper. |
| `src/debs/pwa/ui/subscriptions.cljs` | `::now` (Date), `::relative-time` (signature `[_ timestamp]` — pairs `:now` with the timestamp arg via a sub-input vec), `::tweet-url`, `::valid-tweet-url?`, `::all-tweets` (`(map (fn [id] (get-in db [:tweets id])) (:tweet-ids db))`). |
| `src/debs/shared/validations.cljs` | `tweet-id-from-url` — single regex covering `twitter.com`/`x.com`/`mobile.`/`www.` variants. |
| `src/debs/shared/time_helpers.cljs` | `relative-time-str` — uses `Intl.RelativeTimeFormat` (locale from `navigator.language`, default `"en"`, options `{:numeric "auto" :style "long"}`). Buckets: <10s→"just now", <45s→seconds, <90s/45m→minutes, <1.5h/90m→hours, <1d/2d→days. Works past + future. |
| `src/debs/shared/x_adapters.cljs` | `fetch-tweet` (oEmbed) + `extract-tweet-text` (strip blockquote tags, `<br>`→newline). Used by the legacy browser-side path; the PWA now hits the in-repo server. |
| `src/debs/shared/ui/components.cljs` | `pasteable-input` (input + Paste button, Bulma `is-success`/`is-danger` styling on validity), **`tweet-card`** (renders original tweet as blockquote, a `<progress>` bar while generating, then either a single "de-bullshit" button or Copy / Post / **"New answer"** footer buttons once generated), `prefilled-reply-url` (X intent URL). |
| `src/debs/shared/ui/browser_helpers.cljs` | `copy-to-clipboard`, `paste-from-clipboard`, `build-url` (query-param encoder). |
| `resources/prompts/system.md` | 12 KB Rothbard/Hoppe system prompt: 10 libertarian principles (individualism, isonomy, negative liberty, private property, contractual autonomy, restitution, voluntary association, free market, limited government, global liberal order) + 15 detection criteria for "socialist/collectivist" tweets + output rules (up to 280 chars, never add hashtags, return `"Prompt not socialist enough"` when criteria don't match). |
| `ext/manifest.json` | MV3: `sidePanel`, `activeTab`, `scripting`, `tabs`, `commands` permissions; `host_permissions: ["http://localhost:8000/*", "https://x.com/home"]`; command `Ctrl+Shift+U` reload-extension; `web_accessible_resources` exposes `js/*` and `js/cljs-runtime/*`; content script matches `<all_urls>`, loads `js/initial-content-script.js`. |
| `ext/js/initial-content-script.js` | Tiny ESM that `import()`s the compiled `content-script.js`. Used because MV3 content scripts are not modules natively. |
| `pwa/manifest.json` | PWA with `share_target` (title/text/url), theme color `#FFD700`. |
| `TODO.md` | 4 open items: (1) re-generate button — **actually implemented** ("New answer" in `tweet-card`); (2) prohibit duplicate tweet-ids — distinct on insert in `::original-tweet-success`, but `[:tweet-ids]` itself doesn't dedup past state correctly; (3) config panel (length/tone/context) between last answered card and search bar; (4) delete cards (long-press/swipe-left). |

## Dependencies

From `package.json`:
- **`shadow-cljs` `^3.4.11`** (devDep) — drives all 7 builds.
- **`react` / `react-dom` `19.2.0`** (dep) — Reagent's renderer.
- **`better-sqlite3` `^12.10.1`** (dep, runtime) — synchronous SQLite used by `:server`.

From `shadow-cljs.edn :dependencies`:
- **`reagent` `2.0.1`** + **`re-frame` `1.4.7`** — UI / state.
- **`applied-science/js-interop` `0.4.2`** + **`binaryage/oops` `0.7.2`** — JS interop (pick one per file; both appear).
- **`lambdaisland/fetch` `1.5.83`** — Promise-based `fetch` (used by PWA events and SW responders).
- **`superstructor/re-frame-fetch-fx` `0.4.0`** — `:fetch` re-frame fx.
- **`com.lambdaisland/glogi` `1.4.177`** + **`binaryage/devtools` `1.0.7`** — logging + dev preloads.
- **`org.clojure/data.json` `2.4.0`** — JSON on server.
- **`cider/cider-nrepl` `0.59.0`** — editor integration.

External services (via `.env`):
- `TWITTER_API_KEY` → `twitterapi.io`
- `OPENROUTER_API_KEY`, `OPENROUTER_MODEL` (default `nvidia/nemotron-3-ultra-550b-a55b:free`; `.env` example uses `deepseek/deepseek-v4-flash`), **`OPENROUTER_API_ENDPOINT`** (no default — must be set)
- `DEBS_API_ALLOWED_DOMAINS` — comma-separated origins for CORS (`*` to allow all)
- `DEBS_DB_PATH` — sqlite file location (default `debs.db`)
- **`DEBS_API_BASE_URL`** — PWA's API base, compiled in via shadow-cljs `:closure-defines` (default `http://localhost:3000/api`; production `https://debs.galt.is/api`). Stored in app-db at `[:config :api-base-url]`.

## Available APIs & how to call them

### Local server (`http://localhost:3000`)

All routes are **prefixed `/api/`**:

- `GET /api/health` → `200 {:status :ok}`
- `GET /api/tweet-info?tweetId=<id>` (also accepts `id=`)
  - Cache hit → cached blob
  - Miss → `GET https://api.twitterapi.io/twitter/tweets?tweet_ids=<id>` (2 retries on `ECONNRESET`/5xx) then `INSERT OR REPLACE` into sqlite, return JSON
- `POST /api/generator` body `{:original_text "...", :instructions "..."}` → `{:response "..."}` from OpenRouter
  - Server injects system prompt from `resources/prompts/system.md` — callers send only tweet + per-call instructions
  - 400 when either field missing; 502 on OpenRouter failure
- CORS preflight: any path returns 204 with `Access-Control-Allow-Origin/Methods/Headers` (`X-API-Key` allowed) headers.

```clojure
(fetch/get "http://localhost:3000/api/health" {:accept :json})
(fetch/get "http://localhost:3000/api/tweet-info" {:accept :json :params {:tweetId "1726415665228141028"}})
(fetch/post "http://localhost:3000/api/generator"
            {:accept :json
             :content-type :json
             :body {:original_text "X is great"
                    :instructions "Reply from a Rothbardian perspective."}})
```

### PWA app-db shape (multi-tweet)

```clojure
{:tweet-url nil                       ; raw input string (cleared after fetch)
 :now (js/Date.)                      ; updated by ::tick every 5s
 :config {:api-base-url "http://localhost:3000/api"}
 :instructions "The response length should be up to 280 characters."
 :tweet-ids (list "172641..." "117541...")    ; order of cards, distinct
 :tweets {"1726415665228141028"
          {:loading? false
           :created-at (js/Date.)              ; for ::relative-time sub
           :tweet-url "https://x.com/..."
           :tweet-id "1726415665228141028"
           :text "..."                          ; original tweet text
           :response {:tweet-id "..." :text "..."}        ; nil while pending
           :response-progress {:start-time ms
                               :progress 0-100
                               :interval-id js/timeout-id   ; for cleanup
                               :done? bool}}}}
```

### Re-frame events / subs (PWA)

Events (`debs.pwa.ui.events`):
```clojure
(rf/dispatch [:initialize])                              ;; deep-merges default-db with localStorage
(rf/dispatch [::ui.events/paste-from-clipboard])         ;; reads clipboard → ::set-tweet-url
(rf/dispatch [::ui.events/set-tweet-url url])            ;; 2-arg
(rf/dispatch [::ui.events/set-tweet-url url share])      ;; 3-arg (share-target path)
(rf/dispatch [::ui.events/fetch-tweet tweet-url])        ;; takes URL, computes id
(rf/dispatch [::ui.events/generate-response tweet-id])   ;; starts 30s progress interval
;; success/failure events are dispatched by :fetch fx
```

Subs:
```clojure
(rf/subscribe [::ui.subs/now])              ;; (js/Date.) — refreshed every 5s
(rf/subscribe [::ui.subs/relative-time t])  ;; t is a timestamp arg → "5 minutes ago" etc.
(rf/subscribe [::ui.subs/tweet-url])        ;; raw input string
(rf/subscribe [::ui.subs/valid-tweet-url?]) ;; boolean
(rf/subscribe [::ui.subs/all-tweets])       ;; seq of tweet maps in :tweet-ids order
```

For per-tweet state, deref `[:tweets tweet-id]` from app-db (no top-level subs).

### Extension message bus

Message shape `{:type <keyword> ...}` — `messaging.cljs` keywordizes `:type` on receive, `clj->js` on send.

| `:type` | Sender | Receiver | Payload |
|---|---|---|---|
| `:select-post` | content script | service worker | `{:type :select-post :content <tweet text> :meta <get-tweet-data map>}` |
| `:answer-ready` | service worker | side panel | `{:type :answer-ready :meta <…> :data <LLM response>}` |
| `:answer-error` | service worker | side panel | `{:type :answer-error :data <error>}` |
| `:begin-reply` | anywhere | content script | placeholder |

```clojure
(messaging/send-message {:type :select-post :content t :meta m})
(messaging/send-to-active-tab {:type :begin-reply} tab-id)
(messaging/start-listening! handler)  ;; (fn [{:type …}]) → any
```

### Shared helpers

```clojure
(require '[debs.shared.validations :as v])
(v/tweet-id-from-url "https://x.com/MadisIT/status/1726415665228141028") ;; => "1726415665228141028"
(v/tweet-id-from-url "not a url") ;; => nil

(require '[debs.shared.time-helpers :as th])
(th/relative-time-str (js/Date.))                    ;; => "just now"
(th/relative-time-str (- (.getTime (js/Date.)) (* 5 60 1000))) ;; => "5 minutes ago"

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
   │  /api/ prefix            │   /api/tweet-info   │  • router (handlers.cljs)  │
   │  multi-tweet app-db      │   /api/generator    │  • /api/tweet-info (sqlite)│
   │  localStorage persist    │                     │  • /api/generator (openrouter)│
   │  share_target wired      │                     └─────┬───────────────┬──────┘
   │  DEBS_API_BASE_URL       │                           │               │
   │   baked in at compile    │                  better-sqlite3   fetch (js/fetch,
   └──────────────────────────┘                  (debs.db)        AbortController,
                                                       │            exponential backoff)
                                                       │                  │
                                                       └──────┬───────────┘
                                                              │
                                                     ┌────────┴────────┐
                                                     │ twitterapi.io   │
                                                     │ openrouter.ai   │
                                                     └─────────────────┘
```

Notes:
- The service worker's `responders.cljs` POSTs to **`http://localhost:9621/query`** (external responder gateway, not this repo's server). The in-repo `:server` build listens on **3000** under **`/api/*`** and is what the PWA uses.
- The PWA is the only target wired end-to-end against the in-repo server.
- `DEBS_API_BASE_URL` is a `:closure-defines` on the `:pwa` build, so the server base is baked into the JS at compile time, then read into app-db `[:config :api-base-url]` on `:initialize`.
- The server's `openrouter.cljs` loads the system prompt at compile time from `resources/prompts/system.md` via the `inline-resource` macro.
- The PWA's `debs.pwa.storage` namespace persists the entire app-db to `localStorage["debs-app-db"]` on every state-mutating success event; on `:initialize` it deep-merges with `default-db`.

## Implementation patterns & conventions

- **Three targets, shared Clojure(Script) source.** `:source-paths` includes `src` and `resources` for all builds; build-specific namespaces live under `debs.{ext,pwa,server}` and cross-target helpers under `debs.shared.*`. Test scaffolding under `test/debs/...`.
- **Compile-time resource inlining.** `debs.server.macros/inline-resource` (a `.clj` file) `slurp`s a classpath file and returns the string at macro-expansion time. Use for any non-code asset that should be baked into the `:simple` server bundle.
- **Promise-based async.** Server uses Node 18+ native `js/fetch` wrapped in `js/Promise.`; clients compose with `.then`/`.catch`. No core.async.
- **JS interop mix.** `applied-science.js-interop` (`j/call`, `j/get`, `j/assoc!`, `j/get-in`) and `oops.core` (`ocall`, `oget`, `oset!`) both appear. Per file, pick one.
- **State management.** PWA uses re-frame with a **multi-tweet app-db** (`:tweet-ids` list + `:tweets <id>` map) and `localStorage` persistence via `debs.pwa.storage`. The side panel uses a single Reagent `defonce state` atom. Content script uses raw DOM + `MutationObserver`.
- **Live-updating timestamps via module-level `setInterval`.** `debs.pwa.ui.events` defines `(defonce timer (js/setInterval … 5000))` that dispatches `::tick`, which updates `:now` in app-db. The `::relative-time` sub takes a timestamp as a sub-input vec and re-evaluates whenever `:now` changes — so each card's "5 minutes ago" tag re-renders every 5s.
- **Progress tracking via re-frame `setInterval`.** `::generate-response` stores `{:start-time :counter :interval-id}` at `[:tweets tweet-id :response-progress]` and `::response-progress-tick` recomputes progress from elapsed time / 30s. Both `::generation-success` and `::generation-failure` clear the interval via `clear-response-progress-interval`.
- **Single-atom DB.** `debs.server.db` holds the `better-sqlite3` instance in a `defonce db` atom; `get-tweet`/`save-tweet!` no-op when `@db` is nil (lets tests skip init).
- **Env-driven config.** Server reads `process.env` at namespace load; defaults exist for `OPENROUTER_MODEL` and `DEBS_DB_PATH` but not for `OPENROUTER_API_KEY`/`OPENROUTER_API_ENDPOINT`/`TWITTER_API_KEY` — fail fast if missing.
- **Compile-time env injection.** The PWA's API base URL is baked in via shadow-cljs `:closure-defines {debs.pwa.ui.events/DEBS_API_BASE_URL #shadow/env ["DEBS_API_BASE_URL" "http://localhost:3000/api"]}`. Different env per build profile (dev: `:3000/api`; prod: `https://debs.galt.is/api`).
- **CORS.** Single `cors-headers` fn in `debs.server.http` reads `DEBS_API_ALLOWED_DOMAINS` once at load. `Access-Control-Allow-Headers` includes `X-API-Key`. Use `*` to allow all origins.
- **Hot reload.** Each target that has UI (`:pwa`, `:side-panel`) defines `^:dev/before-load stop!` and `^:dev/after-load start!`; service worker / content script log on init.
- **No tests yet.** `test/debs/shared/` exists but is empty. `test/fixtures/` has cached `twitterapi.io.json`, `syndication.twimg.com.json`, and `qwen3.7-result.json` for future use. The `:test` build is configured (`browser-test`, `public/test`) but unused.
- **Prompt as data.** The Rothbard/Hoppe system prompt is a plain `.md` file in `resources/prompts/`, embedded into the server build at compile time. The per-call `instructions` string lives in app-db `[:instructions]` (default `"The response length should be up to 280 characters."`), seeded by `:initialize`.
- **Per-call instructions live in the client, system prompt lives in the server.** The PWA sends `{:original_text :instructions}` and the server composes `<system>` (compile-time) + `<user>` (request body) before calling OpenRouter.
- **PWA persistence is opaque EDN-ish.** `debs.pwa.storage/save-db!` `pr-str`s the entire app-db; `load-db` uses `cljs.reader/read-string`. `:initialize` strips `:config` and `:now` from the loaded snapshot before merging (so production URLs aren't clobbered by stale local state, and stale `:now` doesn't shadow fresh `::tick` updates).

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

# 7. Production release
./deploy.sh
# - cleans pwa/js/cljs-runtime
# - releases pwa+server with DEBS_API_BASE_URL=https://debs.galt.is/api
# - rsyncs package.json + server/ + pwa/ to rothbard:~/www/debs.galt.is
```

`:repl-init-ns debs.pwa.main` is set on the `:pwa` build; other targets don't pin one but their init fns are good entry points.

## Extension points

- **Consolidate the responder gateway.** `ext/service_worker/responders.cljs` POSTs to `localhost:9621/query`. The in-repo server at `/api/generator` already has the same shape — swap the URL to consolidate. (Note: payload shape differs — `:query`/`mode` vs `:original_text`/`instructions`.)
- **New social platforms.** `content-helpers.cljs` contains all the platform-specific DOM logic. Add a sibling namespace + a `content_scripts.matches` entry in `ext/manifest.json`. `tweet-selector` is the only platform-specific piece of the injection logic.
- **New re-frame events/subs.** Add to `debs.pwa.ui.events` and `debs.pwa.ui.subscriptions`. The pattern is: `reg-event-fx` returning `{:db … :fx [[:fetch …]]}` (or a custom fx), then `::set-…-success`/`::set-…-failure` events. Remember to clear any `setInterval` you started. Add `::persist-db` to `:fx` on success events to persist.
- **New server routes.** Add to the `routes` vector in `handlers.cljs` (under `/api/`). Use `http/send-json`/`send-error` for response writing; `http/read-body`/`http/fetch-json` for I/O. `fetch-json` accepts `:max-retries` and `:timeout`.
- **Different LLM provider.** Replace `debs.server.openrouter/chat-completion`; the `request-body`/response-extraction pattern is isolated. `inline-resource` can load any `.md`/`.txt` system prompt from `resources/`.
- **Tweaking the system prompt.** Edit `resources/prompts/system.md` directly. The server must be rebuilt (`shadow/watch :server` + restart `node server/debs-server.js`) for changes to take effect.
- **Caching.** `debs.server.db` currently has one table (`tweets (id, data)`). Add more by following the `defonce db` + `init-db!` pattern.
- **CORS allowlist.** `.env` `DEBS_API_ALLOWED_DOMAINS` — change from `*` to a comma list for production.
- **Config panel.** `TODO.md` item — extend `debs.pwa.ui.events` `default-db` and the `actionable-tweet-card`/`tweet-card` view to expose length/tone/context controls that update `[:instructions]`. The "between last answered card and search bar" placement requires a `::last-answered-tweet` derived sub.
- **Card deletion.** Tracked in `TODO.md`. Add a long-press/swipe-left gesture to `tweet-card` that dispatches a new `::delete-tweet [tweet-id]` event which removes from both `[:tweet-ids]` and `[:tweets]`, then `::persist-db`.
- **Per-call instructions UI.** Either change `default-db` in `debs.pwa.ui.events`, or add a UI control that updates `[:instructions]`.
- **Tests.** `test/debs/shared/` is empty; fixtures are present. Drop a `deftest` using `debs.shared.validations/tweet-id-from-url` and `debs.shared.time-helpers/relative-time-str` to bootstrap. Server-side tests could exercise `debs.server.handlers/router` via mock `req`/`res`.
