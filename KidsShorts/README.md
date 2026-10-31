# KidsShorts

Android app (Kotlin, Jetpack Compose, Room) that plays YouTube Shorts-style videos **only** from parent-approved channels and
**only** in parent-approved languages. Fail-closed: an empty feed is always preferred to relaxing a rule.

- `domain/`  pure Kotlin rules (eligibility, language, screen time, PIN throttle, parsers) + unit tests
- `app/`     Android app (UI, Room, YouTube Data API client, IFrame player host, Keystore PIN vault)
- `docs/SETUP.md`                  API key, quota, build, install
- `docs/POLICY_AND_LIMITATIONS.md` supported vs best-effort vs unsupported, and legal review items
- `docs/VERIFICATION.md`           exactly what was and was not verified when this was written

Start with `docs/SETUP.md`. **This project has never been built with Gradle** (see VERIFICATION.md): expect to fix a few
compile errors in the Compose layer on first build.

Non-negotiable rule, enforced in one place (`EligibilityEvaluator.evaluateVideo`): a video is playable only if its channel is
approved and enabled, it is available/embeddable with a known Made-for-Kids status, the parent hasn't blocked it, and its
language is verified by YouTube audio-language metadata or confirmed by the parent for that exact video AND exactly matches an
allowed language.
