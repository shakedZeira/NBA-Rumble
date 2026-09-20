# NBA Rumble — Progress

## What is done

- **Android app** (Compose + Firebase Realtime Database), package `com.example.baskettop`, namespace `com.nbarumble.game`.
- **Speech guess game**: player says a name, FuzzyMatcher matches it (with pronunciation aliases for hard/foreign names), 8 attempts, "hint" and "fast" modes.
- **Difficulty levels**: EASY (tier 1 names only), MEDIUM (tier 1–2), HARD (tier 1–3).
- **Single-player mode** (`ui/single`): pick a league (NBA / EuroLeague), pick difficulty and registration order, play against the clock (5sec/correct answer), best scores saved and shown live.
- **Leaderboard** (`ui/leaderboard` + `data/repo/LeaderboardRepository`): Firebase RTDB path `/leaderboard`, entry = {nickname, score, role, timestamp}; top-100 table with rank, highlight of the local player, "send result" flow, pull-to-refresh style reload.
- **Home screen** buttons: JOIN (multiplayer), SINGLE PLAYER, LEADERBOARD, plus rules/settings info and server status.

## Roster

- `app/src/main/assets/nba_players.json` — merged from the original roster + Firebase DBs + verified crawl.
- **459 players total: 405 NBA + 54 EuroLeague.**
  - 131 original legacy players (no league field → treated as NBA).
  - 27 NBA players from Firebase DB (photo URLs from Firebase Storage, real names).
  - 54 EuroLeague players from Firebase DB (photos from Firebase Storage).
  - 247 extra NBA players added with **verified real NBA.com IDs** (from Wikidata property P3647 = NBA.com player ID, cross-checked via the player's own Wikipedia extlinks and confirmed against nba.com player pages).
- Tier distribution: tier 1 = 71, tier 2 = 57, tier 3 = 331.
- New DB-sourced players use `"id": "0"` + `photo` (Firebase Storage URL) — `displayUrl` prefers `photo` over the CDN so they render fine.
- EuroLeague players are tier-tagged from the DB level (tier 3 default when level 3–5).

## Verification

- `.\gradlew.bat :app:testDebugUnitTest` passes: 16 unit tests (fuzzy matcher, clock engine, player repository).
- `.\gradlew.bat :app:assembleDebug` builds the APK cleanly.
- APK copied to project root: `NBA-Rumble-400plus-merged.apk`.

## How the roster was filled (repro steps)

1. Firebase DBs (`fetch_firebase2.ps1` + `merge_roster.ps1`): each league exposes 5 levels, each level is a JSON **array** of `{name, imageUrl}`.
2. Wikidata dump (`fetch_wikidata.ps1`): `wdt:P3647 ?nbaId` SPARQL → ~5400 players with verified NBA.com IDs.
3. Match a curated list of recognizable names against the Wikidata IDs; cross-check each via `https://www.nba.com/player/{id}` og:title (CDN `cdn.nba.com` is blocked from the dev network, so `HEAD` checks are not usable; nba.com pages are the workaround).

## Next steps / notes

- Commit the current batch (single-player + leaderboard + 459-player roster) and push to GitHub.
- The ~5400-player Wikidata file lives at `%TEMP%\opencode\wikidata_nba.json` if more top-up is ever needed.
- `stats.nba.com` and `cdn.nba.com/static/json` are 403-blocked for the dev machine; don't rely on them for future work.