# Firebase Setup (one-time, free)

The app uses the **Firebase Spark (free) plan**. Everything below is free and takes ~5 minutes.

> `app/google-services.json` in this repo is a **placeholder** so the build compiles offline.
> The app still builds without it, but multiplayer will not work until you replace it.

## Steps

1. Go to https://console.firebase.google.com and click **Create a project** (Spark plan — free).
2. Add an Android app:
   - Package name **`com.nbarumble.game`**
   - Download the generated **`google-services.json`**.
3. Replace `app/google-services.json` with the downloaded file.
4. Open the project in the Firebase console:
   - **Build > Authentication** → *Get started* → enable **Anonymous** sign-in.
5. **Build > Realtime Database** → *Create database*:
   - Pick a region (e.g. `europe-west1`)
   - Start in **test mode** for quick testing.
6. Open the **Rules** tab and paste:

```json
{
  "rules": {
    ".read": "auth != null",
    ".write": "auth != null"
  }
}
```

(Playing by authenticated users only — anonymous auth counts. For public LAN parties you can
relax to `true`, but authenticated rules are already enough for 1v1 rooms.)

7. Build & run. Home screen will confirm "Firebase connected: YES".

### How the app protects the clock

- All clock mutations happen via Firebase **transactions** (`runTransaction`) — atomic.
- Clocks are anchored to Firebase's server time through the `.info/serverTimeOffset` node, so
  both devices count down against the same reference — no "my watch is faster" complaints.
- Only the active player's clock runs locally; on turn switch the elapsed delta is written once.