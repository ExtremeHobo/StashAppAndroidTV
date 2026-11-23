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

## Install / update on the Android box (NVIDIA Shield)

Stock builds from `damontecres/StashAppAndroidTV` use a different signing key than this fork, so **one uninstall** is required the first time:

1. Download the latest `presence-unlock` pre-release APK from  
   https://github.com/ExtremeHobo/StashAppAndroidTV/releases/tag/presence-unlock  
   Prefer `arm64-v8a` (or the universal APK).
2. Sideload (ADB example):

   ```bash
   adb connect <shield-ip>
   adb uninstall com.github.damontecres.stashapp
   adb install -r StashAppAndroidTV-*-arm64-v8a.apk
   ```

   Or use Downloader / file manager on the Shield.
3. Open the app, re-enter Stash server URL + API key + PIN.
4. Enable Presence PIN unlock and paste the HA token.
5. (Optional) Point **Update URL** at  
   `https://api.github.com/repos/ExtremeHobo/StashAppAndroidTV/releases/tags/presence-unlock`  
   so in-app updates track this fork.

### Stable signing for future updates

Set GitHub Actions secrets on `ExtremeHobo/StashAppAndroidTV`:

- `PERSONAL_KEYSTORE_BASE64` — `base64 -w0 personal.keystore`
- `PERSONAL_KEYSTORE_PASSWORD` / `PERSONAL_KEY_PASSWORD` / `PERSONAL_KEY_ALIAS`

Without those secrets, CI generates an ephemeral keystore each run (you would need to uninstall again between builds).

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
