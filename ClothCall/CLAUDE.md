"ClothCall uses Llama 4 Scout, Meta's open source multimodal model, running on Groq's inference infrastructure. Llama 4 Scout is a vision-language model — it can receive both images and text as input and reason about what it sees. We send it the clothing photo along with a carefully designed prompt that instructs it to describe condition in passive voice, reference the caregiver by name, and calibrate its judgment to their personal tolerance threshold."

# ClothCall

Android app that checks clothing condition before the user wears it. A photo is taken, sent to Groq (LLaMA 4 Scout), and the AI response is delivered as a simulated incoming phone call from a named "trusted person" (caregiver).

## What it does

1. User opens the app and taps **Check My Clothes**.
2. Camera opens → user takes a photo of what they're about to wear.
3. The photo (plus optional baseline) is sent to Groq with a system prompt tuned for clothing condition.
4. Result arrives as a simulated incoming call: ringtone + vibration, answer/decline buttons.
5. On answer, TTS reads the AI response aloud.
6. User responds by voice: **yes / no / repeat / more detail / already know**.
7. "More detail" fires a multi-turn follow-up to Groq with the same image(s).

**Home/Out toggle** on the home screen switches audio routing:
- *Home* — in-app speakerphone (`MODE_NORMAL`).
- *Out* — uses Android Telecom self-managed calls; audio routes through the earpiece (`MODE_IN_CALL`) and the system lock screen shows an incoming call UI.

## Two scan modes

### Stain-only (no garment selected)
Single image sent. Uses `STAIN_ONLY_PROMPT` as system prompt. Looks for stains, marks, discoloration, and damage. No fading comparison possible.

### Fading + stain comparison (garment selected)
Two images sent — the wardrobe baseline first, today's scan second. Uses `SYSTEM_PROMPT`. Groq compares the two for fading, stains, and condition changes, calibrated to the caregiver's `fadeThreshold` percentage.

The garment dropdown on the Home screen controls which mode runs. Selecting "None" reverts to stain-only.

## Architecture

- **Language:** Kotlin
- **UI:** Jetpack Compose (Material 3)
- **Navigation:** Navigation Compose (`AppNavigation.kt`)
- **Database:** Room (single `AppDatabase` with two tables)
- **Network:** OkHttp (no Retrofit), direct JSON construction with `org.json`
- **AI:** Groq REST API — model `meta-llama/llama-4-scout-17b-16e-instruct` (`GeminiApiService.kt` — class name kept for import compatibility)
- **Telecom:** Android Telecom self-managed `PhoneAccount` + `ConnectionService`
- **Camera:** CameraX `ImageCapture`
- **Voice in:** `SpeechRecognizer` (Android STT)
- **Voice out:** `TextToSpeech` (Android TTS)
- **Audio routing:** `AudioRouter.kt` — called by `CallViewModel` on phase transitions

## Project layout

```
app/src/main/java/com/clothcall/
├── api/
│   ├── GeminiApiService.kt        # Groq REST calls: analyzeClothing + requestMoreDetail
│   └── ClaudeApiService.kt        # deprecated typealias → GeminiApiService (do not touch)
├── data/db/
│   ├── AppDatabase.kt             # Room singleton
│   ├── Garment.kt / GarmentDao.kt
│   └── CaregiverProfile.kt / CaregiverProfileDao.kt
├── telecom/
│   ├── TelecomHelper.kt           # register account, start/end system calls
│   └── ClothCallConnectionService.kt
├── ui/
│   ├── navigation/AppNavigation.kt
│   ├── screens/
│   │   ├── HomeScreen.kt          # profile + garment dropdowns, Home/Out toggle
│   │   ├── ApiKeySetupScreen.kt   # Groq API key entry
│   │   ├── QuickScanScreen.kt     # camera capture → ScanViewModel.analyze()
│   │   ├── CallUIScreen.kt        # ringing → speaking → listening → dismissed
│   │   ├── CaregiverSetupScreen.kt
│   │   ├── WardrobeScreen.kt
│   │   ├── MorningSpinScreen.kt   # multi-frame slow-spin capture (3 frames, 2 s apart)
│   │   └── SharedComposables.kt   # LoadingOverlay, ErrorOverlay, PermissionDeniedScreen
│   ├── viewmodels/
│   │   ├── ScanViewModel.kt       # image → base64 → baseline load → Groq → ScanResultHolder
│   │   ├── CallViewModel.kt       # CallPhase state machine + AudioRouter + STT retry key
│   │   ├── HomeViewModel.kt       # profiles + garments + selectedGarmentId
│   │   ├── WardrobeViewModel.kt
│   │   └── CaregiverViewModel.kt
│   └── theme/
│       ├── Color.kt / Theme.kt / Type.kt
└── utils/
    ├── PreferencesManager.kt      # SharedPreferences: apiKey, isOutMode, selectedGarmentId
    ├── ScanResultHolder.kt        # in-memory singleton bridge scan→call
    └── AudioRouter.kt             # AudioManager mode switching (earpiece / speaker / reset)
```

