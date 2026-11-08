# Debloat Google TV

Make Android TV and Google TV boxes (Chromecast with Google TV, Google TV Streamer, Sony, TCL, Mi Box, Shield, etc.) **fast and smooth again**. It removes bloat, cuts background work, and swaps the ad-heavy home screen for a clean launcher.

Everything runs over ADB from your computer. You don't need root, and **every change can be undone**:

- Apps are removed only for the TV's user (`pm uninstall --user 0`). They stay on the system partition, and a factory reset brings them back.
- Before the first change, the script saves a backup of your packages, settings and home app.
- `./gtv.sh restore` puts the TV back the way it was.

## Quick start

1. **On the TV:** Settings → System → About → click **Build** 7 times. Then Settings → System → Developer options → turn on **USB debugging** (on Google TV this also turns on network debugging).
2. **On your computer:** install [Android platform-tools](https://developer.android.com/tools/releases/platform-tools) so `adb` is on your PATH.
3. Find the TV's IP address (Settings → Network) and run:

```bash
git clone https://github.com/haziqfikri96/Debloat-google-TV.git
cd Debloat-google-TV

./gtv.sh -s 192.168.1.50 status          # connect; accept the prompt on the TV
./gtv.sh -s 192.168.1.50 -n all           # dry run: shows what would change
./gtv.sh -s 192.168.1.50 all              # backup + debloat + speed tweaks
./gtv.sh -s 192.168.1.50 reboot
```

> Windows: run it from **Git Bash** or **WSL**.

## Commands

| Command | What it does |
|---|---|
| `all [profile]` | Backup, debloat (default `recommended`), then speed tweaks |
| `debloat <profile>` | Removes the packages in a profile (see below) |
| `tweaks [0\|0.5\|1]` | Speed tweaks. The argument is the animation speed (default `0.5`; `0` turns animations off) |
| `launcher <apk\|package>` | **Redesign:** installs a launcher, sets it as home, and disables the stock home screen |
| `launcher-reset` | Brings back the stock Google TV / Android TV home screen |
| `restore [backup]` | Undoes everything. By default it restores `original`, the state before your first run |
| `status` | Model, RAM, animation speeds, home app, bloat still installed, top memory users |
| `packages [filter]` | Lists installed packages, e.g. to find OEM bloat |
| `backup` / `backups` | Takes a snapshot / lists snapshots |

Options: `-s SERIAL|IP` picks the device, `-n` is a dry run, `-y` skips confirmation.

## Debloat profiles

Profiles stack: `aggressive` includes `recommended`, and `recommended` includes `safe`.

| Profile | Removes | You lose |
|---|---|---|
| `safe` | Tutorials, feedback, Play Games, YouTube Music/Kids/TV, print services, screensavers | Nothing important. Reinstall any app from the Play Store |
| `recommended` | **+** recommendation engine, Ambient mode, Play Movies, partner promos, TalkBack, calendar/contact sync | Fewer "For you" rows, no ambient photo slideshow |
| `aggressive` | **+** Google Assistant/voice search, offline speech, TTS, Chromecast built-in, Live Channels | **Voice search and casting to the TV** |
| `custom` | Whatever you put in [`lists/custom.txt`](lists/custom.txt) (OEM apps, unused streaming apps) | Your choice |

[`lists/protected.txt`](lists/protected.txt) lists packages the tool will **never** remove, even if you add them to a list: Play Services, Play Store, Settings, the keyboard, the setup wizard, the remote service and the stock launchers. Removing any of these can bootloop the TV or brick setup.

You can also pass a list file directly: `./gtv.sh debloat my-list.txt`.

## What `tweaks` changes

| Tweak | Why it helps |
|---|---|
| Animation scale 0.5× (or 0) | Menus and app switches feel twice as fast |
| Wi-Fi / Bluetooth "always scanning" off | Less background radio and CPU work |
| Screensaver / Ambient mode off | Stops the TV from downloading photos all day |
| `pm trim-caches` | Frees storage. Low storage makes Android TV crawl |
| ART `speed-profile` compile for all apps | Apps start and scroll faster. Takes a few minutes once |

## Redesign: replace the home screen

Most of the lag on Google TV comes from the stock home screen. It is full of ads, autoplaying trailers and heavy recommendation rows. A lightweight launcher makes the biggest difference of anything here.

1. Download a TV launcher APK, for example:
   - **Projectivy Launcher**: clean, customisable, and can override the home button
   - **FLauncher**: open source, minimal, no ads
   - **Wolf Launcher** or **ATV Launcher**: very light
2. Then run:

```bash
./gtv.sh -s 192.168.1.50 launcher ~/Downloads/launcher.apk   # install + set as home
# or, if it's already installed from the Play Store:
./gtv.sh -s 192.168.1.50 launcher com.spocky.projengmenu
```

The script sets the new launcher as the home activity and **disables** (does not uninstall) the stock launcher. To undo it, run `./gtv.sh launcher-reset`.

> On some Chromecast / Google TV firmware, the HOME button is hard-wired to the stock launcher. If HOME still opens Google TV, turn on your launcher's own "override home button" accessibility option (Projectivy has one).

## Undo / troubleshooting

- **Undo everything:** `./gtv.sh restore` then `./gtv.sh reboot`.
- **Reinstall a single app:** `adb shell cmd package install-existing <package>`
- **A feature broke:** check `./gtv.sh status`, then remove the related package from your list and run `restore`.
- **Worst case:** a factory reset restores every app, because nothing is deleted from the system partition.

Backups are stored in `backups/` (git-ignored). `original` always points to the state before your first change, and `latest` points to the newest snapshot.

## Disclaimer

Use at your own risk. Package names differ between manufacturers and firmware versions. Packages that aren't installed are skipped. Always try `-n` (dry run) first.
