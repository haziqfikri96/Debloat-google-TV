#!/usr/bin/env bash
# gtv.sh — Debloat, speed up and redesign Android TV / Google TV over ADB.
#
# Every change is reversible: apps are removed for user 0 only
# (`pm uninstall --user 0`), never deleted from the system partition, and
# `./gtv.sh restore` puts everything back from the automatic backup.
#
# Usage: ./gtv.sh [-s SERIAL|IP[:PORT]] [-n] <command> [args]
#   Run ./gtv.sh help for the full list.

set -euo pipefail

ROOT="$(cd "$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")" && pwd)"
LISTS="$ROOT/lists"
BACKUPS="$ROOT/backups"
DEVICE_FILE="$ROOT/.gtv-device"   # last TV we connected to (handy on a phone)
DRY_RUN=0
SERIAL="${ANDROID_SERIAL:-}"
IS_TERMUX=0
[[ -n "${TERMUX_VERSION:-}" || -d /data/data/com.termux/files/usr ]] && IS_TERMUX=1

# ---------------------------------------------------------------- output ----
if [[ -t 1 ]]; then
  C_R=$'\e[31m' C_G=$'\e[32m' C_Y=$'\e[33m' C_B=$'\e[36m' C_0=$'\e[0m'
else
  C_R='' C_G='' C_Y='' C_B='' C_0=''
fi
info() { printf '%s==>%s %s\n' "$C_B" "$C_0" "$*"; }
ok()   { printf '  %s✔%s %s\n' "$C_G" "$C_0" "$*"; }
warn() { printf '  %s!%s %s\n' "$C_Y" "$C_0" "$*" >&2; }
die()  { printf '%sError:%s %s\n' "$C_R" "$C_0" "$*" >&2; exit 1; }

confirm() {
  [[ "${ASSUME_YES:-0}" == 1 ]] && return 0
  local reply
  read -r -p "$1 [y/N] " reply
  [[ "$reply" =~ ^[Yy]$ ]]
}

# ------------------------------------------------------------------- adb ----
adb_() {
  if [[ -n "$SERIAL" ]]; then adb -s "$SERIAL" "$@"; else adb "$@"; fi
}

# Run a shell command on the device; prints instead of running in dry-run mode.
sh_() {
  if (( DRY_RUN )); then printf '    [dry-run] adb shell %s\n' "$*"; return 0; fi
  adb_ shell "$@" | tr -d '\r'
}

# Read-only shell command: always runs, even in dry-run mode.
q_() { adb_ shell "$@" | tr -d '\r'; }

require_adb() {
  command -v adb >/dev/null && return
  if (( IS_TERMUX )); then die "adb not found. Run: pkg install android-tools   (or ./setup-phone.sh)"; fi
  die "adb not found. Install Android platform-tools: https://developer.android.com/tools/releases/platform-tools"
}

is_ip() { [[ "$1" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+(:[0-9]+)?$ ]]; }

connect_tv() {
  local addr="${1:-}"
  is_ip "$addr" || die "'$addr' is not an IP address (example: 192.168.1.50 or 192.168.1.50:37123)"
  [[ "$addr" == *:* ]] || addr="$addr:5555"
  info "Connecting to $addr — accept the 'Allow debugging?' prompt on the TV if it appears"
  local out; out="$(adb connect "$addr" 2>&1 || true)"
  if [[ "$out" != *"connected to"* ]]; then
    die "Could not connect: $out
       - Phone and TV must be on the same Wi-Fi.
       - Older TVs: Developer options > Network debugging (port 5555).
       - Google TV 12+: Developer options > Wireless debugging. Pair first
         (./gtv.sh pair <IP:PAIR-PORT> <CODE>), then connect to the IP:PORT shown there."
  fi
  SERIAL="$addr"
  echo "$addr" > "$DEVICE_FILE"
}