## Data flow: scan to call

```
HomeScreen
  → user selects garment from dropdown (sets prefs.selectedGarmentId)
  → user taps "Check My Clothes"

QuickScanScreen
  → imageProxyToBitmap()
  → ScanViewModel.analyze(bitmap)
      → bitmap compressed to JPEG, base64-encoded → ScanResultHolder.base64Image
      → loadBaselineBase64()
          → prefs.selectedGarmentId >= 0?
              yes → garmentDao.getGarmentById(id) → load File → base64
                  → ScanResultHolder.baselineBase64 = baseline
              no  → ScanResultHolder.baselineBase64 = null
      → caregiverDao.getActiveProfile() → caregiverName, fadeThreshold
      → GeminiApiService.analyzeClothing(
            base64Image, baselineBase64?, caregiverName, fadeThreshold)
          → baselineBase64 != null → userMessageWithTwoImages + SYSTEM_PROMPT
          → baselineBase64 == null → userMessageWithImage + STAIN_ONLY_PROMPT
      → ScanResultHolder.response = text → ScanState.Done
  → navigate to Route.CALL_UI
      (isOutMode=true → TelecomHelper.startIncomingCall() first)

CallUIScreen
  → CallViewModel.reset()              → CallPhase.Ringing
  → user taps Answer → .answer()
      → AudioRouter.routeToEarpiece()  (Out) or .routeToSpeaker() (Home)
      → CallPhase.Speaking
  → TTS reads ScanResultHolder.response
  → TTS done → .onTtsDone()            → CallPhase.Listening
  → LaunchedEffect(phase, listeningKey) → startListening()
  → STT result → .handleVoiceCommand()
      "yes"/"no"      → CallPhase.Dismissed(warm=…)
      "repeat"        → transitionToSpeaking()
      "more"/"detail" → fetchMoreDetail()
                         → GeminiApiService.requestMoreDetail(base64, baseline?, …)
                         → transitionToSpeaking() with new text
      "already"       → Dismissed(warm=true)
      blank/unknown   → retryListening() → listeningKey++ → STT restarts
  → Dismissed → TTS "Enjoy your day." (warm=true) → navigate Home
  → onCleared → AudioRouter.resetRouting()
```

## Key design decisions

### ScanResultHolder singleton
`ScanResultHolder` is an in-memory `object` bridging `ScanViewModel` and `CallViewModel`. Fields: `base64Image`, `baselineBase64`, `response`, `caregiverName`, `fadeThreshold`, `conversationHistory`. Reset at the start of each new scan via `reset()`. Do not make it dependency-injected — the current design is intentionally lightweight.

### Baseline image flow
`prefs.selectedGarmentId` (Int, default -1) stores which wardrobe item is being worn. `ScanViewModel.loadBaselineBase64()` reads `garmentDao.getGarmentById(id)`, loads the file from disk, and converts it to base64. If the file is missing or no garment is selected, `baselineBase64` is `null` and the stain-only path runs. The selection persists across sessions (stored in SharedPreferences) and is changed via the garment dropdown on HomeScreen.

### STT retry — listeningKey
`CallViewModel` holds `_listeningKey: MutableStateFlow<Int>`. `retryListening()` increments it when already in `CallPhase.Listening`, instead of re-setting the same phase value (which would not trigger `StateFlow` emission). `CallUIScreen` uses `LaunchedEffect(phase, listeningKey)` so STT restarts on every retry. STT blank results and errors call `retryListening()` directly — they never reach `handleVoiceCommand`.

