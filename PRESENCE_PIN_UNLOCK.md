# Presence PIN unlock

Skip the Stash Android TV app PIN when:

1. **Sam is home** (`person.sam_davis` state = `home`)
2. **Meghan is away** (`person.meghan` state ≠ `home`)
3. **Guest mode is off** (`input_boolean.guest_mode` = `off`)

Otherwise the PIN is still required. If Home Assistant is unreachable, the PIN is required (fail closed).

## Signals (already in Home Assistant)

| Role | Default entity | Notes |
|------|----------------|-------|
| Sam home | `person.sam_davis` | Zone/person presence |
| Meghan away | `person.meghan` | Away may be `not_home` or a zone name (e.g. `Meghan Work`) |
| Guest mode | `input_boolean.guest_mode` | Existing Guest Mode toggle |

Entity IDs are configurable in app Preferences.

## App settings

1. Preferences → enable **Presence PIN unlock**
2. **Home Assistant URL** — e.g. `http://192.168.1.23:8123` (IP is more reliable on Android TV than `.local`)
3. **Home Assistant token** — create a long-lived access token in HA (Profile → Security). Stored only on the TV; never committed to git.
4. Confirm the three entity IDs (defaults match the table above)

## Install / update on the Android box

**Device in use:** Sam's Office Onn Box (`192.168.1.171`, ABI `armeabi-v7a`).

Stock builds from `damontecres/StashAppAndroidTV` use a different signing key than this fork, so **one uninstall** was required the first time.

### In-app updates (like stock)

This fork’s default Update URL is:

`https://api.github.com/repos/ExtremeHobo/StashAppAndroidTV/releases/latest`

CI publishes a normal (non-prerelease) release whenever `main` builds, with:

- Release **name** = git describe version (e.g. `v0.9.1-3-gabcdef`) so the app can compare versions
- Assets named `StashAppAndroidTV.apk` / `StashAppAndroidTV-release.apk` plus ABI copies (`-armeabi-v7a`, `-arm64-v8a`) so the Onn box gets a matching APK

Daily (and on demand) **Sync upstream** merges `damontecres/StashAppAndroidTV` into this fork’s `main`, which triggers a new build/release. When that’s newer than what’s installed, the app prompts to install like before.

### Manual sideload

1. Download from https://github.com/ExtremeHobo/StashAppAndroidTV/releases/latest  
   Prefer `StashAppAndroidTV-armeabi-v7a.apk` (Onn) or the universal `StashAppAndroidTV.apk`.
2. `adb connect 192.168.1.171:5555` then `adb install -r <apk>`
3. Preferences → Presence PIN unlock → HA URL `http://192.168.1.23:8123` + long-lived token

### Stable signing

GitHub Actions secrets (already set for this fork):

- `PERSONAL_KEYSTORE_BASE64` / `PERSONAL_KEYSTORE_PASSWORD` / `PERSONAL_KEY_PASSWORD` / `PERSONAL_KEY_ALIAS`

Same keystore ⇒ in-app updates install over the existing app without uninstalling.

## Optional HA template (single sensor)

If you prefer one entity for the app to read later, add a template binary sensor:

```yaml
template:
  - binary_sensor:
      - name: Stash TV PIN unlock
        unique_id: stash_tv_pin_unlock
        state: >
          {{ is_state('person.sam_davis', 'home')
             and not is_state('person.meghan', 'home')
             and is_state('input_boolean.guest_mode', 'off') }}
```

The current app build evaluates the three entities directly (no HA YAML required).

## PIN / DataStore note (2026-11-24)

Compose setup (`InitialSetup`) and Preferences write PIN into Proto DataStore.
The PIN gate historically read only SharedPreferences, so a PIN set during setup
could leave SharedPreferences empty and **skip the PIN screen entirely** � including
when Meghan is home. Fixed builds dual-write and prefer DataStore via `AppPin`.
Presence rule also fail-closes on blank/unknown HA states.
