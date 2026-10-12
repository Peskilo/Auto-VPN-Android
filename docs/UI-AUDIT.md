# UI audit (WP-11a)

Read-only audit of the UI as of `b19aac5`. No production code changed. Evidence: Android emulator
(Medium_Phone_API_36.1, 1080x2400@420dpi), debug build, synthetic subscription (6 fake nodes + local
SOCKS relay), screenshots and `uiautomator` dumps `s01..s55` kept locally in `.pi/wp11a/` (not committed).
Scripts there: `contrast.py`, `l10n.py`, `scan48.py`. Everything marked **[code]** was judged from source only.

## 1. Screen inventory

| Screen | Where | States seen | Notes |
|---|---|---|---|
| Home (`AfterglowHome`) | NavHost route | empty, idle, connected, error, selection-error, ru, dark, font 2.0, landscape, tablet | hero + server/routing cards + stats + shortcuts + bottom action bar |
| Settings | route | en/ru/dark/font 2.0 | flat list, no section headers |
| Servers | bottom sheet | list, latency, timeout, selected | |
| Subscriptions (+add, +detail) | bottom sheet | empty, ok, failed add | add dialog -> HWID consent dialog |
| Routing / Per-app | sheet / route | | |
| Connections, Diagnostics, Session details | routes | connected | |
| QR scanner, VPN permission, notification permission, Quick Settings tile, notifications | system/other | **not reproduced** (no camera / not triggered) **[code]** | |

Not reproduced on emulator (assessed by code): Connecting/Preparing/Reconnecting transitions, stale-latency
chip (>7 min), "connected but unreachable" health surface, notification look.

## 2. Design system

- Theme in `ui/theme/` (`AfterglowColors`, `AfterglowTokens`, `Type`, `Theme`). Light and dark palettes exist.
- **Hero colors (offline/connected/transitional/error) are identical in light and dark** and are not semantic:
  offline is red (`#BC3E3E`) and nearly equal to error (`#942E3B`, contrast ratio 1.44). "Disconnected" looks like a failure.
- Leftovers: `ui/theme/Color.kt` (unused M3 template purple/pink), `res/values/colors.xml` (template purple/teal);
  `themes.xml` parent is `Theme.Material.Light.NoActionBar` (possible light flash on cold start in dark theme — unverified).
- Dynamic color is off by default and only touches accents.
- `AfterglowHome.kt` hardcodes dozens of `sp`/`dp` (10–31 sp); `Type.kt` tokens are barely used. Icons are Canvas-drawn; arrows are text glyphs "↓/↑".
- Bottom bar, cards, borders, mascot: custom; Settings uses default M3 switches and a differently styled battery card.

**Proposed tokens (WP-11b):** semantic state colors per theme — Disconnected = neutral ink/muted (not red),
Connecting/Reconnecting = amber, Connected = jade/teal, Error = red clearly distinct from brand coral and from Disconnected;
type scale (label/caption/body/title/hero) in `Type.kt`; spacing/radius tokens; one `Wordmark`/brand composable. Delete template colors.

## 3. Contrast (WCAG, computed: `contrast-report.txt`)

Fails AA text 4.5:1 (light): muted on paperSecondary 4.18; coral on paper 3.77 (11sp caps section headers, text buttons);
coral on paperSecondary 3.43 (protocol tag, "pending"); coral on errorSurface 3.24 (notice action); onInk on coral 3.97 (primary button);
hero secondary label (alpha .72, 10sp) 3.42 offline / 3.91 transitional — also in dark.
Non-text 3:1: field borders on paper 1.48 (fail). Dark theme text pairs pass otherwise.

## 4. Accessibility / adaptivity

- Font scale 2.0 (ru): Home works but hero text overlaps the mascot, bottom bar hides the server card, battery card is huge;
  Settings readable (rows 2-3 lines), no clipping seen. Screens `s50`, `s51`, `s52`.
- Landscape: single centered column; the floating bottom bar covers the server card, only ~25% of the screen is content (`s53`). No landscape layout.
- Tablet (1600x2560@240): one ~500dp centered column, ~50% empty, no two-pane (`s54`). Acceptable, low priority.
- Touch targets (<48dp by dump bounds, approximate: Compose may enlarge the hit area): Settings switch 48x33, Servers row control 48x38, ru Settings text button 208x34. Re-check on device with TalkBack.
- `contentDescription = null` on 15 places (decorative icons/mascot — mostly legitimate; check `ServersScreen` 327–376, `SettingsScreen` 585). Hero state is not a live region (only the QR screen uses `liveRegion`).
- TalkBack was not run; only content-desc from dumps checked. Hardcoded "↓/↑" will be read literally.

## 4a. Localization

