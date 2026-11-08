#!/data/data/com.termux/files/usr/bin/bash
# setup-phone.sh — one-time setup to run gtv.sh from an Android phone (Termux).
#
#   1. Install Termux from F-Droid or GitHub (the Play Store build is outdated).
#   2. In Termux:
#        pkg install -y git && git clone https://github.com/haziqfikri96/Debloat-google-TV.git
#        cd Debloat-google-TV && bash setup-phone.sh
set -euo pipefail

if [[ -z "${TERMUX_VERSION:-}" && ! -d /data/data/com.termux/files/usr ]]; then
  echo "This script is for Termux on an Android phone. On a PC, install platform-tools and run ./gtv.sh" >&2
  exit 1
fi

echo "==> Installing adb (android-tools) and git"
pkg update -y
pkg install -y android-tools git

echo "==> Granting Termux access to your Downloads folder (tap Allow)"
[[ -d "$HOME/storage/downloads" ]] || termux-setup-storage || true

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
chmod +x "$DIR/gtv.sh"

# Shortcut: type `gtv` from anywhere in Termux.
ln -sf "$DIR/gtv.sh" "$PREFIX/bin/gtv"

echo
echo "Done! adb $(adb version | head -n1 | awk '{print $NF}') is ready."
echo "Start the menu any time by typing:  gtv"