require_device() {
  require_adb
  # Fall back to the TV remembered from the last connect.
  if [[ -z "$SERIAL" && -s "$DEVICE_FILE" ]]; then SERIAL="$(cat "$DEVICE_FILE")"; fi
  if is_ip "$SERIAL"; then
    [[ "$SERIAL" == *:* ]] || SERIAL="$SERIAL:5555"
    adb connect "$SERIAL" >/dev/null 2>&1 || true
  fi
  local state
  state="$(adb_ get-state 2>&1 || true)"
  [[ "$state" == "device" ]] && return
  if [[ "$state" == *"more than one"* ]]; then
    adb devices -l >&2
    die "Several devices are connected; pick the TV with -s <serial|IP>."
  fi
  if [[ "$state" == *unauthorized* ]]; then
    die "The TV hasn't allowed this device yet. Accept the 'Allow debugging?' prompt on the TV and retry."
  fi
  die "No TV connected. Connect first:  ./gtv.sh connect <TV-IP>
       (Developer options > USB/Network/Wireless debugging must be on; see README)."
}

pair_tv() {
  require_adb
  local addr="${1:-}" code="${2:-}"
  [[ -n "$addr" && -n "$code" ]] || die "Usage: ./gtv.sh pair <IP:PAIRING-PORT> <6-digit-code>
       (TV: Developer options > Wireless debugging > Pair device with pairing code)"
  if ! is_ip "$addr" || [[ "$addr" != *:* ]]; then die "Pairing needs IP:PORT, e.g. 192.168.1.50:41235"; fi
  local out; out="$(adb pair "$addr" "$code" 2>&1 || true)"
  [[ "$out" == *Successfully* ]] || die "Pairing failed: $out"
  ok "Paired. Now connect to the IP:PORT shown on the Wireless debugging screen (not the pairing port)."
}

installed_packages() { q_ pm list packages | sed 's/^package://' | sort; }
installed_for_any() { q_ pm list packages -u | sed 's/^package://' | sort; }

# Strip comments/blank lines from a list file.
read_list() { sed -e 's/#.*//' -e 's/[[:space:]]//g' -e '/^$/d' "$1"; }

is_protected() { read_list "$LISTS/protected.txt" | grep -qxF "$1"; }

# ---------------------------------------------------------------- backup ----
backup() {
  require_device
  local dir i=1; dir="$BACKUPS/$(date +%Y%m%d-%H%M%S)"
  while [[ -e "$dir" ]]; do dir="${dir%-[0-9]}-$i"; i=$((i + 1)); done
  mkdir -p "$dir"
  installed_packages > "$dir/packages.txt"
  q_ pm list packages -d | sed 's/^package://' | sort > "$dir/disabled.txt"
  q_ cmd package resolve-activity --brief -c android.intent.category.HOME \
      -a android.intent.action.MAIN | tail -n1 > "$dir/home.txt" || true
  {
    for k in window_animation_scale transition_animation_scale animator_duration_scale \
             wifi_scan_always_enabled ble_scan_always_enabled; do
      printf 'global %s %s\n' "$k" "$(q_ settings get global "$k")"
    done
    for k in screensaver_enabled screensaver_activate_on_sleep; do
      printf 'secure %s %s\n' "$k" "$(q_ settings get secure "$k")"
    done
  } > "$dir/settings.txt"
  q_ getprop ro.product.model > "$dir/device.txt"
  ln -sfn "$(basename "$dir")" "$BACKUPS/latest"
  # The first backup is the untouched factory state; never overwrite it.
  [[ -e "$BACKUPS/original" ]] || ln -sfn "$(basename "$dir")" "$BACKUPS/original"
  ok "Backup saved to ${dir#"$ROOT"/}"
}

ensure_backup() {
  if [[ ! -e "$BACKUPS/original" ]]; then info "No backup yet — creating one first"; backup; fi
}

# --------------------------------------------------------------- debloat ----
profile_files() {
  case "$1" in
    safe)        echo safe ;;
    recommended) echo safe recommended ;;
    aggressive)  echo safe recommended aggressive ;;
    custom)      echo custom ;;
    *) if [[ -f "$1" ]]; then echo "$1"; else die "Unknown profile '$1' (safe|recommended|aggressive|custom|<file>)"; fi ;;
  esac
}