- en = ru = 467 string keys, 0 missing, 0 placeholder mismatch (`l10n-report.txt`).
- **No `<plurals>`**: ru "%d серверов" is wrong for 1/2-4 (strings `subs_card_meta`, `subs_nodes_skipped`, `home_per_app_*`); ru needs one/few/many.
- **Units not localized**: `core/common/Format.kt` hardcodes `KB/MB/GB`, `/s`, `Locale.US`; ru screen shows "0 B/s", "21.0 KB" next to "мс" (`s50`).
- `ui/common/Format.kt` `formatRelativeTime` returns English ("just now", "5 min ago", "never") — used by Connections and Subscriptions (visible in ru).
- 36 unused strings (list in `l10n-report.txt`), e.g. `afterglow_network`, `nav_home`.
- `locales_config.xml`: en, ru only. Date format is ISO `yyyy-MM-dd`.
- Diagnostics export header "VPN Client diagnostics" is hardcoded English (fine for an export, but see 8).

## 5. UX issues (severity: H blocks release / M user confusion / L polish)

| # | Sev | Screen | Problem |
|---|---|---|---|
| 1 | H | Settings > About | `AboutUrls.SOURCE_CODE` and `PRIVACY_POLICY` are empty strings -> no privacy policy link (Google Play requires one in app and listing) |
| 2 | H | first run | No onboarding / prominent disclosure before VPN consent; app opens straight to Home, VPN permission appears on first Connect (see 6) |
| 3 | H | Home | Offline hero is red like Error; Error and offline are nearly the same color (1.44) -> "off" reads as "broken" |
| 4 | H | Home | Hero says "Connected" while the notice below is titled "Connection failed" (selection of an unencrypted node while connected, `s16`); the failed choice stays selected after disconnect |
| 5 | M | Home | Error state shows "Connection failed" twice (hero + notice) (`s35`); empty state "No servers" 3 times |
| 6 | M | Home | First view: bottom bar + mascot cover the stats; battery card pushes content down; statistics are always expanded (`s13`,`s14`) |
| 7 | M | Subscriptions | Failed add (e.g. HWID-limit 404) creates an empty subscription titled with the host ("127.0.0.1"), host repeated under the title (`s25`,`s06`) |
| 8 | M | HWID dialog | Back silently closes both dialogs and adds nothing (`s05`); title is a question "What is HWID?"; Settings subtitle duplicates the link |
| 9 | M | Diagnostics | All health rows "Unverified/Expired" while Connected; jargon (TUN, egress, underlay, DNS bootstrap) (`s18`) |
| 10 | M | l10n | English units/relative time in ru; no plurals (4) |
| 11 | M | Settings | Flat unstructured list, no sections; battery card styled differently from rows; notification "Not granted / Allow" row |
| 12 | M | Servers/Home | "local sidecar", "Unencrypted", "urltest/HEAD", "TLS/SNI/ALPN/Reality", "outbound", "core", "Node", "every 1440 min" — jargon (rewrite list in 7) |
| 13 | L | Home | Stale-latency chip (>7 min), traffic-usage percent, last-known userinfo, health/DNS surface — backlog items from the owner prompt; not reproducible here **[code]** |
| 14 | L | Landscape | Bottom bar covers content (see 4) |
| 15 | L | all | 11sp caps coral section headers (contrast 3.77) |

Top-10 for the report: 1,2,3,4,5,6,7,8,10,9/12.

## 6. Onboarding and prominent disclosure (design, not implemented)

Shown once, before the system VPN consent dialog, with an explicit action ("Continue"); skippable only by leaving the app.
Do not use claims like "safe", "anonymous", "full protection". 4 screens:

1. **What the app does** — "Connects to servers you add yourself and routes your device's traffic through them. The app has no servers of its own." / «Подключается к серверам, которые вы добавляете сами, и направляет через них трафик устройства. Собственных серверов у приложения нет.»
2. **Your subscription** — "Add a subscription link or a QR code from your provider. Links are stored only on this device." / «Добавьте ссылку подписки или QR-код от провайдера. Ссылки хранятся только на этом устройстве.»
3. **VPN permission (disclosure)** — "To route traffic, Android asks you to allow a VPN connection. While it is on, the traffic of selected apps goes through the server you chose, and that server's operator can see where it goes. The app does not collect or sell your traffic." (verify against SECURITY.md before use) / «Чтобы направлять трафик, Android попросит разрешить VPN-подключение. Пока оно включено, трафик выбранных приложений идёт через выбранный сервер, и его оператор видит, куда он идёт. Приложение не собирает и не продаёт ваш трафик.»
4. **Optional** — notifications (explain status notification) and HWID (what is sent, when, off by default? **owner decision**).

Play review video (<=90s) checklist: icon + name -> disclosure screen -> tap Continue -> system VPN dialog -> Connected state with
key icon in status bar -> notification -> Disconnect. Cover privacy-policy link in About and in listing.

## 7. Wording rewrite (jargon -> plain)

