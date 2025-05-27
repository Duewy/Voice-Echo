# Developer Notes for BassAnglerTracker Voice Control

## 🎧 Bluetooth Play/Pause Button Integration

To allow hands-free voice activation using a Bluetooth headset:

- We register a `MediaSessionCompat` with `FLAG_HANDLES_MEDIA_BUTTONS`
- We request audio focus and play a silent audio file (`silence_0_1s`)
- This ensures Android sees our app as the "last media app"
- Result: when the user taps the Play/Pause button, Android routes the event to our app (not Spotify or YouTube)

This workaround is needed because Android normally sends media button events to whichever media app was last active.

> Suppressed deprecation warning on `requestAudioFocus()` for SDK < 26 using `@file:Suppress("DEPRECATION")`

---

## 🗣️ Fun Day vs Tournament Parsing

- **Tournament Mode** requires:
  - Species from a pre-selected list
  - Clip color
- **Fun Day Mode**:
  - Ignores clip color
  - Accepts any recognized species (via `VoiceInputMapper.baseSpeciesVoiceMap`)

Parsers are split into two groups:
- `parseImperialCatchWithClips()` vs `parseImperialCatchSimple()`
- See `VoiceParser.kt` for full breakdown

---

## 🧠 VoiceControlService Wake Flow

- Wake event triggered via media button or broadcast
- Calls `onWake()` which:
  - Loads `VoiceUiHelper`
  - Routes to either `TournamentVoiceHandler` or `FunDayVoiceHandler`
  - Uses `SharedPreferencesManager.getCatchEntryType()` to determine mode
