# 🛠️ Notara - Project Analysis

## 📌 Overview
**Notara** is a minimalist and secure productivity assistant for Android, focusing on privacy, performance, and modern design. It integrates notes, checklists, a calendar, and smart alarms.

---

## 🏗️ Technical Stack
- **Language:** Java
- **UI Framework:** Material Design 3 (Material You), ViewBinding
- **Architecture:** MVVM (Model-View-ViewModel) with Repository pattern
- **Minimum SDK:** 23 (Android 6.0)
- **Target SDK:** 35 (Android 15)
- **Dependency Management:** Gradle

---

## 🔐 Security & Privacy
- **Encryption:** AES-GCM via `androidx.security:security-crypto`.
- **Biometrics:** Integrated fingerprint and facial recognition using `androidx.biometric`.
- **Storage:** Fully local storage. No cloud sync by default.
- **Preferences:** Jetpack `DataStore` (Preferences) for secure and reactive settings.

---

## 📱 Core Components

### 1. UI Layer (Activities)
- `MainActivity`: Central hub for notes and navigation.
- `EditActivity`: Rich text/note editing.
- `ChecklistActivity`: Task list management.
- `CalendarActivity`: Integrated event visualization.
- `AlarmActivity`: Full-screen intent for scheduled tasks/checklists.
- `SettingsActivity`: App customization and security toggles.
- `TrashActivity`: Soft-delete management.

### 2. Data Layer
- `DatabaseHelper`: SQLite management for notes, checklists, and metadata.
- `NoteRepository`: Abstract layer for data operations.
- `NoteViewModel`: Reactive data exposure to the UI using `LiveData`.

### 3. System Integration
- `AlarmReceiver` & `BootReceiver`: Handles exact alarms and ensures they persist after device reboots.
- `Widget System`: Multiple widget providers (`1x1`, `1x2`, `2x2`) with `RemoteViews` for home screen integration.

---

## 📂 Project Structure
- `app/src/main/java/com/notara/`: Core logic, models, and UI.
- `app/src/main/java/com/notara/widget/`: App widget implementations.
- `app/src/main/res/`:
    - `layout/`: XML UI definitions.
    - `values/`: Colors, strings (EN/PT), and theme configurations.
    - `xml/`: Widget info metadata.
- `app/src/test/`: Unit tests for repository logic.

---

## 🚀 Key Features
- **Smart Alarms:** Displays checklists directly on the lock screen.
- **Dynamic Themes:** Support for Material You dynamic coloring.
- **Custom Backgrounds:** Geometric and mesh patterns for notes.
- **Multi-architecture Builds:** APK splits for `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`.
