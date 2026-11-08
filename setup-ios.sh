#!/bin/sh
# setup-ios.sh — one-time setup to run gtv.sh from an iPhone or iPad.
#
# Uses iSH (free on the App Store), which runs a small Alpine Linux where
# Android's adb tool is available. Nothing is installed on iOS itself.
#
#   1. Install "iSH Shell" from the App Store and open it.
#   2. Paste:
#        apk add git && git clone https://github.com/haziqfikri96/Debloat-google-TV.git
#        cd Debloat-google-TV && sh setup-ios.sh
set -e

if [ ! -e /proc/ish ] && ! grep -qi alpine /etc/os-release 2>/dev/null; then
  echo "This script is for iSH on iPhone/iPad. On a phone use setup-phone.sh; on a PC install platform-tools." >&2
  exit 1
fi

echo "==> Installing bash, adb (android-tools), git and curl (a minute or two in iSH)"
apk update
apk add bash android-tools git curl

DIR="$(cd "$(dirname "$0")" && pwd)"
chmod +x "$DIR/gtv.sh"
ln -sf "$DIR/gtv.sh" /usr/local/bin/gtv

echo
echo "Done! adb $(adb version | head -n1 | awk '{print $NF}') is ready."
echo
echo "Before connecting:"
echo "  - When iOS asks to let iSH find devices on your local network, tap Allow"
echo "    (or Settings > Privacy & Security > Local Network > iSH)."
echo "  - Keep iSH open on screen while it works; iOS pauses apps in the background."
echo
echo "Start the menu any time by typing:  gtv"
