# Step 1: Get your TV ready (everyone does this first)

Do this on the TV with its remote. It takes about 3 minutes. Then pick your device in the main [README](../README.md#2-pick-your-device): Android phone, iPhone/iPad, or computer.

**Works on:** Android TV and Google TV (Chromecast with Google TV, Google TV Streamer, Sony, TCL, Xiaomi/Mi TV, Philips, Hisense, Mi Box, Nvidia Shield and similar).
**Not for:** Samsung (Tizen), LG (webOS) and Roku TVs. They don't run Android.

You need two things before you start:

- The TV and your phone or computer on the **same Wi-Fi network** (or the TV on a cable to the same router).
- The TV turned on and awake.

---

## 1. Turn on Developer options

| Your TV | Where to go |
|---|---|
| **Google TV** (colourful home screen with "For you" tabs) | Settings → **System** → **About** |
| **Older Android TV** (rows of apps on a dark background) | Settings → **Device Preferences** → **About** |

Scroll down and click **Android TV OS build** (or just **Build**) **7 times** with the OK button. A message says *"You are now a developer"*. Press Back.

## 2. Turn on debugging

Go back to Settings, and open **Developer options**. It is next to *About*, in the same menu:

- **Google TV:** Settings → System → Developer options
- **Older Android TV:** Settings → Device Preferences → Developer options

Turn on:

- **USB debugging** (always)
- **Wireless debugging** (only if you see it. It appears on Android 11 and newer, which includes every Google TV)

> Some TVs call it **Network debugging** or **ADB debugging**. Turn it on too.

## 3. Find your TV's IP address

Settings → **Network & Internet** → click the network you are connected to. Write down the **IP address**. It looks like `192.168.1.50`.

(Another place: Settings → System → About → Status.)

## 4. Which way will you connect?

The program (`gtv`) connects in one of two ways. The TV decides which one:

| What you see in Developer options | Use | In the `gtv` menu |
|---|---|---|
| Only **USB debugging** or **Network debugging** | Connect with the IP address | **1) Connect to TV** |
| **Wireless debugging** (Google TV, Chromecast with Google TV, any Android 11+) | Pair once with a code, then connect | **2) Pair TV** |

If you don't know, open Wireless debugging. If it is there, use option **2**.

### How pairing works (option 2)

Do this when the install guide tells you to choose **2**:

1. On the TV open **Developer options → Wireless debugging** and turn it **on**.
2. Look at the main Wireless debugging screen. It shows **IP address & Port**, for example `192.168.1.50:38123`. This is the *connect address*. Write it down.
3. Click **Pair device with pairing code**. A box appears with a **6-digit code** and a *different* **IP address & Port**, for example `192.168.1.50:41235`. This is the *pairing address*.
4. **Leave that box open on the TV.** It disappears if you press Back or wait too long.
5. Now type into `gtv`:
   1. the **pairing address** (`192.168.1.50:41235`) and the **6-digit code**,
   2. then the **connect address** from step 2 (`192.168.1.50:38123`).
6. The first time, the TV asks **Allow debugging?** Tick **Always allow** and press **Allow**.

The two ports are different numbers. That is normal. The ports also change each time Wireless debugging is turned off and on, so if you come back later and it says it can't connect, open the Wireless debugging screen again and use the new port.

---

## Before you start the big step

- **Keep the TV awake.** If the screen goes dark in the middle, press any button on the remote.
- **Don't unplug the TV** while the program works.
- Nothing is deleted for good. See [Undo](../README.md#undo--troubleshooting).

Next: your device guide:

- [Android phone](INSTALL-ANDROID-PHONE.md)
- [iPhone or iPad](INSTALL-IPHONE-IPAD.md)
- [Windows, Mac or Linux computer](INSTALL-LAPTOP.md)
