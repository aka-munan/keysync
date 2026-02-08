# KeySync

KeySync is an Android app that allows users to play games using an external keyboard and mouse
without requiring root access. It leverages [Shizuku](https://github.com/RikkaApps/Shizuku) to grant
necessary permissions and maps key events from external input devices to on-screen touch points.

## Native Dependent – Feature Branch

## Overview

This feature branch introduces the **first phase of a native input handling pipeline** for the KeySync app.  
The primary goal is to **reduce dependency on floating windows and view-based input listeners** by moving critical input reading logic closer to the system level using native code.

Many users have reported issues related to floating window permissions, reliability, and OEM restrictions. This branch is an architectural step toward making KeySync **more robust, low-latency, and eventually floating-window independent**.

This is an **incremental migration**, not a complete replacement yet.

___

## Usage Instructions

User-facing usage and setup instructions are currently maintained in the **[main](https://github.com/aka-munan/keysync?tab=readme-ov-file#usage) branch**.

This feature branch focuses on **internal architectural changes** and does not introduce any breaking changes or new user-facing workflows yet.

👉 Please refer to the [main](https://github.com/aka-munan/keysync?tab=readme-ov-file#usage) branch README for:
- Installation steps
- Permissions setup
- App usage instructions

---

## What’s Implemented (Current State)

### ✅ Native Input Reading
- Keyboard input events are now **read directly via native C++ code**
- Uses `/dev/input/eventX` for low-level input capture
- Significantly reduces dependency on UI-based listeners for keyboard input

### ✅ Hybrid Architecture (Temporary)
- Native C++ is used for **input reading**
- Kotlin layer still handles:
    - Event injection
    - Mouse pointer location tracking
    - Other key event handling via view-based listeners

This hybrid model is intentional and serves as a **transition phase**.

---

## What’s Still Pending

The following components **still depend on UI / Kotlin-based listeners** and will be migrated in future updates:

- Mouse pointer location tracking
- Mouse key events
- Event mapping logic
- Event injection pipeline

---

## Future Roadmap

Planned enhancements after this initial merge include:

- Fully native event mapping and injection  
(Lowest possible latency, no UI thread involvement)

-  Global key combinations
    - Toggle floating window on/off
    - Trigger system navigation (Home, Recents, Back)

- In-app typing support
    - Direct text injection into foreground apps

-  Progressive removal of floating window dependency
    - Floating window becomes optional or completely removed

---

## Merge Plan

This feature branch is expected to be **merged into the main branch in the near future** after:
- Stability validation
- Incremental migration of remaining UI-dependent components

---

## Notes

- This is not a breaking change
- Existing functionality remains intact
- Native integration is scoped carefully to avoid regressions

---

## Status

🟡 **In Progress**  
Initial native input reading is complete. Further native migration is planned.

---

## Feedback & Community Input

Feedback is **Appreciated**

### You can share feedback on:
- Stability of native keyboard input reading
- Any regressions compared to the floating-window-based approach
- Latency improvements (or lack thereof)
- Device- or OEM-specific behavior
- Suggestions for native event mapping or injection improvements

### How to share feedback
- Open an **Issue** with logs, device details, and reproduction steps
- Comment on the feature branch with observations or suggestions
- Propose architectural improvements or edge cases we may have missed

This branch is intentionally iterative. Early feedback helps ensure that the eventual merge into `main` results in a **more reliable, performant, and future-proof KeySync experience**.