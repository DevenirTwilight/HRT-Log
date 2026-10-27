#!/usr/bin/env bash
set -euo pipefail
AUDIT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_DIR="$(git -C "$AUDIT_DIR" rev-parse --show-toplevel)"
AUDIT_WORK="${1:?usage: bash run-probes.sh /absolute/scratch-directory [existing-jars-directory]}"
mkdir -p "$AUDIT_WORK"
AUDIT_WORK="$(cd "$AUDIT_WORK" && pwd)"
AUDIT_DEPS="${2:-$AUDIT_WORK/tools}"
python3 "$AUDIT_DIR/fetch_dependencies.py" "$AUDIT_DEPS"
test "$(git -C "$REPO_DIR" rev-parse HEAD)" = 9fafe3c27d563c32f5638029ff084db3e1b59d7f
cd "$REPO_DIR"
compiler() { java -cp "$AUDIT_DEPS/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 "$@"; }
DOMAIN_DIR=core/domain/src/main/kotlin/net/plainnotes/app/domain
DATA_DIR=core/data/src/main/java/net/plainnotes/app/data
APP_DIR=app/src/main/java/net/plainnotes/app
compiler -classpath "$AUDIT_DEPS/kotlin-stdlib.jar:$AUDIT_DEPS/android-all.jar:$AUDIT_DEPS/bcprov-jdk18on.jar" \
  -d "$AUDIT_WORK/lock.jar" "$APP_DIR/security/AppLock.kt" "$AUDIT_DIR/LockAtomicProbe.kt"
java -cp "$AUDIT_WORK/lock.jar:$AUDIT_DEPS/*" LockAtomicProbeKt | tee "$AUDIT_WORK/lock-atomic.txt"
compiler -classpath "$AUDIT_DEPS/kotlin-stdlib.jar:$AUDIT_DEPS/android-all.jar:$AUDIT_DEPS/kotlinx-coroutines-core-jvm.jar" \
  -d "$AUDIT_WORK/probes.jar" "$DOMAIN_DIR/AttachmentSession.kt" "$DOMAIN_DIR/AttachmentInputStream.kt" \
  "$APP_DIR/attachments/AttachmentFlowController.kt" "$APP_DIR/attachments/ui/MedicalAttachmentsController.kt" \
  "$AUDIT_DIR/stubs/ActivityResult.kt" "$AUDIT_DIR/stubs/Viewer.kt" "$AUDIT_DIR/stubs/Backend.kt" "$AUDIT_DIR/stubs/GatewayInterface.kt" \
  "$AUDIT_DIR/UnknownProbe.kt" "$AUDIT_DIR/BlockingProbe.kt"
java -cp "$AUDIT_WORK/probes.jar:$AUDIT_DEPS/*" net.plainnotes.app.attachments.UnknownProbeKt | tee "$AUDIT_WORK/unknown.txt"
java -cp "$AUDIT_WORK/probes.jar:$AUDIT_DEPS/*" BlockingProbeKt | tee "$AUDIT_WORK/blocking.txt"
compiler -classpath "$AUDIT_DEPS/kotlin-stdlib.jar:$AUDIT_DEPS/tink.jar" -d "$AUDIT_WORK/crypto.jar" \
  "$DOMAIN_DIR/AttachmentSession.kt" "$DOMAIN_DIR/AttachmentInputStream.kt" "$DATA_DIR/AttachmentObjectContext.kt" \
  "$DATA_DIR/AttachmentObjectStreams.kt" "$AUDIT_DIR/CryptoProbe.kt"
java -Xmx192m -cp "$AUDIT_WORK/crypto.jar:$AUDIT_DEPS/*" net.plainnotes.app.data.CryptoProbeKt | tee "$AUDIT_WORK/crypto.txt"
compiler -classpath "$AUDIT_WORK/crypto.jar:$AUDIT_DEPS/kotlin-stdlib.jar:$AUDIT_DEPS/tink.jar:$AUDIT_DEPS/bcprov-jdk18on.jar" \
  -Xfriend-paths="$AUDIT_WORK/crypto.jar" -d "$AUDIT_WORK/collection.jar" \
  "$DATA_DIR/ArchiveKdfWorkspace.kt" "$DATA_DIR/MedicalArchiveCrypto.kt" "$DATA_DIR/MedicalCollectionIndex.kt" \
  "$DATA_DIR/MedicalCollectionWriter.kt" "$DATA_DIR/MedicalArchiveManifest.kt" "$DATA_DIR/AttachmentBackupManifest.kt" \
  "$AUDIT_DIR/stubs/CollectionBoundaries.kt" "$AUDIT_DIR/CollectionCancelProbe.kt"
java -Xmx192m -cp "$AUDIT_WORK/collection.jar:$AUDIT_WORK/crypto.jar:$AUDIT_DEPS/*" \
  net.plainnotes.app.data.CollectionCancelProbeKt | tee "$AUDIT_WORK/collection-cancel.txt"