debloat() {
  local profile="${1:-recommended}"
  local files; files="$(profile_files "$profile")" || exit 1
  require_device
  (( DRY_RUN )) || ensure_backup

  local -a targets=()
  local f path
  for f in $files; do
    path="$f"; [[ -f "$path" ]] || path="$LISTS/$f.txt"
    mapfile -t -O "${#targets[@]}" targets < <(read_list "$path")
  done

  local installed; installed="$(installed_packages)"
  local -a todo=()
  local p
  for p in "${targets[@]}"; do
    if is_protected "$p"; then warn "Skipping protected package $p"; continue; fi
    grep -qxF "$p" <<<"$installed" && todo+=("$p")
  done

  if (( ${#todo[@]} == 0 )); then ok "Nothing to remove for profile '$profile' — already clean."; return; fi

  info "Profile '$profile' will remove ${#todo[@]} package(s) for user 0:"
  printf '    %s\n' "${todo[@]}"
  [[ "$profile" == aggressive ]] && warn "Aggressive removes voice search, Chromecast built-in and TTS."
  (( DRY_RUN )) || confirm "Continue?" || die "Aborted."

  local removed=0 out
  for p in "${todo[@]}"; do
    sh_ am force-stop "$p" >/dev/null 2>&1 || true
    out="$(sh_ pm uninstall -k --user 0 "$p" 2>&1 || true)"
    if (( DRY_RUN )) || [[ "$out" == *Success* ]]; then
      ok "$p"; removed=$((removed + 1))
    else
      # Some system packages refuse uninstall; disabling is the next best thing.
      out="$(sh_ pm disable-user --user 0 "$p" 2>&1 || true)"
      if [[ "$out" == *disabled* ]]; then ok "$p (disabled)"; removed=$((removed + 1))
      else warn "$p: ${out:-failed}"; fi
    fi
  done
  info "Done: $removed/${#todo[@]} removed. Reboot recommended: ./gtv.sh reboot"
}

# ---------------------------------------------------------------- tweaks ----
tweaks() {
  local anim="${1:-0.5}"
  [[ "$anim" =~ ^(0|0\.5|1)$ ]] || die "Animation scale must be 0, 0.5 or 1"
  require_device
  (( DRY_RUN )) || ensure_backup

  info "Animations → ${anim}x (snappier menus)"
  sh_ settings put global window_animation_scale "$anim"
  sh_ settings put global transition_animation_scale "$anim"
  sh_ settings put global animator_duration_scale "$anim"
  ok "animation scales set"

  info "Stopping always-on Wi-Fi/Bluetooth background scanning"
  sh_ settings put global wifi_scan_always_enabled 0
  sh_ settings put global ble_scan_always_enabled 0
  ok "background scanning off"

  info "Disabling screensaver (ambient mode keeps loading images over the network)"
  sh_ settings put secure screensaver_enabled 0
  sh_ settings put secure screensaver_activate_on_sleep 0
  ok "screensaver off"

  info "Trimming app caches"
  sh_ pm trim-caches 999G >/dev/null 2>&1 || true
  ok "caches trimmed"

  info "Pre-compiling apps with ART (speed-profile) — can take several minutes"
  if sh_ cmd package compile -m speed-profile -a >/dev/null 2>&1; then
    ok "apps compiled"
  else
    warn "compile not supported on this Android version — skipped"
  fi
  info "Done. Reboot to apply everything: ./gtv.sh reboot"
}

# -------------------------------------------------------------- launcher ----
STOCK_LAUNCHERS=(com.google.android.apps.tv.launcherx com.google.android.tvlauncher com.google.android.leanbacklauncher)

# Home activities offered by a package, e.g. com.spocky.projengmenu/.ui.MainActivity
home_activity_of() {
  q_ cmd package query-activities --brief -a android.intent.action.MAIN \
     -c android.intent.category.HOME 2>/dev/null | grep "^$1/" | head -n1 || true
}

# Resolve an APK path, also checking the phone's / PC's Downloads folder.
find_apk() {
  local f="$1" d
  [[ -f "$f" ]] && { echo "$f"; return; }
  for d in "$HOME/storage/downloads" /sdcard/Download /storage/emulated/0/Download "$HOME/Downloads"; do
    [[ -f "$d/$(basename "$f")" ]] && { echo "$d/$(basename "$f")"; return; }
  done
  return 1
}

downloaded_apks() {
  local d
  for d in "$HOME/storage/downloads" /sdcard/Download "$HOME/Downloads"; do
    [[ -d "$d" ]] && find "$d" -maxdepth 1 -name '*.apk' 2>/dev/null
  done | sort -u
}

launcher() {
  local target="${1:-}"
  [[ -n "$target" ]] || die "Usage: ./gtv.sh launcher <launcher.apk | package.name>"
  require_device
  (( DRY_RUN )) || ensure_backup

  local pkg="$target"
  if [[ "$target" == *.apk ]]; then
    target="$(find_apk "$target")" || die "APK not found: $1 (looked here and in Downloads)"
    info "Installing $(basename "$target") on the TV"
    local before; before="$(installed_packages)"
    if (( DRY_RUN )); then printf '    [dry-run] adb install -r %s\n' "$target"
    else adb_ install -r "$target" >/dev/null || die "Install failed"; fi
    # Package name: from aapt if available, otherwise whatever is new on the TV
    # (aapt usually isn't installed on a phone).
    pkg="$(aapt dump badging "$target" 2>/dev/null | sed -n "s/^package: name='\([^']*\)'.*/\1/p" || true)"
    if [[ -z "$pkg" ]] && (( ! DRY_RUN )); then
      pkg="$(comm -13 <(echo "$before") <(installed_packages) | head -n1)"
    fi
    if [[ -z "$pkg" ]]; then
      if (( DRY_RUN )); then pkg="<launcher>"
      else read -r -p "Package name of the launcher you just installed: " pkg; fi
    fi
  fi
  is_protected "$pkg" && [[ ! " ${STOCK_LAUNCHERS[*]} " == *" $pkg "* ]] && die "$pkg is not a launcher"

  local activity; activity="$(home_activity_of "$pkg")"
  [[ -n "$activity" || $DRY_RUN == 1 ]] || die "$pkg is not installed or has no HOME activity"

  info "Setting $pkg as the home screen"
  if ! sh_ cmd package set-home-activity "${activity:-$pkg}" >/dev/null 2>&1; then
    warn "set-home-activity unsupported; you will be asked to pick a home app after the next step"
  fi

  info "Disabling the stock launcher(s) so HOME goes to the new one"
  local s installed; installed="$(installed_packages)"
  for s in "${STOCK_LAUNCHERS[@]}"; do
    [[ "$s" == "$pkg" ]] && continue
    if grep -qxF "$s" <<<"$installed"; then
      sh_ pm disable-user --user 0 "$s" >/dev/null && ok "disabled $s"
    fi
  done
  info "Done. Press HOME on the remote. Undo with: ./gtv.sh launcher-reset"
}

launcher_reset() {
  require_device
  info "Re-enabling stock launcher(s)"
  local s all; all="$(installed_for_any)"
  for s in "${STOCK_LAUNCHERS[@]}"; do
    grep -qxF "$s" <<<"$all" || continue
    sh_ pm enable "$s" >/dev/null 2>&1 && ok "enabled $s" || true
    local act; act="$(home_activity_of "$s")"
    [[ -n "$act" ]] && sh_ cmd package set-home-activity "$act" >/dev/null 2>&1 || true
  done
  info "Done. Press HOME on the remote."
}

# --------------------------------------------------------------- restore ----
restore() {
  local which="${1:-original}"
  local dir="$BACKUPS/$which"
  [[ -d "$dir" ]] || die "No backup '$which' in ${BACKUPS#"$ROOT"/}/ (try: ./gtv.sh backups)"
  require_device
  info "Restoring from ${dir#"$ROOT"/} ($(cat "$dir/device.txt" 2>/dev/null))"
  confirm "Reinstall every removed app and reset tweaked settings?" || die "Aborted."

  local now p n=0; now="$(installed_packages)"
  while read -r p; do
    [[ -z "$p" ]] && continue
    if ! grep -qxF "$p" <<<"$now"; then
      sh_ cmd package install-existing --user 0 "$p" >/dev/null 2>&1 \
        || sh_ pm install-existing --user 0 "$p" >/dev/null 2>&1 \
        || { warn "could not restore $p"; continue; }
      ok "restored $p"; n=$((n + 1))
    fi
  done < "$dir/packages.txt"

  # Re-enable anything we disabled that wasn't disabled at backup time.
  local disabled_now; disabled_now="$(q_ pm list packages -d | sed 's/^package://')"
  for p in $disabled_now; do
    grep -qxF "$p" "$dir/disabled.txt" || { sh_ pm enable "$p" >/dev/null 2>&1 && ok "re-enabled $p"; n=$((n + 1)); }
  done

  local ns k v
  while read -r ns k v; do
    if [[ "$v" == null ]]; then sh_ settings delete "$ns" "$k" >/dev/null
    else sh_ settings put "$ns" "$k" "$v"; fi
  done < "$dir/settings.txt"
  ok "settings restored"

  local home; home="$(cat "$dir/home.txt" 2>/dev/null || true)"
  [[ "$home" == */* ]] && sh_ cmd package set-home-activity "$home" >/dev/null 2>&1 || true

  info "Restored $n package(s). Reboot recommended: ./gtv.sh reboot"
}

# ---------------------------------------------------------------- status ----
status() {
  require_device
  info "Device"
  printf '    %-14s %s\n' Model "$(q_ getprop ro.product.model)" \
    Android "$(q_ getprop ro.build.version.release) (SDK $(q_ getprop ro.build.version.sdk))" \
    Packages "$(installed_packages | wc -l | tr -d ' ') installed"
  info "Memory"
  q_ cat /proc/meminfo | awk '/MemTotal|MemAvailable/ {printf "    %-14s %d MB\n", $1, $2/1024}'
  info "Animations"
  printf '    %s / %s / %s\n' "$(q_ settings get global window_animation_scale)" \
    "$(q_ settings get global transition_animation_scale)" "$(q_ settings get global animator_duration_scale)"
  info "Home app"
  printf '    %s\n' "$(q_ cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN | tail -n1)"
  info "Bloat still installed"
  local installed f p any=0; installed="$(installed_packages)"
  for f in safe recommended aggressive; do
    while read -r p; do
      grep -qxF "$p" <<<"$installed" && { printf '    [%s] %s\n' "$f" "$p"; any=1; }
    done < <(read_list "$LISTS/$f.txt")
  done
  (( any )) || ok "none"
  info "Top memory users"
  q_ dumpsys meminfo 2>/dev/null | awk '/Total PSS by process/{f=1;next} f&&NF==0{exit} f' | head -n 8 | sed 's/^ */    /' || true
}

packages() {
  require_device
  if [[ -n "${1:-}" ]]; then installed_packages | grep -i -- "$1" || true; else installed_packages; fi
}

# ------------------------------------------------------------------ menu ----
# Number-driven menu: easy to use from a phone keyboard in Termux.
menu() {
  local choice ip code apk p i cur
  local -a apks
  while true; do
    cur="not connected"
    [[ -s "$DEVICE_FILE" ]] && cur="$(cat "$DEVICE_FILE")"
    [[ -n "$SERIAL" ]] && cur="$SERIAL"
    printf '\n%sDebloat Google TV%s   TV: %s\n' "$C_B" "$C_0" "$cur"
    printf '  %s\n' \
      "1) Connect to TV" \
      "2) Pair TV (Google TV 12+ wireless debugging)" \
      "3) Make it fast (backup + recommended debloat + speed tweaks)" \
      "4) Debloat only (choose level)" \
      "5) Speed tweaks only" \
      "6) New home screen (launcher)" \
      "7) Bring back stock home screen" \
      "8) Status" \
      "9) Undo everything" \
      "r) Reboot TV" \
      "0) Exit"
    read -r -p "Choose: " choice || return 0
    case "$choice" in
      1) read -r -p "TV IP address (TV: Settings > Network): " ip
         if ( connect_tv "$ip" ); then SERIAL="$(cat "$DEVICE_FILE")"; ok "Connected to $SERIAL"; fi ;;
      2) read -r -p "IP:PORT under 'Pair device with pairing code': " ip
         read -r -p "6-digit code: " code
         ( pair_tv "$ip" "$code" ) || continue
         read -r -p "IP:PORT shown on the Wireless debugging screen: " ip
         if ( connect_tv "$ip" ); then SERIAL="$(cat "$DEVICE_FILE")"; ok "Connected to $SERIAL"; fi ;;
      3) ( debloat recommended && tweaks ) || true ;;
      4) echo "  a) safe   b) recommended   c) aggressive (no voice search/casting)   d) custom"
         read -r -p "Level: " p
         case "$p" in a) p=safe ;; b) p=recommended ;; c) p=aggressive ;; d) p=custom ;; *) continue ;; esac
         ( debloat "$p" ) || true ;;
      5) read -r -p "Animation speed: 0 = off, 0.5 = fast (default), 1 = normal: " p
         ( tweaks "${p:-0.5}" ) || true ;;
      6) mapfile -t apks < <(downloaded_apks)
         if (( ${#apks[@]} )); then
           echo "APKs in Downloads:"
           for i in "${!apks[@]}"; do printf '  %d) %s\n' $((i + 1)) "$(basename "${apks[$i]}")"; done
         fi
         read -r -p "Number from the list, an APK path, or an installed package name: " apk
         if [[ "$apk" =~ ^[0-9]+$ ]] && (( apk >= 1 && apk <= ${#apks[@]} )); then apk="${apks[$((apk - 1))]}"; fi
         [[ -n "$apk" ]] && { ( launcher "$apk" ) || true; } ;;
      7) ( launcher_reset ) || true ;;
      8) ( status ) || true ;;
      9) ( restore ) || true ;;
      r|R) ( require_device; info "Rebooting"; adb_ reboot ) || true ;;
      0|q|Q) return 0 ;;
      *) warn "Unknown choice '$choice'" ;;
    esac
  done
}