### Caregiver profile calibration
`CaregiverSetupScreen` shows shirt and jeans silhouettes (drawn via Canvas) at five fade levels (0%, 5%, 10%, 20%, 30%). The trusted person rates each as *Still fine / Borderline / Retire*. `computeOverallThreshold()` averages both and stores a single `Int` on `CaregiverProfile.fadeThreshold`. Passed into the Groq prompt so the AI calibrates language to that person's tolerance.

### Prompt rules (both prompts)
- Passive voice throughout
- Location-specific stain descriptions
- Soft comparative language for fading
- Never: "you should", "you must", "change your shirt"
- Caregiver referenced by name when provided
- Always ends with exactly: **Do you still want to wear it?**

Do not loosen these constraints — they exist to protect user autonomy and dignity.

### Audio routing
`AudioRouter` is created inside `CallViewModel.factory()` using `APPLICATION_KEY` app context. `transitionToSpeaking()` calls `routeToEarpiece()` (Out mode → `MODE_IN_CALL`) or `routeToSpeaker()` (Home mode → `MODE_NORMAL` + speakerphone). `onCleared()` calls `resetRouting()`. The `CallUIScreen` `DisposableEffect` also resets audio on dispose — the two resets are idempotent.

### Telecom self-managed call
`TelecomHelper.startIncomingCall()` calls `TelecomManager.addNewIncomingCall()`. Creates a real system call visible on the lock screen. If the device blocks self-managed calls, the flow degrades silently to in-app audio — never crash.

### `GeminiApiService` naming
The class is named `GeminiApiService` and the file kept as `GeminiApiService.kt` for import compatibility. The implementation calls Groq, not Gemini. `ClaudeApiService.kt` is a deprecated `typealias` pointing here — do not add logic to it.

## Groq request format

**Endpoint:** `https://api.groq.com/openai/v1/chat/completions`  
**Model:** `meta-llama/llama-4-scout-17b-16e-instruct`  
**Auth:** `Authorization: Bearer <gsk_...>`

```json
{
  "model": "meta-llama/llama-4-scout-17b-16e-instruct",
  "messages": [
    { "role": "system", "content": "<SYSTEM_PROMPT or STAIN_ONLY_PROMPT>" },
    {
      "role": "user",
      "content": [
        { "type": "image_url", "image_url": { "url": "data:image/jpeg;base64,<baseline>" } },
        { "type": "image_url", "image_url": { "url": "data:image/jpeg;base64,<current>" } },
        { "type": "text",      "text": "<user prompt with caregiver name + threshold>" }
      ]
    }
  ],
  "max_tokens": 1024
}
```

Single-image (stain-only) omits the first `image_url` part.  
Multi-turn (`requestMoreDetail`) appends an `assistant` message then a new `user` text message.  
Response parsed at: `choices[0].message.content`

## Build

```bash
./gradlew assembleDebug
./gradlew installDebug
```

Requires a **Groq API key** (`gsk_...`) from console.groq.com. Entered at first launch, stored in SharedPreferences (not exported, not in source). Clear app data to re-enter: `adb shell pm clear com.clothcall`.

## Permissions required

| Permission | Used for |
|---|---|
| `CAMERA` | Photo capture in QuickScan + Wardrobe + MorningSpin |
| `RECORD_AUDIO` | STT in CallUI and WardrobeScreen name input |
| `INTERNET` | Groq REST API calls |
| `MODIFY_AUDIO_SETTINGS` | Speakerphone / earpiece routing |
| `VIBRATE` | Ringing vibration pattern in CallUI |
| `MANAGE_OWN_CALLS` | Telecom self-managed PhoneAccount registration |

## What does not exist yet

- No tests (unit or instrumented).
- `ScanResultHolder.conversationHistory` is populated but never read — multi-turn context beyond the first follow-up is not implemented.
- No UI to select which garment is being worn from within the scan flow itself — selection is done on HomeScreen before scanning.
- Wardrobe baseline comparison quality depends on lighting/angle consistency between the saved photo and the scan photo — no guidance is given to the user about this.
- `MorningSpinScreen` uses only the last of 3 captured frames for analysis — multi-frame averaging or best-frame selection is not implemented.