local sidecar -> "Local test server (on this device)"; Unencrypted -> "No encryption (not recommended)"; Node -> "Server";
outbound/underlay -> "network"; core/libbox -> "VPN engine" (Diagnostics only); "every 1440 min" -> "every 24 h" (formatted);
"Interval override (minutes, min 15)" -> "Update every (minutes, at least 15)"; HWID dialog title -> "Send device ID?" with
"Send" / "Don't send" and Back = "Don't send" (explicit). Keep TLS/SNI/ALPN/Reality only under "Technical details".

## 8. Product name (single point of truth)

- `app_name = "VPN Client"` (en and ru) -> manifest application/activity/tile labels and notification texts.
- Wordmark `"RUNE"` is **hardcoded** in `AfterglowHome.kt:123`; `afterglow_network` ("RUNE / NETWORK") unused.
- `DiagnosticExport.kt:9` hardcodes "VPN Client".
- Result: launcher says "VPN Client", Home says "RUNE". **Owner decision: final name.** Then: `app_name` string only, wordmark
  from `stringResource` (`brand_wordmark`, `translatable=false`), export header from `app_name`. `applicationId` stays
  `dev.typenil.vpnclient`; deeplink schemes `sing-box://`, `clash://` are protocol names, not branding.

## 9. Assets

- Launcher: adaptive icon with **no `<monochrome>` layer** (no themed icon on Android 13+). Foreground is an opaque PNG with a baked
  `#1E1D1F` background, content bbox ~72dp of 108dp > 66dp safe circle -> head can be cropped by circle masks (`icon-fg-safezone.png`).
- Mascot: `afterglow_mascot` 1254x1254 RGB (no alpha, baked dark side), 4 `companion_*` RGBA states OK. QS tile vector OK.
- Missing for Google Play: 512x512 icon, 1024x500 feature graphic, >=2 phone screenshots (none in repo).
- **Owner decision (neutral flag):** the mascot/icon carries a circled "A" emblem (visible in the hero on every state). Check how store
  reviewers and users may read it, and confirm authorship/licence of the artwork (Play IP/originality policies).
- Do not use the mascot as a "protection" metaphor in store texts.

## 10. Work split (proposal)

| WP | Scope | Packages / files | Depends | Est. lines | Risks |
|---|---|---|---|---|---|
| 11b | Tokens + semantic state colors (light/dark), type scale, remove template colors, contrast fixes | `ui/theme/*`, `res/values/colors.xml`, hero/notice usages | – | 300–500 | Visual regressions in all screens; needs screenshot compare |
| 11c | l10n: plurals, localized units/relative time, unused strings, wording rewrite (7) | `core/common/Format.kt`, `ui/common/Format.kt`, `values*/strings.xml` | – (parallel with 11b) | 300–500 | Format tests; ru plural forms; 21 `formatBytes/formatRate` callers |
| 11d | Home layout: bottom inset/scroll, collapsible stats, dedupe texts, state notice fixes (4,5,6), landscape | `ui/home/*` | 11b | 300–600 | Largest file; keep ConnectionManager state as the only source |
| 11e | Settings sections, battery card, HWID dialog (Back = explicit), subscription failed-add behavior, About links | `ui/settings/*`, `ui/subscriptions/*` | 11c | 300–500 | Behavior change in add flow (last-known-good rule); privacy URL needs owner |
| 11f | Onboarding + disclosure + permission order | new `ui/onboarding/*`, `VpnApp.kt`, DataStore flag | 11b, 11c, name decision | 400–600 | Must not break Tile/Boot/auto-start paths; needs on-device test |
| 11g | Branding + assets: name single point, monochrome icon, safe-zone foreground, Play assets | `res/mipmap-*`, manifest, `AfterglowHome.kt:123`, `DiagnosticExport.kt` | name/art decisions | 150–300 + assets | Depends on owner artwork |
| 11h | a11y: 48dp targets, live-region hero, contentDescription review, TalkBack pass | all screens | 11d, 11e | 150–300 | Needs device TalkBack run |

Order: 11b ‖ 11c -> 11d -> 11e -> 11f -> 11g -> 11h. Each slice: build + unit tests + lint + on-device screenshots.

## 11. Owner decisions needed

1. Final product name (VPN Client vs RUNE vs other).
2. Privacy policy URL and source-code URL (empty now).
3. The circled "A" emblem and artwork rights/originality.
4. HWID: default on/off, wording, whether it belongs in onboarding.
5. Failed add: keep an empty subscription or discard it?
6. Is onboarding skippable / shown again after "Clear data"?
7. Disconnected hero: neutral (proposed) or keep red?

## 12. Not verified

- TalkBack behavior, real touch-target sizes, themed-icon behavior, white flash in dark cold start.
- Connecting/Reconnecting/Preparing visuals, stale chip, health "unreachable", notification permission flow, QR, tile, notifications.
- Physical device not used; all captures are emulator only (software rendering, fonts may differ).
- The plan section "Бэклог после WP-8" is not in the repo; backlog items above come from the owner prompt and observation.
