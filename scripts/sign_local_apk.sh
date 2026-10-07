#!/usr/bin/env bash
# Deliver locally signed release builds without putting private credentials in CI.
set -euo pipefail
if [[ $# != 5 ]]; then
  echo 'Usage: sign_local_apk.sh BUILD_TOOLS_DIR UNSIGNED_APK KEYSTORE PASSWORD_FILE OUTPUT_APK' >&2
  exit 2
fi
build_tools=$1
unsigned_apk=$2
signing_keystore=$3
password_file=$4
output_apk=$5
expected_certificate=989ba04532e4c3ec11c2de989d5b1905cf67bdc6c449361293ef62b8a59379b1
repo_root=$(cd "$(dirname "$0")/.." && pwd)
for private_file in "$signing_keystore" "$password_file"; do
  resolved=$(realpath "$private_file")
  case "$resolved" in
    "$repo_root"/*) echo 'Signing credentials must be outside the source repository.' >&2; exit 1 ;;
  esac
done
output_parent=$(realpath "$(dirname "$output_apk")")
case "$output_parent" in
  "$repo_root"|"$repo_root"/*) echo 'Delivered APKs must be outside the source repository.' >&2; exit 1 ;;
esac
umask 077
temporary_dir=$(mktemp -d)
trap 'rm -rf "$temporary_dir"' EXIT
"$build_tools/zipalign" -f -P 16 4 "$unsigned_apk" "$temporary_dir/aligned.apk"
"$build_tools/apksigner" sign --ks "$signing_keystore" --ks-key-alias release \
  --ks-pass "file:$password_file" --out "$temporary_dir/signed.apk" "$temporary_dir/aligned.apk"
"$build_tools/apksigner" verify --verbose --print-certs "$temporary_dir/signed.apk" > "$temporary_dir/verification.txt"
certificate=$(sed -n 's/^.*certificate SHA-256 digest: //p' "$temporary_dir/verification.txt" | tr -d ':' | tr '[:upper:]' '[:lower:]' | sort -u)
if [[ "$certificate" != "$expected_certificate" ]]; then
  echo 'Certificate does not match the existing official installation.' >&2
  exit 1
fi
"$build_tools/zipalign" -c -P 16 4 "$temporary_dir/signed.apk"
mv "$temporary_dir/signed.apk" "$output_apk"
echo 'Official certificate verified; signed APK:'
sha256sum "$output_apk"
