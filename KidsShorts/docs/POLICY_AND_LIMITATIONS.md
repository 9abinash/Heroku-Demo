# What works, what is best-effort, what YouTube/Android do not allow

## A. Officially supported (works as designed)
- Resolve channels with `channels.list` (`id`, `forHandle`, `forUsername`) and show name, thumbnail and channel ID before approval.
- List a channel's uploads (`playlistItems.list` on the uploads playlist) and read per-video metadata (`videos.list`:
  `snippet`, `contentDetails`, `status`) with an API key.
- Play videos through the **official YouTube IFrame player** (privacy-enhanced `youtube-nocookie.com` host) in a WebView.
  The Android Player API no longer exists, so the IFrame player is the supported route. The page is loaded with
  `loadDataWithBaseURL(https://<applicationId>, …)` so a Referer identifies the app; without it YouTube returns player error 153.
- Nothing is ever drawn over YouTube's controls or branding. Our controls, channel name and language label sit **above/below** the player.
- Channel allow-listing, language allow-listing, per-video decisions, PIN, screen-time limits, quiet hours, overrides, audit log, data reset.
- Fail-closed eligibility: one function (`EligibilityEvaluator.evaluateVideo`) decides; the feed cannot be built from anything else
  (`EligibilityResult.Eligible` has an `internal` constructor in the `:domain` module).

## B. Best-effort (metadata-based; can be wrong)
| Topic | What we do | Limitation |
|---|---|---|
| Spoken language | `snippet.defaultAudioLanguage` verifies a language. `snippet.defaultLanguage` (title/description language) is used **only to detect conflicts**. | These fields are creator-supplied and frequently missing or wrong. Missing ⇒ *Unknown* ⇒ blocked. Conflict ⇒ *Conflicting* ⇒ blocked. The parent's per-video confirmation is the strongest control. |
| Title/description language | Deliberately **not** analysed. | Inferring spoken language from text would violate the "evidence, not proof" rule. |
| Shorts detection | Duration 1–180 s (Shorts can be up to 3 min since 15 Oct 2024). | The Data API has no Shorts flag and exposes no aspect ratio. Short *landscape* clips can pass; this is a format heuristic, not a language/safety rule. |
| Region restrictions / removed videos | Missing-from-API ⇒ unavailable. Player errors 100/101/150/152 mark that video unavailable for this device and the feed moves on. | Region availability is only discovered when the player tries to play it. A sync clears these flags once for a retry. |
| Foreign-video guard | The page compares the playing video ID with the one we requested (every 750 ms and on state changes) and stops/replaces any mismatch; on `ENDED` we re-cue the same video so end-screen suggestions don't appear. | Defence in depth, not a guarantee: there is a short grace window (2.5 s) after each load, and a suggestion could play for up to ~1 s before the guard fires. The player's own UI is deliberately left untouched. |
| PIN / screen-time | Salted PBKDF2 hash inside a Keystore-encrypted record; persisted throttling; usage stored in the DB and measured with a monotonic clock. | Not tamper-proof: clearing app data, uninstalling, changing the date to reach a new day, a rooted device, or switching apps all bypass it. Use alongside Android parental controls (e.g. Family Link). |

## C. Not supported (and why)
- **Real spoken-language detection.** The official API doesn't provide it, and downloading/processing YouTube audio would violate the API terms.
- **Caption languages.** `captions.list` requires OAuth 2.0 authorisation, so it can't be used with an API key. `captionLanguages` was therefore removed from the data model.
- **A reliable Shorts flag**, a vertical-only check, or channel-wide "approved languages" (a manual approval is always per video ID).
- **Hiding/removing YouTube branding, controls, ads or required UI; downloading or re-hosting video.**
- **Kiosk/lock-task mode.** By design there is a visible exit to a PIN screen and no deceptive lock-in; the Home button always works. Preventing the child from opening other apps needs OS-level controls.
- **Restricting to Made-for-Kids content** is not implemented (it would shrink the catalogue a lot); `status.madeForKids` is fetched, re-checked, and required to be known before embedding.

## D. Known tension in the specification (decision for you)
The brief asks for an optional **"allow videos whose language is unknown"** toggle *and* a non-negotiable rule that a child never sees a
video unless its language is known or parent-confirmed. These conflict. The toggle exists, is **OFF by default**, shows a red warning when on,
never admits *Conflicting* videos, never works when no language is allowed, and is recorded in the activity log. If you want the strict reading,
delete the switch in `LanguageScreen.kt` and the `blockUnknownLanguage` branch in `EligibilityEvaluator.kt` (one `when` arm); tests will tell you what to update.

## E. Items that need legal / policy review before any public release
1. **Child privacy law:** COPPA (US), GDPR-K / UK Children's Code, India's Digital Personal Data Protection Act. Using the YouTube API does **not** make an app compliant.
2. **YouTube API Services Terms & Developer Policies:** public distribution needs a public privacy policy, visible links to YouTube's Terms and Google's Privacy Policy
   (the Data & privacy screen links them), a compliance audit/quota extension, and correct handling of `status.madeForKids` (policy III.E.4.j) — confirm our handling
   (nocookie host, cookies off, no sign-in, first video only *cued* until the child acts) satisfies your counsel's reading, particularly for non-Made-for-Kids videos.
3. **Google Play:** Families Policy / target-audience declaration, Data safety form, and any rules about embedded third-party web content in apps for children.
4. **Trademarks/branding:** the app must not imply YouTube endorsement; keep YouTube's branding visible in the player.
5. **API key strategy:** a shared key baked into a public APK is not allowed; either keep per-family keys (current design) or add a backend that holds a server-side key (this introduces data-processing obligations).
6. The exact `Referer` format and player parameters should be re-checked against YouTube's *current* embedded-player documentation at release time; they have changed before (error 153 appeared in 2025).
