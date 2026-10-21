# herdr for Android

Phone client for [herdr](https://herdr.dev): see agents across your machines, get notified when one is blocked, and answer it.

The app talks to each machine over SSH and runs the `herdr` CLI there (`herdr api snapshot`, `pane read`, `agent prompt`, …). Nothing extra runs on the server.

## Machine requirements

- herdr installed and a session running (`herdr session list`).
- SSH reachable from the phone. Easiest: Tailscale with Tailscale SSH (`sudo tailscale set --ssh`), which needs no key setup.
  Otherwise add the phone's key (shown in onboarding / Settings › Phone SSH key) to `~/.ssh/authorized_keys`, or use a password.

## Build

```bash
export JAVA_HOME=$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home
./gradlew assembleDebug testDebugUnitTest
adb install app/build/outputs/apk/debug/app-debug.apk
```

IBM Plex Mono is under the SIL Open Font License (`third_party/IBMPlexMono-OFL.txt`).
