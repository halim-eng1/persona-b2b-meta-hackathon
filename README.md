# Persona B2B — Meta Model API Edition

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Platform-Android_14_%28API_34%2B%29-green.svg?logo=android)](https://developer.android.com)
[![Compose](https://img.shields.io/badge/UI-Jetpack_Compose_Material_3-4285F4.svg?logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Meta Model API](https://img.shields.io/badge/AI_Engine-Meta_Model_API-0668E1.svg?logo=meta)](https://about.meta.com)
[![License: AGPL v3](https://img.shields.io/badge/License-AGPL_v3-blue.svg)](https://www.gnu.org/licenses/agpl-3.0)

> **Persona B2B** is an autonomous, privacy-first, on-device AI communication and stateful commerce engine built for small and medium businesses (SMBs).
> Engineered for high-volume conversational commerce, it natively combines on-device neural edge classification with official **Meta Model API** multimodal reasoning (`Muse Spark 1.3`, `Muse Wonder`, and `Muse Voice Transcribe`).

---

## 🌟 Key Innovations & Architecture

```
                    +------------------------------------+
                    |   Incoming Customer Interaction    |
                    |   (WhatsApp / Messaging / Voice)   |
                    +-----------------+------------------+
                                      |
                                      v
                    +------------------------------------+
                    |  Hardware Audio / Media Extractor  |
                    | (MediaCodec -> 16kHz PCM WAV sync) |
                    +-----------------+------------------+
                                      |
                                      v
       +-------------------------------------------------------------+
       |             Autonomous Decision Gatekeeper                  |
       |  - Tier 1: On-Device Quantized Multilingual Embedding (ONNX)|
       |  - Tier 2: Cloud Decision Classifier & Intent Router        |
       +------------------------------+------------------------------+
                                      |
             +------------------------+------------------------+
             |                                                 |
             v (Complex Turn / Voice / Vision)                 v (High-Confidence Local)
+------------------------------------------+    +-------------------------------+
|          Meta Model API Client           |    |     On-Device Synthesizer     |
| - Muse Spark 1.3 (Reasoning & Orders)    |    | (Zero-latency offline reply)  |
| - Muse Wonder (Visual Product Ingestion) |    +---------------+---------------+
| - Muse Voice Transcribe (Audio Notes)    |                    |
+--------------------+---------------------+                    |
                     |                                          |
                     +--------------------+---------------------+
                                          |
                                          v
                    +------------------------------------+
                    |  Stateful Commerce & Order Action  |
                    |  (Room SQL Engine / Manifests)     |
                    +-----------------+------------------+
                                      |
                                      v
                    +------------------------------------+
                    |  Direct Reply Dispatcher / HITL    |
                    +------------------------------------+
```

### 1. Official Meta Model API Multimodal Core
- **`MetaModelApiClient.kt`**: High-throughput HTTP/2 integration with Meta inference endpoints.
- **Muse Spark 1.3**: Advanced business logic reasoning, conversation state tracking, and autonomous JSON order action invocation with automated `<think>` reasoning tag sanitation.
- **Muse Wonder**: Multimodal visual product search and verification directly from customer camera/screenshots.
- **Muse Voice Transcribe**: End-to-end voice note transcribing pipeline backed by on-device hardware-accelerated transcoding (`AudioWavConverter`).
- **Dynamic Key Pool Rotation**: Real-time multi-key health checks, automatic quota failover, and exponential backoff circuit breakers.

### 2. Dual Operational Modes (B2B & Personal)
- **B2B Merchant Mode**: Automated WhatsApp catalog discovery, customer order extraction, stateful order status management (Unfulfilled, Shipping, Shipped, Returned, Cancelled), and courier delivery manifest export.
- **Personal Assistant Mode**: Clean personal executive tasking and conversational auto-pilot, fully isolated from commercial catalog databases.

### 3. Edge-Native Performance & Privacy
- **Quantized Multilingual ONNX Runtime**: Local semantic search and vector retrieval running directly on device.
- **Hardware-Accelerated Media Transcoding**: Resamples dynamic audio formats to standard 16kHz linear PCM WAV using Android `MediaCodec` and `MediaExtractor`.
- **Zero-Leak Reasoning Guard**: Internal Chain-of-Thought traces are isolated and purged from customer-facing replies.

### 4. Internationalization & Design System
- **100% Bilingual Arabic & English**: Comprehensive localization across all strings, error recovery dialogs, and navigation routes.
- **Cairo Typography & RTL Mirroring**: Full right-to-left layout adaptation with high-contrast Outdoor Light and Dark Terminal themes.
- **Currency Generalization**: Adaptive `CurrencyFormatter` with locale-aware symbol placement and Latin/Arabic numeral normalization.

---

## 🛠️ Build & Installation

### Prerequisites
- Android Studio Ladybug (2024.2.1+) or newer
- JDK 17
- Android SDK 35 (Android 15)

### Configuration
Copy `secrets.properties.example` to `secrets.properties`:
```properties
META_API_KEY=your_meta_api_key_here
```

### Running Tests
```bash
./gradlew testDebugUnitTest
```

### Building Release APK
```bash
./gradlew assembleRelease
```

---

## 📄 License

This project is licensed under the **GNU Affero General Public License v3.0 (AGPLv3)**. See [LICENSE](LICENSE) for details.
