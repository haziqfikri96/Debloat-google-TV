# Install from an iPhone or iPad

iPhones can't run Android tools directly. The free app **iSH Shell** runs a tiny Linux on your iPhone/iPad, and that Linux has Android's `adb` tool. Nothing is installed on iOS itself, and nothing needs a jailbreak.

You need: an iPhone or iPad, the same Wi-Fi as the TV, and about 15 minutes. iSH is slower than a computer, but every step works the same.

Before you start, finish **[Step 1: Get your TV ready](TV-SETUP.md)**. You should know whether your TV uses **Connect** (option 1) or **Pair** (option 2).

---

## 1. Stop the screen locking (just for now)

iOS pauses iSH when the screen locks, which stops the install half way. Open **Settings → Display & Brightness → Auto-Lock** and choose **Never**. You can set it back afterwards.

## 2. Install iSH

Install **iSH Shell** from the App Store: <https://apps.apple.com/app/ish-shell/id1436902243> (free). Open it. You see a black screen with `localhost:~#`.

## 3. Set up iSH and download the program

Copy this whole box, then **long-press** the iSH screen and tap **Paste**, then press **Return**:

```sh
apk update && apk add git && git clone https://github.com/haziqfikri96/Debloat-google-TV.git
cd Debloat-google-TV && sh setup-ios.sh
```

What to expect:

- It takes **1 to 3 minutes** and prints a lot of text. iSH is slow; wait until you see `localhost:~/Debloat-google-TV#` again.
- At the end it says `Done! adb ... is ready.`
- When iOS asks *"iSH would like to find and connect to devices on your local network"*, tap **Allow**. If you missed it: **Settings → Privacy & Security → Local Network → turn iSH on**. Without this, iSH can't see the TV.

## 4. Check everything (recommended)

```sh
gtv doctor
```

It shows a tick for each thing that is ready, and tells you how to fix anything that is not. Right now only *"No TV chosen yet"* is expected.

## 5. Connect to the TV

Type `gtv` and press Return to open the menu:

```
Debloat Google TV   TV: not connected
  1) Connect to TV
  2) Pair TV (Google TV 12+ wireless debugging)
  ...
```

- **TV with USB / Network debugging only:** type `1`. Enter the TV's IP address (for example `192.168.1.50`). **Look at the TV** and press **Allow** (tick *Always allow*).
- **TV with Wireless debugging (Google TV):** type `2` and follow [How pairing works](TV-SETUP.md#how-pairing-works-option-2). You type the pairing address and code, then the connect address.

> **Tip:** to switch between iSH and the TV's settings screen, use the iPhone's app switcher, and keep iSH close at hand. If you leave iSH for too long, iOS may close it. If that happens, open iSH, type `gtv`, and carry on. The TV address is remembered.

When it works you see `✔ Connected to 192.168.1.50:...`.

## 6. Make the TV fast

Type `3` (*Make it fast*) and press Return. It saves a backup first, then removes bloat and applies speed tweaks. Confirm with `y`.

- It takes **5 to 15 minutes**, mostly the step *"pre-compiling apps"*.
- **Keep iSH open on screen** until it finishes.
- If the TV screen goes dark, press any button on the remote.

## 7. Install the glass home screen

Type `g`. It downloads Glass Launcher, installs it on the TV and makes it your home screen. Then set up the extras, whichever you want:

| Menu | What it does |
|---|---|
| `p` | **Prayer times.** Type your **town**, e.g. `Shah Alam`. (iSH can't read the iPhone's GPS, so `gps` is not available.) |
| `h` | **Google Home** button. |
| `w` | **Your own wallpaper** (see below). |

### Using a photo from your iPhone as wallpaper

1. Save the photo into the **Files** app, in a folder you can find (for example Downloads). From Photos: open the photo → **Share** → **Save to Files**.
2. In iSH, type this and press Return:
   ```sh
   mount -t ios . /mnt
   ```
3. The Files picker opens. Open the folder with your photo and tap **Open** at the top right. (Don't tap Cancel.)
4. Type `ls /mnt` to see your files. Find the exact name of your photo.
5. Type `gtv wallpaper /mnt/family.jpg` (use your real file name).

If you see `mount: ... No such device`, run the same `mount` command again; the picker should open. If the photo is not listed, you picked the wrong folder: type `umount /mnt` and do step 2 again.

iPhones sometimes add a second ending such as `family.jpg.JPG`. `gtv` finds it even if you leave that out.

## 8. Restart the TV

Type `r` (Reboot). When the TV starts again, press **HOME** on the remote. The glass home screen opens. Done!

Nothing is deleted for good: choose `9` (Undo everything) any time, or see [Undo](../README.md#undo--troubleshooting).

---

## Later

- **Update to the newest version** (in iSH):
  ```sh
  cd Debloat-google-TV && git pull
  gtv glass
  ```
  Your prayer location, wallpaper and settings are restored automatically.
- **Something not working?** Run `gtv doctor`, then see [Troubleshooting](TROUBLESHOOTING.md).
- **`gtv: command not found`?** Run `cd ~/Debloat-google-TV && sh setup-ios.sh` again, or start it with `cd ~/Debloat-google-TV && bash gtv.sh`.
- Remember to set **Auto-Lock** back to your normal time.
