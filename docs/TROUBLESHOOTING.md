# Troubleshooting

**First, always run the check:** `gtv doctor` (computer: `./gtv.sh doctor`, Windows: `bash gtv.sh doctor`). It tells you what is wrong and how to fix it. The sections below cover everything it can't fix itself.

Jump to: [Installing](#installing-the-program) · [Connecting](#connecting-to-the-tv) · [While it runs](#while-it-runs) · [After it finishes](#after-it-finishes) · [Undo](#undo-everything)

---

## Installing the program

### `gtv: command not found`
The shortcut was not created, or you opened a new terminal that doesn't see it.
- **Android (Termux):** `cd ~/Debloat-google-TV && bash setup-phone.sh`
- **iPhone/iPad (iSH):** `cd ~/Debloat-google-TV && sh setup-ios.sh`
- **Any device**, without the shortcut: `cd ~/Debloat-google-TV && bash gtv.sh`

### `adb: command not found`
- **Android (Termux):** `pkg install android-tools`
- **iPhone/iPad (iSH):** `apk add android-tools`
- **Windows:** `C:\platform-tools\adb.exe` must exist, and you must run the `echo 'export PATH=...'` line from the [Windows guide](INSTALL-LAPTOP.md#3-open-git-bash-and-make-adb-available). Then open a *new* Git Bash window.
- **Mac:** `brew install --cask android-platform-tools`
- **Linux:** `sudo apt install adb` (Fedora/Arch: `android-tools`)

### `gtv.sh needs bash 4 or newer` (Mac)
macOS has an old bash. Run `brew install bash`, then start the program with `"$(brew --prefix)/bin/bash" ./gtv.sh`.

### `apk add git` says `git (no such package)` (iPhone/iPad)
The package list is empty. Run `apk update` first, then `apk add git` again. The command in the guide already does both.

### `pkg` shows mirror or *"Unable to locate package"* errors (Android)
Run `termux-change-repo`, press Enter on the first choice, then run `pkg update -y` again.

### `$'\r': command not found` or `bad interpreter` (Windows)
The files were saved with Windows line endings. Run these in Git Bash, inside the `Debloat-google-TV` folder:
```bash
git config core.autocrlf false
git checkout -- .
```
If it still happens: delete the folder and `git clone` it again.

### `Permission denied` when running `./gtv.sh`
Run `bash gtv.sh` instead, or `chmod +x gtv.sh` first.

---

## Connecting to the TV

### *"Could not connect"*, *"Connection refused"* or *"timed out"*
Check these in order:
1. The TV is **on and awake** (press a button on the remote).
2. The phone/computer and the TV are on the **same Wi-Fi network**. Guest networks, VPNs and mobile data don't work.
3. The IP address is right. It can change after the TV or router restarts: look again at Settings → Network & Internet.
4. **Developer options → USB debugging** is on. Newer TVs need **Wireless debugging** and the [pairing steps](TV-SETUP.md#how-pairing-works-option-2) (menu option **2**), because port 5555 is closed on them.
5. **iPhone/iPad:** Settings → Privacy & Security → **Local Network** → turn **iSH** on.
6. **Mac:** System Settings → Privacy & Security → **Local Network** → turn **Terminal** on.

### *"Pairing failed"*
- The 6-digit code **expires** when the box closes on the TV. Click **Pair device with pairing code** again for a new code and a new port.
- Use the **pairing** address (from the pairing box) for pairing, and the **other** address (main Wireless debugging screen) for connecting. They have different ports.
- Type the numbers carefully, with a colon between IP and port, e.g. `192.168.1.50:41235`.

### `unauthorized`
The TV hasn't allowed this device. **Look at the TV screen** for *Allow USB debugging?*, tick **Always allow** and press **Allow**. If no box appears: TV → Developer options → **Revoke USB debugging authorizations**, then connect again.

### `offline`
A stale connection. Run `adb disconnect`, connect again (menu **1**), and if that fails restart the TV.

### *"Several devices are connected"*
Pick the TV: `./gtv.sh -s 192.168.1.50 status`, or run `adb disconnect` first to clear old connections.

### It worked yesterday, today it can't find the TV
Wireless debugging gets a **new port** each time it turns off and on (also after a TV restart). Open the Wireless debugging screen on the TV and connect to the address shown now. To stop the IP changing, give the TV a fixed address ("DHCP reservation") in your router settings.

---

## While it runs

### The "pre-compiling apps" step seems stuck
It is not stuck. The TV prints nothing while it works, and it takes **5 to 15 minutes** (longer on older TVs). The newest version prints `still working...` every 30 seconds, and stops waiting after 20 minutes. Keep Termux/iSH on the screen and don't lock the phone.

This step is **optional**: everything else has already been done when you reach it. To skip it, press **Ctrl-C** (iSH: tap **ctrl** on the keyboard bar, then **c**), then restart the TV with `gtv reboot`. You can run it again later (menu **5**).

### After "Rebooting" the screen just sits there
The TV did restart. Older versions of `gtv` waited for the TV to answer, which it never does while restarting. Press **Ctrl-C** (iSH: tap **ctrl** on the keyboard bar, then **c**) and carry on. The newest version stops waiting after a few seconds by itself.

### It stopped half way (phone locked, app closed)
Open the app again, type `gtv` and run the same step again. Nothing breaks if you repeat a step. Your backup was saved before the first change.

### `Download failed` when installing Glass
The device can't reach GitHub. Check its internet, turn off any VPN, and try again. Or download `glass-launcher.apk` from the [Releases page](https://github.com/haziqfikri96/Debloat-google-TV/releases/tag/glass-latest) into your **Downloads** folder and run `gtv glass` again. It finds the file there.

### `Image not found` for a wallpaper
Give the exact file name. In iSH run `ls /mnt` to see it (iPhones sometimes add a second ending, e.g. `.jpg.JPG`; `gtv` already allows that). On Android the photo must be in **Downloads** or **Pictures**.

---

## After it finishes

### HOME still opens the old Google TV home screen
Many Google TVs ignore the "default home app" setting. `gtv glass` turns on a helper for this (**Settings → Accessibility → Glass Launcher: open on HOME**). Check it is on. If it still fails, run `gtv glass --replace`. If another app such as **Button Mapper** or **Projectivy** has its own HOME setting, switch that off or point it at *Glass Launcher*.

### After a restart the TV opens the old home screen and I have to open Glass by hand
Glass opens itself through a small helper (**Settings → Accessibility → Glass Launcher: open on HOME**). Two things can go wrong:
1. **The helper is off.** Run `gtv doctor`. If it says *HOME takeover is OFF*, run `gtv glass` once more (menu **g**), then restart the TV (menu **r**).
2. **It started too late after the restart.** Older versions missed the first home screen after a reboot. Update (`git pull`, then `gtv glass`); the new version opens Glass by itself about 2 seconds after the TV starts.

If it still happens, check on the TV that **Glass Launcher: open on HOME** is switched on, and that no other app (Button Mapper, Projectivy) takes the HOME button.

### HDMI inputs or Live TV stopped working
You removed the TV's Live TV app, which shows the inputs. Run `gtv bring-back` and pick it from the list.

### Voice search, casting or the Google Home panel stopped working
You used the `aggressive` level. Run `gtv google-home`; it restores Google Assistant and casting. Don't use `aggressive` unless you don't need them.

### An app I removed came back after a reboot or update
Run `gtv recheck`. It removes them again. (On Xiaomi TVs see [the Xiaomi note](../README.md#xiaomi-tvs-netflix--prime-video-come-back-after-a-reboot).)

### The prayer times look wrong
Almost always the **TV's clock or time zone** is wrong. On the TV: Settings → System → Date & time → turn on automatic date and time, and set the right time zone. Then run `gtv prayer "Your Town"` again. In Malaysia the launcher shows JAKIM's official times for your zone.

### The Spotify "now playing" card doesn't appear
Run `gtv glass` again (it gives Glass access to music playback info), then play a song. It only shows while music is playing. If it still doesn't: TV → Settings → Apps → Special app access → **Notification access** → turn on **Glass now playing**.

### Everything looks different after an update
Updating Glass restarts it and re-applies your settings (location, wallpaper, Google Home). If something is missing, run `gtv glass` again.

---

## Undo everything

Nothing is deleted for good: apps are only hidden for the TV's user.

| I want to… | Run |
|---|---|
| Bring back one or more removed apps | `gtv bring-back` |
| Go back to the stock home screen | `gtv launcher-reset` |
| Put the whole TV back as it was before the first run | `gtv restore`, then `gtv reboot` |
| Remove Glass Launcher completely | `gtv launcher-reset`, then `adb uninstall io.github.debloatgtv.glass` |
| Start from zero | TV → Settings → System → Reset (factory reset). This also brings back every app |

Still stuck? [Open an issue](https://github.com/haziqfikri96/Debloat-google-TV/issues) and paste the output of `gtv doctor` and the exact error message.
