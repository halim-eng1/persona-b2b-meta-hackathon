<div align="center">

<img src="docs/persona_neural_icon.svg" alt="Persona B2B Logo" width="120" height="120" />

# Persona B2B: Smart Auto Reply

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Platform-Android_14_%28API_34%2B%29-green.svg?logo=android)](https://developer.android.com)
[![Compose](https://img.shields.io/badge/UI-Jetpack_Compose_Material_3-4285F4.svg?logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Meta Model API](https://img.shields.io/badge/AI_Runtime-100%25_Meta_Model_API-0668E1.svg?logo=meta)](https://dev.meta.ai)
[![Tests](https://img.shields.io/badge/Unit_Tests-640%2B_Passing-success.svg)](#%EF%B8%8F-build--installation-guide)
[![License: AGPL v3](https://img.shields.io/badge/License-AGPL_v3-blue.svg)](https://www.gnu.org/licenses/agpl-3.0)

</div>

> **Persona B2B** is an autonomous, multimodal (Text, Voice Notes, & Images), bilingual (Arabic/English) AI communication and stateful commerce engine for Android. Engineered exclusively on the official **Meta Model API** (`Muse Spark 1.3`, `Muse Spark 1.2`, and `Muse Voice Transcribe 1.0`), it empowers small and medium businesses (SMBs) as a full-cycle sales & order agent—or operates as an isolated **Personal Executive Assistant**—with zero third-party AI model dependencies at runtime.

---

## 🏗️ Production System Architecture (Autonomous Background Engine)

The core strength of **Persona B2B** lies in its autonomous background execution on Android. It operates as an unattended, privacy-first daemon that listens to real customer chats, extracts multimodal context, executes deterministic RAG retrieval, reasons with Meta's official LLMs, and updates stateful order databases—all without needing user supervision.

```mermaid
flowchart TD
    subgraph Inbound["1. Inbound Production Channels"]
        A["Android NotificationListenerService<br/>(WhatsApp & WhatsApp Business)"]
        WL["Background WakeLock & Coroutine Worker"]
        A --> WL
    end

    subgraph Preprocessing["Multimodal Ingestion Pipeline"]
        T["Text Normalizer<br/>(Arabic Diacritics & Punctuation)"]
        V["AudioWavConverter<br/>(MediaCodec -> 16kHz PCM WAV)"]
        I["ImagePayloadPreprocessor<br/>(Downscale & Base64 Packaging)"]
        WL -->|Text| T
        WL -->|Voice Note| V
        WL -->|Photo| I
    end

    subgraph RAG["2. Zero-Startup Deterministic RAG"]
        ASR["Meta muse-voice-transcribe-1.0"]
        V --> ASR
        SYN["Synonym Expansion Layer<br/>(Dialectal & Commerce Aliases)"]
        HASH["384-Dim Feature Hashing + BM25"]
        ROOM[("Room SQLite Vector Store")]
        TOP8["Top-8 Knowledge Chunks (topK = 8)"]

        T --> SYN
        ASR --> SYN
        SYN --> HASH --> ROOM --> TOP8
    end

    subgraph Reasoning["3. Unified Meta Model Reasoning Engine"]
        SPARK["Muse Spark 1.3 (Unified Single-Pass Reasoning)"]
        VISION["Muse Spark 1.3 Vision (Product Matching)"]
        FAILOVER["Transparent 404 Entitlement Failover<br/>(standard &lt;--&gt; contributor)"]
        KEYS["Meta Key Rotation & Circuit Breaker"]
        
        TOP8 --> SPARK
        I --> VISION --> SPARK
        SPARK --- FAILOVER
        SPARK --- KEYS
    end

    subgraph Execution["4. Dual Autonomous Modes"]
        B2B["4A. B2B Merchant Mode<br/>• Autonomous JSON Order Extraction<br/>• Room DB Order State Tracking<br/>• Direct WhatsApp Quick-Reply"]
        EXEC["4B. Executive Assistant Mode<br/>• Context-Aware Personal Replies<br/>• Schedule & Meeting Coordination<br/>• Commercial Action Guardrails"]
        
        SPARK -->|B2B Mode| B2B
        SPARK -->|Personal Mode| EXEC
    end
```

---

## ⚡ Live WhatsApp Production vs. Interactive Bot Simulator

To provide the evaluation committee with the most flexible testing experience, **Persona B2B** provides two interchangeable entry points to the exact same Meta AI pipeline:

| Dimension | 📱 Live WhatsApp Background Engine | 🧪 Interactive Bot Simulator (In-App) |
| :--- | :--- | :--- |
| **Target Audience** | Real customers on WhatsApp & WhatsApp Business | Hackathon Judges & Fast Evaluation |
| **Prerequisites** | Android Notification Access permission | **Zero Permissions Required** (Runs standalone) |
| **Input Modalities** | Inbound WhatsApp texts, voice notes, photos | Typed queries, dynamic FAQ chips, live mic audio |
| **Audio Processing** | WhatsApp audio notes $\rightarrow$ MediaCodec $\rightarrow$ Meta ASR | Live microphone $\rightarrow$ 16kHz PCM WAV $\rightarrow$ Meta ASR |
| **RAG & Reasoning** | Deterministic 384-Dim RAG + Meta Muse Spark 1.3 | Identical Deterministic RAG + Meta Muse Spark 1.3 |
| **Order Extraction** | Automatic JSON extraction into Room DB | Live interactive order preview + Room DB update |
| **Reply Dispatch** | Automated OS Notification Action Quick-Reply | Real-time chat bubbles in UI with timing metrics |
---

## 🌟 Core Pillars & Hackathon Innovations

### 1. 100% Exclusive Meta Model API Runtime
Every AI model invoked at runtime is an official Meta model via `MetaModelApiClient.kt`:
- **`muse-spark-1.3` & `muse-spark-1.3-contributor`**: Powers unified single-stage reasoning—simultaneously handling intent understanding, RAG grounded synthesis, conversational nuances, and structured JSON order extraction in a single authoritative pass.
- **`muse-spark-1.2` & `muse-spark-1.2-contributor`**: Serves as the secondary Meta reasoning tier.
- **`muse-voice-transcribe-1.0`**: Transcribes incoming customer voice notes and live simulator microphone recordings with high accuracy across English and colloquial Arabic.
- **Transparent Entitlement Failover**: Automatically detects HTTP `404 model_not_found` responses and transparently switches between standard model IDs (`muse-spark-1.3`) and hackathon contributor variants (`muse-spark-1.3-contributor`) on the fly, ensuring 100% compatibility with any judge's API key.
- **Dynamic Key Pool & Circuit Breakers**: Supports rotating across multiple Meta API keys with exponential backoff and human-in-the-loop (HITL) escalation if rate limits are exhausted.

### 2. True Multimodal Ingestion (Text, Voice Notes & Product Photos)
Persona B2B processes every customer interaction modality natively:
- **Product Photos & Image Recognition**: When customers send a photo of an item or inquiry (ImagePayloadPreprocessor.kt), the engine decodes, optimizes, and forwards the base64 visual payload to Meta's multimodal vision pipeline (Muse Spark 1.3 Vision). It extracts exact product specs, colors, and models to ground RAG retrieval and confirm orders.
- **Voice Notes & Audio Transcription**: Incoming customer voice messages and in-app microphone recordings are automatically converted to 16kHz mono PCM WAV on-device and transcribed in real-time via **Meta muse-voice-transcribe-1.0** with robust understanding of colloquial Arabic dialects and English.
- **Natural Conversational Text**: Fluent, dialect-aware chat replies powered by single-pass Meta reasoning.

### 3. Zero-Startup Deterministic 384-Dim RAG with Synonym Expansion
- **Zero External AI Models**: Built with a pure mathematical **Multilingual Feature Hashing & BM25 Vector Engine** (`EmbeddingEngine.kt`).
- **0-Second Cold Start**: Launches instantly with zero model download wait times while preserving a native `FloatArray(384)` vector layout in Room SQLite.
- **Colloquial & Commerce Synonym Expansion**: Pre-expands regional and commercial terms prior to hashing (e.g., mapping `هاتف` $\leftrightarrow$ `موبايل/جوال/فون`, `سعر` $\leftrightarrow$ `بكام/تكلفة`, `شحن` $\leftrightarrow$ `توصيل/دليفري`, `order` $\leftrightarrow$ `طلب/اوردر`).
- **Deep Context Window (`topK = 8`)**: Supplies the top 8 most relevant knowledge chunks directly to `Muse Spark 1.3` for comprehensive domain grounding.

### 4. Interactive Bot Simulator with Live Voice Recording & Dynamic FAQs
- **No WhatsApp Setup Needed for Judges**: Test the entire end-to-end Meta AI pipeline inside the built-in **Bot Simulator** bottom sheet.
- **Live Microphone Recording**: Record real-time 16kHz mono PCM WAV voice notes directly inside the simulator (`LiveAudioRecorder.kt`), complete with runtime permission handling, pulse/timer animations, hardware WAV conversion, and live transcription via `muse-voice-transcribe-1.0`.
- **Dynamic Suggested FAQ Chips**: Automatically parses indexed knowledge headings and product entities to generate 4–6 clickable suggested question chips tailored to the currently active knowledge base.

### 4. 1-Click Bilingual Sample Knowledge & Dual Modes (B2B vs. Personal)
- **4 Built-In Sample Templates (Arabic & English)**: Depending on your active UI language (Arabic or English), the Knowledge Studio and Bot Simulator display **2 instant 1-click injection buttons**:
  1. **Load Business / Sales Sample**: Injects a complete retail merchant catalog (products, sizes, stock, shipping rates, return policies, and payment methods).
  2. **Load Personal Assistant Sample**: Injects an executive assistant profile (working hours, scheduling rules, bio, and priority contact protocols).
- **Custom Knowledge with Meta AI**: Copy the built-in structured Knowledge Prompt from the app to generate your own custom domain knowledge with **Meta AI** and import it in seconds.

---

## 🚀 Step-by-Step Walkthrough (How to Use & Test the App)

When you launch **Persona B2B**, the app guides you through a seamless setup flow:

1. **Guided Onboarding & Permissions**:
   - Follow the interactive onboarding cards to grant notification and microphone permissions (see the [Android 13/14+ Sideloading Note](#-important-notes-for-judges-apk-sideloading--android-1314-permissions) below if testing live OS notifications).
2. **Enter Your Meta Model API Key**:
   - Navigate to **Settings $\rightarrow$ AI Engine** (or the Onboarding API step) and paste your **Meta Model API Key** from `dev.meta.ai` (if not already pre-configured via `secrets.local.properties` at build time).
3. **Select Persona Mode & Customize Identity**:
   - Choose between **B2B Sales Assistant Mode** or **Personal Assistant Mode**, and customize your assistant's name, tone, and fallback rules.
4. **Inject Knowledge (1-Click Samples or Custom Import)**:
   - Open the **Knowledge Studio (Import Knowledge)** tab.
   - **Fastest Path for Judges**: Tap either **"Load Business Knowledge"** or **"Load Personal Knowledge"**. The app automatically loads the matching **English or Arabic** pre-packaged sample based on the current app language and indexes it in milliseconds.
   - **Custom Path**: Copy the built-in prompt template, generate a custom knowledge base using **Meta AI**, and import the Markdown file/text.
5. **Test Live in the Bot Simulator (or via Real WhatsApp)**:
   - Tap the **Bot Simulator** button to open the interactive sandbox:
     - Tap any **Dynamic FAQ Chip** to test instant RAG grounding.
     - Tap the **Microphone Icon** to record a live voice query (`muse-voice-transcribe-1.0` $\rightarrow$ `muse-spark-1.3`).
     - **In B2B Mode**: Ask about product prices, shipping, or place a complete order (name, phone, address, items)—watch `Muse Spark 1.3` autonomously extract the order and populate the **Orders Dashboard**!
     - **In Personal Mode**: Test executive scheduling, availability inquiries, and personal message filtering.

---

## ⚠️ Important Notes for Judges (APK Sideloading & Android 13/14+ Permissions)

Because this application is distributed directly as a standalone APK outside the Google Play Store for hackathon evaluation, Android's built-in OS security applies two standard restrictions to sideloaded apps:

### 1. Installing the Signed Release APK (Google Play Protect)
> **"Application is built with an official v1/v2 cryptographic release signature. When installing outside Google Play, tap 'More details' $\rightarrow$ 'Install anyway' if prompted by Google Play Protect (or temporarily pause Play Protect during installation)."**

*(Note: If you onely wish to evaluate the Meta AI reasoning, RAG retrieval, order extraction, and live voice transcription via the in-app **Bot Simulator**, no special OS notification permissions are required!)*

### 2. Unlocking "Restricted Settings" for Notification Access (Android 13 / 14 / 15)
If you install the APK directly on a physical Android 13, 14, or 15 device (e.g., Realme, Xiaomi, Samsung, Pixel) and want to test **live WhatsApp notification interception**, Android automatically grays out the **Notification Listener** toggle for any app installed outside Google Play under a security feature called **Restricted Settings (الإعدادات المحظورة)**—even when the APK is officially release-signed.

**To enable Notification Access in 5 quick steps:**
1. **Open App Info**: Long-press the **Persona B2B** icon on your home screen or app drawer and tap **App Info `(i)`** (معلومات التطبيق).
2. **Open the Overflow Menu**: Tap the **three vertical dots (`⋮`)** in the top-right or top-left corner of the App Info screen.
3. **Allow Restricted Settings**: Select **"Allow restricted settings"** (السماح بالإعدادات المحظورة).
4. **Verify Identity**: Authenticate using your device's screen lock (fingerprint, face unlock, or PIN).
5. **Grant Notification Access**: Return to **Device & app notifications** (إشعارات التطبيقات والأجهزة) and toggle **Persona B2B** ON. It will now activate immediately without restriction!

---

## 🛠️ Build & Installation Guide

### Prerequisites
- **Android Studio** Ladybug (2024.2.1+) or newer
- **JDK 17**
- **Android SDK 35** (Compile SDK 35 / Min SDK 26)

### 🔑 Crucial Note on API Keys (Before Building vs. In-App UI)
In `app/build.gradle.kts`, the build system automatically checks for `META_API_KEY` inside **`secrets.local.properties`** (or `secrets.properties`) in the root project directory:
- **Option 1 (Pre-configure before building — Zero UI Setup)**:
  Create `secrets.local.properties` in the root directory of the project and add your key so the compiled app works out-of-the-box:
  ```properties
  META_API_KEY=your_meta_model_api_key_here
  ```
- **Option 2 (Configure anytime via In-App UI)**:
  If you build without `secrets.local.properties` (or install the pre-built APK), simply open the app and enter your key directly in **Settings $\rightarrow$ AI Engine $\rightarrow$ Meta Model API Key**. Keys are encrypted on-device via `EncryptedSharedPreferences`.

### 1. Building & Installing the Debug APK
To compile and install the debug build on a connected device or emulator:
```bash
# Build the Debug APK
./gradlew assembleDebug

# Or build and install directly via ADB
./gradlew installDebug
```
*Output path:* `app/build/outputs/apk/debug/app-debug.apk`

### 2. Building the Signed Release APK
To compile the R8-optimized, production-signed release APK:
```bash
./gradlew assembleRelease
```
*Output path:* `app/build/outputs/apk/release/app-release.apk`

### 3. Running the Automated Unit Test Suite (640+ Tests)
Verify the deterministic RAG engine, synonym expansion, single-stage orchestrator, and Meta Model API client failover logic:
```bash
./gradlew testDebugUnitTest
```

---

## 📄 License

This project is open-source and licensed under the **GNU Affero General Public License v3.0 (AGPLv3)**. See the [LICENSE](LICENSE) file for full details.