usage() {
  cat <<EOF
gtv.sh — make Android TV / Google TV fast again (all changes reversible)

Usage: ./gtv.sh                      (interactive menu - easiest on a phone)
       ./gtv.sh [-s SERIAL|IP] [-n] [-y] <command> [args]

Options
  -s SERIAL|IP   Target device (an IP like 192.168.1.50 auto-connects on :5555)
  -n             Dry run: show what would happen, change nothing
  -y             Don't ask for confirmation

Commands
  menu                   Interactive numbered menu (default with no command)
  connect <IP[:PORT]>    Connect to the TV over Wi-Fi and remember it
  pair <IP:PORT> <CODE>  Pair using Wireless debugging (Google TV / Android 11+)
  all [profile]          backup + debloat + tweaks (default profile: recommended)
  debloat [profile]      Remove bloat: safe | recommended | aggressive | custom | <file>
  tweaks [0|0.5|1]       Speed tweaks; arg = animation scale (default 0.5, 0 = off)
  launcher <apk|pkg>     Redesign: install/set a new launcher, disable the stock one
  launcher-reset         Go back to the stock Google TV / Android TV home screen
  backup                 Snapshot packages + settings (done automatically before changes)
  backups                List backups
  restore [name]         Undo everything (default: 'original' = state before first run)
  status                 Device info, memory, remaining bloat
  packages [filter]      List installed packages
  reboot                 Reboot the TV
EOF
}

