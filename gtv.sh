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

if (( BASH_VERSINFO[0] < 4 )); then
  echo "gtv.sh needs bash 4 or newer (you have $BASH_VERSION)." >&2
  echo "On a Mac: brew install bash   then run:  $(command -v brew >/dev/null && echo "\$(brew --prefix)/bin/bash" || echo bash) ./gtv.sh" >&2
  exit 1
fi

# Folder of this script, following the `gtv` symlink (readlink -f is missing on older macOS).
SELF="${BASH_SOURCE[0]}"
while [[ -L "$SELF" ]]; do
  target="$(readlink "$SELF")"
  [[ "$target" == /* ]] && SELF="$target" || SELF="$(dirname "$SELF")/$target"
done
ROOT="$(cd "$(dirname "$SELF")" && pwd)"
LISTS="$ROOT/lists"
BACKUPS="$ROOT/backups"
GLASS_PKG="io.github.debloatgtv.glass"
GLASS_URL="https://github.com/haziqfikri96/Debloat-google-TV/releases/download/glass-latest/glass-launcher.apk"
DEVICE_FILE="$ROOT/.gtv-device"   # last TV we connected to (handy on a phone)
DRY_RUN=0
SERIAL="${ANDROID_SERIAL:-}"
IS_TERMUX=0
[[ -n "${TERMUX_VERSION:-}" || -d /data/data/com.termux/files/usr ]] && IS_TERMUX=1
IS_ISH=0
[[ -e /proc/ish ]] && IS_ISH=1   # iSH on iPhone/iPad

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
  if (( IS_ISH )); then die "adb not found. Run: apk add android-tools   (or sh setup-ios.sh)"; fi
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
       - Phone and TV must be on the same Wi-Fi.$( (( IS_ISH )) && printf '\n       - iPhone/iPad: allow iSH under Settings > Privacy & Security > Local Network.')
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
    mapfile -t -O "${#targets[@]}" targets <<<"$(read_list "$path")"
  done

  local installed; installed="$(installed_packages)"
  local -a todo=()
  local p
  for p in "${targets[@]}"; do
    [[ -z "$p" ]] && continue
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
  for d in "$HOME/storage/downloads" /sdcard/Download /storage/emulated/0/Download "$HOME/Downloads" /mnt; do
    [[ -f "$d/$(basename "$f")" ]] && { echo "$d/$(basename "$f")"; return; }
  done
  return 1
}

downloaded_apks() {
  local d
  for d in "$HOME/storage/downloads" /sdcard/Download "$HOME/Downloads" /mnt; do
    [[ -d "$d" ]] && find "$d" -maxdepth 1 -name '*.apk' 2>/dev/null
  done | sort -u
}

# adb install, retrying after uninstall if an old copy was signed differently.
install_apk() {
  local out pkg
  out="$(adb_ install -r "$1" 2>&1 || true)"
  [[ "$out" == *Success* ]] && return 0
  if [[ "$out" == *UPDATE_INCOMPATIBLE* || "$out" == *INCONSISTENT_CERTIFICATES* ]]; then
    pkg="$(sed -n 's/.*Package \([a-zA-Z0-9_.]*\) signatures.*/\1/p' <<<"$out" | head -n1)"
    [[ -z "$pkg" && "$(basename "$1")" == glass-launcher*.apk ]] && pkg="$GLASS_PKG"
    if [[ -n "$pkg" ]]; then
      warn "An older build of $pkg is signed differently; reinstalling it"
      adb_ uninstall "$pkg" >/dev/null 2>&1 || true
      out="$(adb_ install -r "$1" 2>&1 || true)"
      [[ "$out" == *Success* ]] && return 0
    fi
  fi
  die "Install failed: $out"
}

# ------------------------------------------------------------ glass launcher --
# Finds a Glass Launcher APK: local build, Downloads, or the latest GitHub release.
glass_apk() {
  local f
  for f in "${GLASS_APK:-}" \
           "$ROOT/glass-launcher/app/build/outputs/apk/release/app-release.apk" \
           "$HOME/storage/downloads/glass-launcher.apk" "$HOME/Downloads/glass-launcher.apk"; do
    [[ -n "$f" && -f "$f" ]] && { echo "$f"; return; }
  done
  command -v curl >/dev/null || die "curl not found (Termux: pkg install curl)"
  mkdir -p "$ROOT/.cache"
  f="$ROOT/.cache/glass-launcher.apk"
  info "Downloading Glass Launcher" >&2
  curl -fL --retry 3 -o "$f.part" "$GLASS_URL" >&2 \
    || die "Download failed. Build it yourself (see glass-launcher/README.md) or
       download glass-launcher.apk from the repo's Releases page into Downloads."
  mv "$f.part" "$f"
  echo "$f"
}

glass() {
  require_device
  local apk; apk="$(glass_apk)"
  if [[ "${1:-}" == --replace ]]; then KEEP_STOCK=0 launcher "$apk"; else KEEP_STOCK=1 launcher "$apk"; fi
  sh_ pm grant "$GLASS_PKG" android.permission.ACCESS_COARSE_LOCATION >/dev/null 2>&1 || true
  apply_pending
  # Lets the launcher read a wallpaper pushed with `./gtv.sh wallpaper`.
  sh_ pm grant "$GLASS_PKG" android.permission.READ_MEDIA_IMAGES >/dev/null 2>&1 \
    || sh_ pm grant "$GLASS_PKG" android.permission.READ_EXTERNAL_STORAGE >/dev/null 2>&1 || true
  ok "Glass Launcher is your home screen. Change the look with the Style button,"
  ok "or use your own photo: ./gtv.sh wallpaper <photo.jpg>"
}

wallpaper() {
  local img="${1:-}"
  [[ -n "$img" ]] || die "Usage: ./gtv.sh wallpaper <photo.jpg|png> | --remove"
  require_device
  local pics=/sdcard/Pictures/glass-wallpaper.jpg
  local own="/sdcard/Android/data/$GLASS_PKG/files/wallpaper.jpg"
  if [[ "$img" == --remove ]]; then
    sh_ rm -f "$pics" "$own" >/dev/null 2>&1 || true
    ok "Custom wallpaper removed"
  else
    img="$(find_file "$img")" || die "Image not found: $1 (looked here, in Downloads and Pictures)"
    info "Sending $(basename "$img") to the TV"
    if (( DRY_RUN )); then printf '    [dry-run] adb push %s %s\n' "$img" "$pics"
    else
      adb_ push "$img" "$pics" >/dev/null || die "Could not copy the image to the TV"
      # Also into the app's own folder (readable without any permission).
      sh_ mkdir -p "$(dirname "$own")" >/dev/null 2>&1 || true
      sh_ cp "$pics" "$own" >/dev/null 2>&1 || true
    fi
    sh_ pm grant "$GLASS_PKG" android.permission.READ_MEDIA_IMAGES >/dev/null 2>&1 \
      || sh_ pm grant "$GLASS_PKG" android.permission.READ_EXTERNAL_STORAGE >/dev/null 2>&1 || true
    ok "Wallpaper set (Style: Photo). The glass now frosts your photo."
  fi
  sh_ am force-stop "$GLASS_PKG" >/dev/null 2>&1 || true
  sh_ am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null 2>&1 || true
}

# --------------------------------------------------------------- prayer ----
# Tiny JSON helpers (no jq on a stock Termux / macOS).
json_num() { grep -o "\"$1\":[ ]*-\?[0-9.]*" | head -n1 | sed 's/.*:[ ]*//'; }
json_str() { grep -o "\"$1\":[ ]*\"[^\"]*\"" | head -n1 | sed 's/.*:[ ]*"//; s/"$//'; }
urlencode() { local s="$1" out="" c i; for ((i = 0; i < ${#s}; i++)); do c="${s:i:1}"
  case "$c" in [a-zA-Z0-9.~_-]) out+="$c" ;; ' ') out+="%20" ;; *) out+="$(printf '%%%02X' "'$c")" ;; esac; done; echo "$out"; }

# Sends settings to Glass Launcher on the TV (it applies them instantly).
glass_installed() { q_ pm list packages "$GLASS_PKG" | grep -qxF "package:$GLASS_PKG"; }

PENDING_FILE="$ROOT/.glass-pending"

# Sends settings to Glass Launcher. If it isn't installed yet, saves them and
# returns 1; `glass` applies saved settings right after installing.
glass_config() {
  if (( ! DRY_RUN )) && ! glass_installed; then
    printf '%q ' "$@" >> "$PENDING_FILE"; echo >> "$PENDING_FILE"
    return 1
  fi
  local args="" k v out
  while (( $# )); do
    k="$1" v="$2"; shift 2
    case "$k" in
      lat|lng) args+=" --ef $k $v" ;;
      *) args+=" --es $k $(printf '%q' "$v")" ;;
    esac
  done
  out="$(sh_ "am start -n $GLASS_PKG/.HomeActivity -a io.github.debloatgtv.glass.CONFIG$args" 2>&1 || true)"
  if [[ "$out" == *Error* ]]; then warn "Glass Launcher didn't accept the settings: ${out##*Error: }"; return 2; fi
  return 0
}

# Settings saved before Glass was installed.
apply_pending() {
  [[ -s "$PENDING_FILE" ]] || return 0
  glass_installed || return 0
  local line todo; todo="$(cat "$PENDING_FILE")"; rm -f "$PENDING_FILE"
  while IFS= read -r line; do
    [[ -n "$line" ]] && { eval "glass_config $line" || true; }
  done <<<"$todo"
  ok "Applied your saved settings (prayer location, Google Home, suggestions)"
}

not_yet() {
  ok "$1 saved. The Glass home screen isn't installed yet:"
  ok "choose g in the menu (or ./gtv.sh glass) and it will be applied automatically."
}

prayer() {
  local where="" method="" asr="" zone="" lang="" adjust="" name=""
  while (( $# )); do
    case "$1" in
      --method) method="$2"; shift 2 ;;
      --asr) asr="$2"; shift 2 ;;
      --zone) zone="$2"; shift 2 ;;
      --lang) lang="$2"; shift 2 ;;
      --adjust) adjust="$2"; shift 2 ;;
      --name) name="$2"; shift 2 ;;
      *) where="${where:+$where }$1"; shift ;;
    esac
  done
  [[ -n "$where$method$asr$zone$lang$adjust" ]] || die 'Usage: ./gtv.sh prayer <gps | "City name" | LAT,LNG> [options]
       --zone WLY01        JAKIM zone (Malaysia; normally found automatically)
       --method jakim|muis|kemenag|mwl|isna|egypt|makkah|karachi|turkey|...  (default: auto)
       --asr shafi|hanafi  --lang ms|en  --adjust fajr=+2,isha=-1  --name "Label"'
  require_device

  local lat="" lng="" tz=""
  if [[ "$where" == gps ]]; then
    (( IS_ISH )) && die "iSH can't read the iPhone's GPS. Type your town instead, e.g.: gtv prayer \"Shah Alam\""
    command -v termux-location >/dev/null \
      || die "GPS needs Termux:API: install the Termux:API app, then run: pkg install termux-api"
    info "Getting your phone's location (allow Location for Termux:API)"
    local j; j="$(termux-location -p gps -r once 2>/dev/null || termux-location -p network -r once 2>/dev/null || true)"
    lat="$(json_num latitude <<<"$j")"; lng="$(json_num longitude <<<"$j")"
    [[ -n "$lat" && -n "$lng" ]] || die "Could not read the phone's location. Is Location turned on?"
    name="${name:-My location}"
  elif [[ "$where" =~ ^(-?[0-9]+\.?[0-9]*)[,\ ]+(-?[0-9]+\.?[0-9]*)$ ]]; then
    lat="${BASH_REMATCH[1]}"; lng="${BASH_REMATCH[2]}"
  elif [[ -n "$where" ]]; then
    command -v curl >/dev/null || die "curl not found (Termux: pkg install curl)"
    info "Looking up \"$where\""
    local j; j="$(curl -fsS --max-time 20 "https://geocoding-api.open-meteo.com/v1/search?count=1&name=$(urlencode "$where")" || true)"
    lat="$(json_num latitude <<<"$j")"; lng="$(json_num longitude <<<"$j")"; tz="$(json_str timezone <<<"$j")"
    [[ -n "$lat" ]] || die "Place not found: $where (try a bigger nearby town, or LAT,LNG)"
    local found country; found="$(json_str name <<<"$j")"; country="$(json_str country <<<"$j")"
    name="${name:-$found}"
    ok "Found $found${country:+, $country} ($lat, $lng)"
  fi

  # Time zone of the place (to check the TV's), when the lookup didn't give one.
  if [[ -n "$lat" && -z "$tz" ]] && command -v curl >/dev/null; then
    tz="$(curl -fsS --max-time 15 "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lng&timezone=auto" 2>/dev/null \
      | json_str timezone || true)"
  fi

  local -a cfg=()
  [[ -n "$lat" ]] && cfg+=(lat "$lat" lng "$lng" city "${name:-$where}")
  [[ -n "$method" ]] && cfg+=(method "$method")
  [[ -n "$asr" ]] && cfg+=(asr "$asr")
  [[ -n "$zone" ]] && cfg+=(zone "$zone")
  [[ -n "$lang" ]] && cfg+=(lang "$lang")
  [[ -n "$adjust" ]] && cfg+=(adjust "$adjust")
  local rc=0; glass_config "${cfg[@]}" || rc=$?
  case $rc in
    0) ok "Prayer settings sent to Glass Launcher" ;;
    1) not_yet "Prayer location" ;;
  esac

  # Accuracy checks: the TV's clock and time zone must be right for the times to be right.
  local tv_tz tv_now my_now
  tv_tz="$(q_ getprop persist.sys.timezone)"
  if [[ -n "$tz" && -n "$tv_tz" && "$tz" != "$tv_tz" ]]; then
    warn "TV time zone is $tv_tz but ${name:-this location} is in $tz."
    warn "Fix it on the TV: Settings > System > Date & time > Time zone (or turn on automatic)."
  fi
  if [[ -z "$tz" && -n "$lng" ]]; then
    # Offline sanity check: solar time zone ~ longitude / 15.
    local z h; z="$(q_ date +%z 2>/dev/null)"
    if [[ "$z" =~ ^([+-])([0-9]{2})([0-9]{2})$ ]]; then
      h=$(( 10#${BASH_REMATCH[2]} * 60 + 10#${BASH_REMATCH[3]} )); [[ "${BASH_REMATCH[1]}" == - ]] && h=$(( -h ))
      if awk -v h="$h" -v l="$lng" 'BEGIN { d = h / 60 - l / 15; exit !(d > 2.5 || d < -2.5) }'; then
        warn "TV time zone (UTC$z${tv_tz:+, $tv_tz}) doesn't fit this location."
        warn "Fix it on the TV: Settings > System > Date & time > Time zone (or turn on automatic)."
      fi
    fi
  fi
  tv_now="$(q_ date +%s 2>/dev/null || echo 0)"; my_now="$(date +%s)"
  if [[ "$tv_now" =~ ^[0-9]+$ ]] && (( tv_now > 0 )); then
    local skew=$(( tv_now > my_now ? tv_now - my_now : my_now - tv_now ))
    if (( skew > 90 )); then
      warn "TV clock is $((skew / 60)) min off. Turn on Settings > System > Date & time > Automatic."
    else
      ok "TV clock is correct${tv_tz:+ (time zone $tv_tz)}"
    fi
  fi
  ok "Malaysia: official JAKIM zone times are downloaded automatically. Elsewhere: calculated offline."
}

# ----------------------------------------------------------- google home ----
google_home() {
  require_device
  local all; all="$(installed_packages)"
  info "Checking what Google Home control needs"
  local p missing=0
  for p in com.google.android.katniss com.google.android.apps.mediashell; do
    if grep -qxF "$p" <<<"$all"; then ok "$p present"
    else
      warn "$p was removed (aggressive debloat). Restoring it."
      if sh_ cmd package install-existing --user 0 "$p" >/dev/null 2>&1; then ok "restored $p"
      else warn "could not restore $p"; missing=1; fi
    fi
  done

  info "Looking for the Google TV Home panel"
  local -a panels=()
  local dump; dump="$(q_ dumpsys package 2>/dev/null || true)"
  mapfile -t panels <<<"$(printf '%s\n' "$dump" \
    | grep -oE '[a-z][a-z0-9_.]*/[A-Za-z0-9_.$]*(HomePanel|SmartHome|HomeControl|Dashboard)[A-Za-z0-9_.$]*' \
    | grep -E '^com\.google\.' | sort -u || true)"
  [[ -z "${panels[0]:-}" ]] && panels=()
  local pick=""
  if (( ${#panels[@]} == 1 )); then
    pick="${panels[0]}"
  elif (( ${#panels[@]} > 1 )); then
    local i; for i in "${!panels[@]}"; do printf '    %d) %s\n' $((i + 1)) "${panels[$i]}"; done
    read -r -p "Which one opens the Home panel? (number, Enter to skip): " i || true
    [[ "$i" =~ ^[0-9]+$ ]] && (( i >= 1 && i <= ${#panels[@]} )) && pick="${panels[$((i - 1))]}"
  fi
  if [[ -n "$pick" ]]; then
    glass_config homePanel "$pick" || not_yet "Google Home button setting"
    ok "Google Home button opens $pick"
  else
    glass_config homePanel "" || true
    ok "No separate Home panel app found: the Google Home button starts Google Assistant."
  fi
  echo
  ok "Ways to control your Google Home devices from the TV:"
  echo "    - Google Home button on the Glass home screen (Assistant: \"turn off the lights\")"
  echo "    - Press the mic button on the remote and ask Google"
  echo "    - Long-press HOME for the Home panel (Google TV 12+; needs the stock launcher enabled)"
  (( missing )) && warn "Some Google parts could not be restored; run ./gtv.sh restore if voice control fails."
  return 0
}

suggestions() {
  local mode="${1:-}"
  [[ "$mode" =~ ^(on|off|reset)$ ]] || die "Usage: ./gtv.sh suggestions on|off|reset"
  require_device
  if ! glass_config suggestions "$mode"; then not_yet "Suggestions setting"; return 0; fi
  ok "Suggested apps: $mode"
}

find_file() {
  local f="$1" d
  [[ -f "$f" ]] && { echo "$f"; return; }
  for d in "$HOME/storage/downloads" "$HOME/storage/pictures" /sdcard/Download /sdcard/Pictures /mnt \
           "$HOME/Downloads" "$HOME/Pictures"; do
    [[ -f "$d/$(basename "$f")" ]] && { echo "$d/$(basename "$f")"; return; }
  done
  return 1
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
    else install_apk "$target"; fi
    # Package name: from aapt if available, otherwise whatever is new on the TV
    # (aapt usually isn't installed on a phone).
    pkg="$(aapt dump badging "$target" 2>/dev/null | sed -n "s/^package: name='\([^']*\)'.*/\1/p" || true)"
    if [[ -z "$pkg" ]] && (( ! DRY_RUN )); then
      local p
      for p in $(installed_packages); do
        grep -qxF "$p" <<<"$before" || { pkg="$p"; break; }
      done
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

  if [[ "${KEEP_STOCK:-0}" == 1 ]]; then
    ok "Stock launcher kept enabled (long-press HOME still opens the Google Home panel)."
    warn "If HOME still opens Google TV, run: ./gtv.sh glass --replace"
    return 0
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
    done <<<"$(read_list "$LISTS/$f.txt")"
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
      "g) Glass home screen (frosted-glass launcher)" \
      "w) Glass wallpaper from a photo" \
      "p) Prayer times location" \
      "h) Google Home control" \
      "6) Other launcher (APK or package)" \
      "7) Bring back stock home screen" \
      "8) Status" \
      "9) Undo everything" \
      "r) Reboot TV" \
      "0) Exit"
    read -r -p "Choose: " choice || return 0
    case "$choice" in
      1) read -r -p "TV IP address (TV: Settings > Network; Enter = back): " ip
         [[ -z "$ip" ]] && continue
         if ( connect_tv "$ip" ); then SERIAL="$(cat "$DEVICE_FILE")"; ok "Connected to $SERIAL"; fi ;;
      2) read -r -p "IP:PORT under 'Pair device with pairing code' (Enter = back): " ip
         [[ -z "$ip" ]] && continue
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
      6) mapfile -t apks <<<"$(downloaded_apks)"
         [[ -z "${apks[0]:-}" ]] && apks=()
         if (( ${#apks[@]} )); then
           echo "APKs in Downloads:"
           for i in "${!apks[@]}"; do printf '  %d) %s\n' $((i + 1)) "$(basename "${apks[$i]}")"; done
         fi
         read -r -p "Number from the list, an APK path, or an installed package name: " apk
         if [[ "$apk" =~ ^[0-9]+$ ]] && (( apk >= 1 && apk <= ${#apks[@]} )); then apk="${apks[$((apk - 1))]}"; fi
         [[ -n "$apk" ]] && { ( launcher "$apk" ) || true; } ;;
      g|G) ( glass ) || true ;;
      p|P) echo "  Type 'gps' for this phone's location, a city (e.g. Shah Alam), or LAT,LNG"
           read -r -p "Location: " p
           [[ -n "$p" ]] && { ( prayer "$p" ) || true; } ;;
      h|H) ( google_home ) || true ;;
      w|W) read -r -p "Photo file name in Downloads/Pictures (or full path), or 'remove': " p
           [[ "$p" == remove ]] && p=--remove
           [[ -n "$p" ]] && { ( wallpaper "$p" ) || true; } ;;
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
  glass [--replace]      Redesign: install the frosted-glass home screen (Glass Launcher)
                         --replace also disables the stock launcher (if HOME ignores Glass)
  prayer <gps|city|lat,lng> [opts]  Prayer times location (JAKIM official in Malaysia)
  google-home            Set up Google Home control (Assistant + Home panel)
  suggestions on|off|reset  App icons for popular apps you haven't installed
  wallpaper <img|--remove>  Use your own photo behind the glass
  launcher <apk|pkg>     Use any other launcher instead
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
  glass)          glass "$@" ;;
  prayer)         prayer "$@" ;;
  google-home)    google_home ;;
  suggestions)    suggestions "$@" ;;
  wallpaper)      wallpaper "$@" ;;
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
