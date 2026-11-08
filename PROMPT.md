# Let an AI assistant set up your TV

If you use an AI coding assistant that can run commands (Claude Code, Cursor, and similar), paste one line:

```text
Set up my Android/Google TV with https://github.com/haziqfikri96/Debloat-google-TV — follow PROMPT.md
```

In Malay: `Pasang dekat TV rumah saya: https://github.com/haziqfikri96/Debloat-google-TV — ikut PROMPT.md`

The assistant does the rest. You only answer its questions and press **Allow** on the TV.

---

## Instructions for the AI assistant

Follow these steps in order, and explain each one to the user in plain words. Every change `gtv.sh` makes can be undone, and you must keep it that way.

1. **Get the code.** Clone the repository and `cd` into it. On an iPhone with iSH, run `sh setup-ios.sh`. On Termux, run `bash setup-phone.sh`. On a computer, check that `adb` works.
2. **Connect to the TV.**
   - Ask for the TV's IP address (TV: Settings → Network & Internet). Run `./gtv.sh connect <IP>`, and tell the user to press **Allow** on the TV.
   - If the TV only offers *Wireless debugging* (Google TV 12+), ask for the pairing IP:port and code, then run `./gtv.sh pair <IP:PORT> <CODE>`. After that, connect to the IP:port shown on the main Wireless debugging screen.
3. **Look before changing anything.** Run `./gtv.sh status` and `./gtv.sh apps`, and tell the user what you see (brand, Android version, RAM, launcher).
4. **Debloat.**
   - Run `./gtv.sh -n debloat recommended` first and show the list.
   - After the user agrees, run it without `-n`.
   - On a Xiaomi TV, also offer `./gtv.sh debloat xiaomi`.
   - Never use `aggressive` unless the user says they don't need Google Assistant, casting or the Google Home panel.
5. **Speed tweaks.** Run `./gtv.sh tweaks`.
6. **Remove apps the user doesn't use.** Run `./gtv.sh apps` and ask which numbers to remove. Warn before removing anything marked as handling **HDMI inputs** (Live TV / tvinput apps) or **screen mirroring**.
7. **New home screen.**
   - Run `./gtv.sh glass`. It installs Glass Launcher, turns on its HOME takeover and the StandBy clock, and keeps the stock launcher enabled, which the Google Home panel needs.
   - If another launcher's HOME override (such as Projectivy) is on, it asks before turning it off.
8. **Prayer times.**
   - Ask the user's town, then run `./gtv.sh prayer "<town>"`. In Malaysia this uses JAKIM's official zone timetable.
   - Show the warnings about the TV clock and time zone if there are any.
9. **Google Home.** Run `./gtv.sh google-home`.
10. **Reboot and check.**
    - Run `./gtv.sh reboot`, then `./gtv.sh status`.
    - Check that HOME opens Glass, that the inputs work, and that nothing removed came back. If something came back, run `./gtv.sh recheck`.

**Undo:**
- `./gtv.sh bring-back` brings back individual apps.
- `./gtv.sh launcher-reset` brings back the stock home screen.
- `./gtv.sh restore` undoes everything.

Don't delete system apps with `pm uninstall` without `--user 0`. Don't disable `com.google.android.apps.tv.launcherx` or `com.google.android.tungsten.setupwraith` unless the user explicitly asks; that can leave a black screen. And don't touch anything listed in `lists/protected.txt`.