# ------------------------------------------------------------------ main ----
while getopts ":s:nyh" opt; do
  case "$opt" in
    s) SERIAL="$OPTARG" ;;
    n) DRY_RUN=1 ;;
    y) ASSUME_YES=1 ;;
    h) usage; exit 0 ;;
    *) usage; exit 1 ;;
  esac
done
shift $((OPTIND - 1))

if [[ $# -eq 0 && -t 0 ]]; then cmd=menu; else cmd="${1:-help}"; shift || true; fi
case "$cmd" in
  menu)           menu ;;
  connect)        require_adb; connect_tv "${1:-}"; ok "Connected to $SERIAL (remembered for next time)" ;;
  pair)           pair_tv "$@" ;;
  all)            debloat "${1:-recommended}"; tweaks ;;
  debloat)        debloat "$@" ;;
  tweaks)         tweaks "$@" ;;
  launcher)       launcher "$@" ;;
  launcher-reset) launcher_reset ;;
  backup)         backup ;;
  backups)        found=0
                  for d in "$BACKUPS"/*/; do
                    [[ -d "$d" && ! -L "${d%/}" ]] && { basename "$d"; found=1; }
                  done
                  (( found )) || echo "(none)" ;;
  restore)        restore "$@" ;;
  status)         status ;;
  packages)       packages "$@" ;;
  reboot)         require_device; info "Rebooting"; adb_ reboot ;;
  help|-h|--help) usage ;;
  *)              usage; exit 1 ;;
esac
