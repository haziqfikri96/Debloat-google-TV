# Install from an Android phone

You need: an Android phone (Android 7 or newer), the same Wi-Fi as the TV, and about 10 minutes.

Before you start, finish **[Step 1: Get your TV ready](TV-SETUP.md)**. You should know whether your TV uses **Connect** (option 1) or **Pair** (option 2).

---

## 1. Install Termux (the terminal app)

Termux is a free app that gives your phone a terminal.

> **Do not install Termux from the Play Store.** That version is old and does not work. Use one of these:
>
> - **F-Droid (easiest):** open <https://f-droid.org/packages/com.termux/> on your phone, scroll down and tap **Download APK**.
> - **GitHub:** open <https://github.com/termux/termux-app/releases> and download the file ending in `arm64-v8a.apk` (most phones) or `universal.apk` (if unsure).

Open the downloaded file. If Android says *"For your security, your phone is not allowed to install unknown apps from this source"*, tap **Settings**, turn on **Allow from this source**, then go back and tap **Install**.

## 2. Set up Termux and download the program

Open **Termux**. A black screen with a `$` appears. Copy this whole box, then **long-press** on the Termux screen and tap **Paste**, then press **Enter**:

```bash
pkg update -y && pkg install -y git && git clone https://github.com/haziqfikri96/Debloat-google-TV.git
cd Debloat-google-TV && bash setup-phone.sh
```

What to expect:

- It prints a lot of text for a minute or two. That is normal.
- If it asks something like *"Do you want to continue? [Y/n]"* or about a configuration file, press **Enter** (or type `Y`).
- A box *"Allow Termux to access photos, media and files?"* may appear. Tap **Allow**. (This lets you use photos from your Downloads folder as wallpaper.)
- At the end you see `Done! adb ... is ready.`

If `pkg update` shows errors about mirrors or *"Unable to locate package"*, run `termux-change-repo`, press Enter on the first choice, and try step 2 again.

## 3. Check everything (recommended)

```bash
gtv doctor
```

It prints a green tick for each thing that is ready and tells you exactly how to fix anything that is not. At this point only *"No TV chosen yet"* is expected.

## 4. Connect to the TV

Type `gtv` to open the menu. (You can start it again at any time, from any folder.)

```
Debloat Google TV   TV: not connected
  1) Connect to TV
  2) Pair TV (Google TV 12+ wireless debugging)
  ...
```

- **TV with USB / Network debugging only:** type `1` and press Enter. Enter the TV's IP address (for example `192.168.1.50`). **Look at the TV** and press **Allow** (tick *Always allow*).
- **TV with Wireless debugging (Google TV):** type `2` and press Enter and follow [How pairing works](TV-SETUP.md#how-pairing-works-option-2). You type the pairing address and code, then the connect address.

When it works you see `✔ Connected to 192.168.1.50:...` and the top of the menu shows your TV.

## 5. Make the TV fast

Type `3` (*Make it fast*). It first saves a backup of your TV's current setup, then removes bloat and applies speed tweaks. It asks you to confirm with `y`.

- It can take **5 to 10 minutes**, especially the step *"pre-compiling apps"*.
- **Keep Termux open on the screen** until it finishes. Android may stop Termux if you switch away.
- If the TV screen goes dark, press any button on the remote.

## 6. Install the glass home screen

Type `g`. It downloads Glass Launcher, installs it on the TV and makes it your home screen.

Then set up the extras, whichever you want:

| Menu | What it does |
|---|---|
| `p` | **Prayer times.** Type your town, e.g. `Shah Alam`, or type `gps` to use this phone's location (needs the free **Termux:API** app from F-Droid). |
| `w` | **Your own wallpaper.** Save a photo in your phone's **Downloads** folder, then type its file name, e.g. `family.jpg`. |
| `h` | **Google Home** button (Assistant voice control or the TV's Home panel). |

## 7. Restart the TV

Type `r` (Reboot). When the TV starts again, press **HOME** on the remote. The glass home screen opens.

Done! Nothing is deleted for good: choose `9` (Undo everything) any time, or see [Undo](../README.md#undo--troubleshooting).

---

## Later

- **Update to the newest version:**
  ```bash
  cd ~/Debloat-google-TV && git pull
  gtv glass
  ```
  Your prayer location, wallpaper and settings are restored automatically.
- **Something not working?** Run `gtv doctor`, then see [Troubleshooting](TROUBLESHOOTING.md).
- **`gtv: command not found`?** Run `cd ~/Debloat-google-TV && bash setup-phone.sh` again, or start it with `cd ~/Debloat-google-TV && bash gtv.sh`.

## Tips for Android phones

- On Android 12 and newer, the system may close Termux in the background. In Termux, pull down the notification and tap **Acquire wakelock**. Keep Termux on the screen during the long steps.
- Use the same Wi-Fi as the TV. A phone on mobile data, a guest network or a VPN can't reach the TV.
