# Debloat Google TV

Make Android TV and Google TV boxes (Chromecast with Google TV, Google TV Streamer, Sony, TCL, Xiaomi, Mi Box, Shield and more) **fast and smooth again**. It removes bloat, cuts background work, and swaps the ad-heavy home screen for a clean, frosted-glass launcher with prayer times, weather, a Spotify player and a Google Home button.

![Glass Launcher](docs/glass-clean.jpg)

**You don't need root, a computer or any special skill.** You can set up the TV from an Android phone, an iPhone/iPad or a laptop, in about 15 minutes. And **every change can be undone**:

- Apps are removed only for the TV's user (`pm uninstall --user 0`). They stay on the TV's system partition, and a factory reset brings them back.
- Before the first change, a backup of your packages, settings and home app is saved.
- `gtv restore` puts the TV back the way it was.

> **Have an AI coding assistant?** Paste one line and let it do everything. See [PROMPT.md](PROMPT.md).

## How to install (3 steps)

### 1. Get your TV ready

On the TV, turn on Developer options and **USB debugging** / **Wireless debugging** (3 minutes, with the remote).
**[Follow: TV setup](docs/TV-SETUP.md)**

### 2. Pick your device

Use whichever you have. The steps are written to be followed one by one, with the exact thing to type or tap.

| I have… | Follow this guide | Needs |
|---|---|---|
| **An Android phone** | **[Install from an Android phone](docs/INSTALL-ANDROID-PHONE.md)** | The free *Termux* app |
| **An iPhone or iPad** | **[Install from an iPhone or iPad](docs/INSTALL-IPHONE-IPAD.md)** | The free *iSH Shell* app |
| **A laptop or desktop** (Windows, Mac, Linux) | **[Install from a computer](docs/INSTALL-LAPTOP.md)** | Git and Android platform-tools |

All three end up in the same place: a numbered menu (`gtv`) where you connect to the TV, make it fast, and install the glass home screen.

### 3. Check, then run

Each guide ends with the same short list. This is the whole job:

```text
gtv doctor      check that this device and the TV are ready (shows how to fix anything that isn't)
gtv             open the menu:   1/2 connect  ·  3 make it fast  ·  g glass home screen  ·  r reboot
```

Something went wrong? Run `gtv doctor`, then see **[Troubleshooting](docs/TROUBLESHOOTING.md)**.

### The menu

```
Debloat Google TV   TV: 192.168.1.50:5555
  1) Connect to TV
  2) Pair TV (Google TV 12+ wireless debugging)
  3) Make it fast (backup + recommended debloat + speed tweaks)
  4) Debloat only (choose level)
  5) Speed tweaks only
  d) Check my setup (run this if something fails)
  g) Glass home screen (frosted-glass launcher)
  w) Glass wallpaper from a photo
  p) Prayer times location
  h) Google Home control
  u) Remove apps (pick by number)
  b) Bring back removed apps
  6) Other launcher (APK or package)
  7) Bring back stock home screen
  8) Status
  9) Undo everything
  r) Reboot TV
  0) Exit
```

On a computer you can use commands instead of the menu; see the table below. Windows: `bash gtv.sh <command>`. Mac and Linux: `./gtv.sh <command>`.

## What you get

- **Speed:** bloat removed, background work cut, animations faster, apps pre-compiled.
- **Glass home screen:** a clean, frosted-glass launcher. Your own wallpaper shows through, with a big clock, weather, a dock of your apps and shortcut icons. Apps you don't have show as dimmed icons and are **installed only when you select them**.
- **Prayer times** that follow your location. In Malaysia they are JAKIM's official times; elsewhere they are calculated with your country's method.
- **Spotify player:** a floating "now playing" card with the cover, title and play/next/previous buttons.
- **Google Home** button, an **Inputs** button for HDMI, and a StandBy clock screensaver.
- **Undo** for everything.

See [glass-launcher/README.md](glass-launcher/README.md) for all the features and how the glass stays fast.

## Commands

| Command | What it does |
|---|---|
| *(no command)* / `menu` | Numbered interactive menu, easiest on a phone |
| `doctor` | Checks that this device and the TV are ready, and shows how to fix anything that isn't. Run it first |
| `connect <IP[:PORT]>` | Connects over Wi-Fi and remembers the TV for later runs |
| `pair <IP:PORT> <CODE>` | Pairs with *Wireless debugging* (Google TV / Android 11+) |
| `all [profile]` | Backup, debloat (default `recommended`), then speed tweaks |
| `debloat <profile>` | Removes the packages in a profile (see below) |
| `tweaks [0\|0.5\|1]` | Speed tweaks. The argument is the animation speed (default `0.5`; `0` turns animations off) |
| `glass` | **Redesign:** installs the frosted-glass home screen and sets it as home |
| `wallpaper <photo\|--remove>` | Puts your own photo behind the glass |
| `prayer <gps\|city\|lat,lng>` | Sets the prayer times location. Uses JAKIM's official times in Malaysia, calculated elsewhere, and checks the TV's clock and time zone |
| `google-home` | Sets up the Google Home button and restores Assistant/casting if they were debloated |
| `suggestions on\|off\|reset` | Icons for popular apps you haven't installed (installed only when you select them) |
| `apps` | Numbered list of the apps on your home screen. Type the numbers to remove (menu **u**) |
| `bring-back [pkg]` | Numbered list of apps `gtv` removed. Pick the ones to bring back (menu **b**) |
| `recheck` | Removes again any app that came back after a reboot or update (`status` shows them) |
| `screensaver on\|off` | Glass StandBy clock: a big clock, Hijri date and next prayer, burn-in safe |
| `launcher <apk\|package>` | Uses any other launcher instead |
| `launcher-reset` | Brings back the stock Google TV / Android TV home screen |
| `restore [backup]` | Undoes everything. By default it restores `original`, the state before your first run |
| `status` | Model, RAM, animation speeds, home app, bloat still installed, top memory users |
| `packages [filter]` | Lists installed packages, e.g. to find OEM bloat |
| `backup` / `backups` | Takes a snapshot / lists snapshots |

