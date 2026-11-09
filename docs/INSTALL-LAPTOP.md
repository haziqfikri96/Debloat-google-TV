# Install from a laptop or desktop (Windows, Mac, Linux)

You need: a computer on the same Wi-Fi (or network) as the TV, and about 10 minutes.

Before you start, finish **[Step 1: Get your TV ready](TV-SETUP.md)**. You should know whether your TV uses **Connect** (option 1) or **Pair** (option 2).

Pick your system:

- [Windows](#windows)
- [Mac](#mac)
- [Linux](#linux)

> **Note:** on a computer, start the program with **`./gtv.sh`** (Mac, Linux) or **`bash gtv.sh`** (Windows). Everywhere the guides say `gtv`, type that instead.

---

## Windows

Windows needs two free tools: **Git for Windows** (it also gives you a terminal called *Git Bash*) and Google's **platform-tools** (they contain `adb`).

### 1. Install Git for Windows

Download from <https://git-scm.com/download/win>, run the installer and **click Next through every screen** (the defaults are right).

### 2. Download platform-tools

1. Open <https://developer.android.com/tools/releases/platform-tools> and click **Download SDK Platform-Tools for Windows**. Accept the terms.
2. Open your Downloads folder, right-click the zip, choose **Extract All…**, change the folder to `C:\` and click **Extract**.
3. You should now have `C:\platform-tools\adb.exe`.

### 3. Open Git Bash and make `adb` available

Press the Windows key, type **Git Bash**, and open it. Paste these lines (right-click → Paste), then press Enter:

```bash
echo 'export PATH="/c/platform-tools:$PATH"' >> ~/.bashrc
source ~/.bashrc
adb version
```

You should see `Android Debug Bridge version ...`. If it says `command not found`, check that `C:\platform-tools\adb.exe` exists. If the zip made a double folder (`C:\platform-tools\platform-tools\adb.exe`), use `/c/platform-tools/platform-tools` in the first line instead.

If Windows Firewall asks about `adb.exe`, click **Allow access**.

### 4. Download the program and check it

```bash
cd ~
git clone https://github.com/haziqfikri96/Debloat-google-TV.git
cd Debloat-google-TV
bash gtv.sh doctor
```

At this point only *"No TV chosen yet"* is expected. Go to [Connect and run](#connect-and-run) below.

---

## Mac

### 1. Install Homebrew, a newer bash and adb

macOS ships a very old `bash`, so install a new one. Open **Terminal** (Cmd+Space, type *Terminal*). If you don't have [Homebrew](https://brew.sh) yet, install it by pasting the one-line command from that page. Then run:

```bash
brew install bash
brew install --cask android-platform-tools
```

Close Terminal and open it again.

### 2. Download the program and check it

```bash
git clone https://github.com/haziqfikri96/Debloat-google-TV.git
cd Debloat-google-TV
"$(brew --prefix)/bin/bash" ./gtv.sh doctor
```

(That long first word just means "use the new bash". If `./gtv.sh doctor` alone works for you, you can use the short form.)

At this point only *"No TV chosen yet"* is expected. Go to [Connect and run](#connect-and-run).

> **macOS 15 and newer:** the first time, Mac asks whether Terminal may *find devices on your local network*. Click **Allow**. If you missed it: **System Settings → Privacy & Security → Local Network → turn Terminal on**.

---

## Linux

Debian, Ubuntu, Mint:

```bash
sudo apt update && sudo apt install -y adb git curl
```

Fedora:

```bash
sudo dnf install -y android-tools git curl
```

Arch, Manjaro:

```bash
sudo pacman -S --needed android-tools git curl
```

Then:

```bash
git clone https://github.com/haziqfikri96/Debloat-google-TV.git
cd Debloat-google-TV
./gtv.sh doctor
```

At this point only *"No TV chosen yet"* is expected.

---

## Connect and run

Use `./gtv.sh` (Windows: `bash gtv.sh`). Typing it alone opens the same numbered menu as on a phone. You can use the menu, or the commands below.

### Connect

- **TV with USB / Network debugging only:**
  ```bash
  ./gtv.sh connect 192.168.1.50
  ```
  (use your TV's IP). **Look at the TV** and press **Allow** (tick *Always allow*).
- **TV with Wireless debugging (Google TV):** pair once, then connect. Follow [How pairing works](TV-SETUP.md#how-pairing-works-option-2). With the numbers from the TV:
  ```bash
  ./gtv.sh pair 192.168.1.50:41235 123456      # pairing address + 6-digit code
  ./gtv.sh connect 192.168.1.50:38123           # the "connect" address on the main screen
  ```

Then check that all is well:

```bash
./gtv.sh doctor
```

It should end with `✔ Everything looks good.`

### Make it fast

```bash
./gtv.sh -n all      # a dry run: shows what it WOULD do, changes nothing
./gtv.sh all         # backup + debloat + speed tweaks (5 to 10 minutes; confirm with y)
```

### Install the glass home screen

```bash
./gtv.sh glass
./gtv.sh prayer "Shah Alam"           # your town, or: ./gtv.sh prayer gps  (phone only) / lat,lng
./gtv.sh wallpaper ~/Pictures/my-photo.jpg    # optional
./gtv.sh google-home                  # optional
```

### Restart

```bash
./gtv.sh reboot
```

When the TV starts, press **HOME**. The glass home screen opens. Done!

Nothing is deleted for good: `./gtv.sh restore` undoes everything. See [Undo](../README.md#undo--troubleshooting).

---

## Later

- **Update to the newest version:** `cd Debloat-google-TV && git pull && ./gtv.sh glass` (Windows: `bash gtv.sh glass`). Your settings are restored automatically.
- **Something not working?** Run `./gtv.sh doctor`, then see [Troubleshooting](TROUBLESHOOTING.md).
- **Windows and WSL:** Git Bash (above) is the easiest. WSL works like Linux, but its network is virtual, which sometimes stops `adb` finding the TV. If that happens, use Git Bash.
