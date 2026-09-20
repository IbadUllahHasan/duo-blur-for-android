---
name: Bug report
about: Something is broken or behaving wrong
title: "[bug] "
labels: bug
assignees: ""
---

<!--
The three things below solve most of what comes in. If you skip them,
expect a follow-up asking for them anyway.
-->

**Device**
<!--
Make and model. e.g. "Pixel 8", "Samsung Galaxy S23", "OnePlus 11".
This matters — the same Android version behaves differently on different
OEMs because of sensor axis conventions, GPU drivers, and overlay policy.
-->

**Android version**
<!--
Settings → About phone → Android version. e.g. "14", "13".
-->

**Steps to reproduce**
<!--
What you did, in order. "Opened DuoFlow, tapped Enable, granted the
overlay permission, accepted the capture dialog, tilted the phone left."
If it happens every time, say so. If it's flaky, say so.
-->

**What I expected**

**What actually happened**
<!--
Screenshots or a screen recording help a lot. If it's reproducible,
logcat output between the tap and the symptom is gold:

  adb logcat -d -s FoldEchoOverlay:* FoldEchoService:* FoldShader:* AndroidRuntime:E
-->

**Which renderer**
<!--
Ray-traced fold / Classic (lean/scale). The mode selector in the app's
Tuning section. If the ray-traced mode is selected but the effect looks
like the classic one, the AGSL shader probably failed to compile on
your GPU — logcat will say so.
-->

**Anything else**
<!--
Build flavour (debug / signed release), whether you've tried
Recalibrate, whether toggling Flip tilt direction changes anything.
-->