Once you `connect`, you don't need `-s` again. Options: `-s SERIAL|IP` picks the device, `-n` is a dry run, `-y` skips confirmation.

## Debloat profiles

Profiles stack: `aggressive` includes `recommended`, and `recommended` includes `safe`.

| Profile | Removes | You lose |
|---|---|---|
| `safe` | Tutorials, feedback, Play Games, YouTube Music/Kids/TV, print services, screensavers | Nothing important. Reinstall any app from the Play Store |
| `recommended` | **+** recommendation engine, Play Movies, partner promos, TalkBack, calendar/contact sync | Fewer "For you" rows |
| `aggressive` | **+** Google Assistant/voice search, offline speech, TTS, Chromecast built-in, Live Channels, Ambient mode (which also hosts the Google Home panel) | **Voice search, casting, and Google Home voice control**. `./gtv.sh google-home` restores the Assistant |
| `xiaomi` | Xiaomi tracking (`com.xiaomi.statistic`), promo auto-installer, Mi Music. Optional extras are listed in [`lists/xiaomi.txt`](lists/xiaomi.txt) | Nothing you'd notice. Use it on its own: `./gtv.sh debloat xiaomi` |
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

## Redesign: frosted-glass home screen

Most of the lag on Google TV comes from the stock home screen. It is full of ads, autoplaying trailers and heavy recommendation rows. This repo includes **[Glass Launcher](glass-launcher/)**, a tiny home screen with a frosted-glass design that is built to stay smooth on cheap TV hardware.

```bash
./gtv.sh glass                    # install it and make it the home screen
./gtv.sh wallpaper beach.jpg      # optional: put your own photo behind the glass
./gtv.sh launcher-reset           # back to the stock home screen
```

It also has **prayer times** (JAKIM's official times in Malaysia), a **Google Home** button, and icons for popular apps that are **installed only when you select them**.

From the phone menu, choose **g** for the Glass home screen, **w** for a wallpaper, **p** for the prayer location and **h** for Google Home. The **palette icon** on the TV switches between Aurora, Ocean, Sunset, Graphite and Photo. See [glass-launcher/README.md](glass-launcher/README.md) for how the glass stays fast.

### Or use another launcher

You can also install any other TV launcher APK, for example **Projectivy Launcher**, **FLauncher**, or **Wolf Launcher** / **ATV Launcher**:

```bash
./gtv.sh launcher ~/Downloads/launcher.apk   # install + set as home
./gtv.sh launcher com.spocky.projengmenu     # already installed from the Play Store
```

Either way, the script sets the new launcher as the home activity and **disables** (does not uninstall) the stock launcher. To undo it, run `./gtv.sh launcher-reset`.

> On some Chromecast / Google TV firmware, the HOME button is hard-wired to the stock launcher. If HOME still opens Google TV, use a launcher with an "override home button" accessibility option (Projectivy has one).

### Xiaomi TVs: Netflix / Prime Video come back after a reboot?

A hidden system app, `com.mstar.netflixobserver`, re-installs them at every boot, and it also runs the remote's Netflix button. `gtv status` shows apps that came back, and `gtv recheck` removes them again. To stop it for good, uncomment that line in [`lists/xiaomi.txt`](lists/xiaomi.txt) and run `gtv debloat xiaomi`.

## Undo / troubleshooting

Something not working? Run `gtv doctor` first, then see **[docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md)**. It covers every error we know of, with the fix.

- **Undo everything:** `gtv restore` then `gtv reboot`.
- **Bring back removed apps:** `gtv bring-back` (pick them from a numbered list).
- **Back to the stock home screen:** `gtv launcher-reset`.
- **Reinstall a single app:** `adb shell cmd package install-existing <package>`
- **Worst case:** a factory reset restores every app, because nothing is deleted from the system partition.

Backups are stored in `backups/` (git-ignored). `original` always points to the state before your first change, and `latest` points to the newest snapshot.

## Update

```bash
cd Debloat-google-TV && git pull
gtv glass          # installs the newest Glass Launcher; your settings are restored
```

## Fork or build it yourself

The Glass Launcher is built by GitHub Actions (`.github/workflows/glass-launcher.yml`) and published as `glass-launcher.apk` on the **glass-latest** release. In a fork, enable Actions and push; `gtv` downloads from `GLASS_URL`, which you can point at your own release: `GLASS_URL=https://github.com/<you>/Debloat-google-TV/releases/download/glass-latest/glass-launcher.apk gtv glass`. To build locally, see [glass-launcher/README.md](glass-launcher/README.md).

Contributions are welcome. Before you open a pull request, run `bash -n gtv.sh && shellcheck gtv.sh setup-*.sh` and test with `-n` (dry run).

## Credits

- Device knowledge (Xiaomi's Netflix observer, extra Xiaomi/Google tracking packages, which apps handle HDMI) is partly from the device lists of [CleanYourTV](https://github.com/Gathin23/cleanyourtv) (MIT) and [Glass TV](https://github.com/aqmangge/glass-tv-home).
- Official Malaysian prayer times come from [JAKIM e-Solat](https://www.e-solat.gov.my/).

## License

[MIT](LICENSE). Free to use, copy and change.

## Disclaimer

Use at your own risk. Package names differ between manufacturers and firmware versions. Packages that aren't installed are skipped. Always try `-n` (dry run) first.
