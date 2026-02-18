# Rushlight Demo Video Script

**Target length:** 3-5 minutes
**Audience:** OTF Internet Freedom Fund reviewers
**Tone:** Serious, professional, purposeful
**Theme:** Pip-Boy (green CRT aesthetic) throughout for visual distinctiveness

---

## Pre-Recording Checklist

### Device Setup (Pixel 9a)
- [ ] Download and install GGUF model to `/sdcard/Android/data/io.rushlight.app/files/llm_models/`
  - Recommended: TinyLlama 1.1B Q4_K_M (~670MB) or Phi-3 Mini Q4_K_M (~2.3GB)
  - Command: `adb push model.gguf /sdcard/Android/data/io.rushlight.app/files/llm_models/`
- [ ] Download ZIM file to `/sdcard/Android/data/io.rushlight.app/files/zim/`
  - Recommended: Wikipedia Simple English (~300MB) or any small Wikipedia
  - Download from: https://download.kiwix.org/zim/wikipedia/
- [ ] Download a regional map via OsmAnd (e.g., Ukraine, Syria, or Myanmar for narrative relevance)
- [ ] Grant ALL permissions: camera, microphone, Bluetooth, location, nearby devices
- [ ] Set Pip-Boy theme as active (Tools > Theme > Pip-Boy)
- [ ] Load AI model via AI Chat > Download > Load
- [ ] Clear old chat history for clean demo
- [ ] Enable Do Not Disturb mode (clean notification bar)
- [ ] Charge to 100%
- [ ] Set up screen recording:
  - Option A: `scrcpy --record rushlight-demo.mp4 --max-fps 30`
  - Option B: Android built-in screen recorder

### Second Device (Optional, for P2P demo)
- [ ] Install Rushlight APK on second device
- [ ] Download same or different map/ZIM content
- [ ] Enable Bluetooth and nearby device permissions

---

## Script

### SEQUENCE 1: Opening - The Problem (30 seconds)

**[Text overlay on black screen]:**
> "When the internet goes dark..."

**[Voiceover]:**
"In 2023, Sudan experienced a total internet blackout lasting months. Myanmar's military junta has imposed rolling shutdowns since the 2021 coup. Ukraine's infrastructure attacks left entire cities offline. In these moments, the people who need information most — journalists, activists, humanitarian workers — lose access to maps, knowledge, and secure communication."

**[Text overlay]:**
> "What if your phone could still work?"

---

### SEQUENCE 2: Introduction - Rushlight (20 seconds)

**[Show Rushlight launching on phone, map loads with Pip-Boy green aesthetic]**

**[Voiceover]:**
"Rushlight is an open-source offline survival computer. Built on OsmAnd's battle-tested mapping platform, it runs entirely on your phone with zero internet dependency. On-device AI, offline Wikipedia, peer-to-peer sharing, and covert communications — everything you need when infrastructure fails."

**[Action: Show the tab bar on the right edge, briefly highlight each icon]**

---

### SEQUENCE 3: Offline Maps (30 seconds)

**[Action: Show map loaded with detailed regional data. Pan and zoom.]**

**[Voiceover]:**
"Full offline maps with turn-by-turn navigation. Download any region before deployment — no cell towers needed."

**[Action: Search for a location, show route calculation]**

**[Voiceover]:**
"Search, navigate, and track your position using GPS alone. The same mapping engine trusted by humanitarian organizations worldwide."

---

### SEQUENCE 4: AI Assistant (45 seconds)

**[Action: Tap AI Chat tab. Show the Pip-Boy themed chat interface.]**

**[Voiceover]:**
"A local AI assistant running entirely on-device. No data leaves the phone."

**[Action: Type "What are the symptoms of dehydration and how do I treat it?" — show streaming response]**

**[Voiceover]:**
"Powered by llama.cpp, the AI processes queries using downloaded language models. RAG integration pulls context from your offline Wikipedia and nearby map data for grounded, sourced answers."

**[Action: Show source citations in the response if available]**

**[Voiceover]:**
"Medical guidance, survival knowledge, translation assistance — all available offline."

---

### SEQUENCE 5: Offline Wikipedia (30 seconds)

**[Action: Tap Wiki tab. Show the Wikipedia browser with ZIM files.]**

**[Voiceover]:**
"The entire Wikipedia — or any subset — available offline through ZIM files."

**[Action: Search for an article, open it, scroll through formatted content]**

**[Action: Tap "Ask AI" to feed the article into AI chat]**

**[Voiceover]:**
"Search, browse, and feed articles directly into the AI for analysis. Download Wikipedia in any of 300+ languages before you go."

---

### SEQUENCE 6: P2P Sharing (45 seconds)

**[Action: Tap P2P tab. Show the scanning interface.]**

**[Voiceover]:**
"Share maps, Wikipedia databases, AI models, and even the app itself — device to device, no internet required."

**[If 2 devices: Show peer discovery, content manifest exchange, file transfer with progress bar]**

**[If 1 device: Show the UI with voiceover explaining the flow]**

**[Voiceover]:**
"Using Bluetooth and WiFi Direct, Rushlight creates a mesh of shared knowledge. One device with downloaded content can supply an entire team. The app itself can be shared as an APK — enabling grassroots distribution in regions where app stores are blocked."

---

### SEQUENCE 7: Morse Code (40 seconds)

**[Action: Tap Morse tab. Show send mode.]**

**[Action: Tap SOS quick message chip. Show Morse preview "... --- ...". Tap SEND.]**

**[Voiceover]:**
"Zero-infrastructure communication via Morse code. Send distress signals through your phone's flashlight or speaker."

**[Action: Switch to Receive mode. Show the listening interface.]**

**[Voiceover]:**
"Decode incoming Morse via microphone or camera. Built-in quick messages with GPS coordinates let you transmit your location when no other channel exists."

**[Action: Show the self-test feature decoding audio]**

---

### SEQUENCE 8: Security (30 seconds)

**[Action: Tap Tools tab. Scroll to Security section.]**

**[Voiceover]:**
"Built for hostile environments. Encrypted local storage. Biometric lock with PIN fallback."

**[Action: Show Emergency Wipe button, then Stealth Settings]**

**[Voiceover]:**
"Emergency data wipe destroys sensitive content instantly. Stealth mode hides the app from your launcher entirely — accessible only through a secret dialer code. If detained, your phone reveals nothing."

**[Action: Show the stealth dialer code *#73784#]**

---

### SEQUENCE 9: Closing - Why It Matters (30 seconds)

**[Show Rushlight on the map screen, Pip-Boy green glow]**

**[Voiceover]:**
"Rushlight is fully open-source under GPLv3. Every feature runs on-device with zero internet dependency. It's built for the people who need it most — journalists in conflict zones, activists under surveillance, aid workers in disaster areas."

**[Text overlay]:**
> github.com/anthropics/OsmAnd (or actual repo URL)

**[Voiceover]:**
"We're seeking OTF support to fund a professional security audit, usability testing with at-risk users, multi-language localization, and sustained open-source maintenance."

**[Final text overlay]:**
> "Rushlight: Knowledge that travels with you."

---

## Recording Tips

1. **Record each sequence separately** — easier to re-take individual sections
2. **Practice each sequence 2-3 times** before recording
3. **Keep transitions smooth** — no fumbling between taps
4. **Pip-Boy theme throughout** — it's visually distinctive and memorable
5. **Show real content** — actual Wikipedia articles, actual map navigation
6. **Landscape vs Portrait** — record in portrait (phone's native orientation)
7. **Speed** — don't rush. Let each feature register visually before moving on
8. **Voiceover** — can be recorded separately and overlaid in editing

## Post-Production Notes

- Add text overlays for section transitions
- Include subtle background music (royalty-free, understated)
- Add the Rushlight logo in corner watermark
- Export at 1080p minimum
- Upload as unlisted YouTube video for OTF application link
