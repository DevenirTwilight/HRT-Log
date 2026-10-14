package net.plainnotes.app.data;

/** Test-only attestation. Device names/fingerprints are not reliable emulator identity. */
public final class Gate2EmulatorGuard {
    private Gate2EmulatorGuard() {}
    public static void requireEmulator() {
        try {
            Process process = new ProcessBuilder("/system/bin/getprop", "ro.kernel.qemu").start();
            String value;
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                value = reader.readLine();
            }
            if (process.waitFor() != 0 || !"1".equals(value))
                throw new IllegalStateException("Synthetic emulator only: ro.kernel.qemu must be 1");
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot attest synthetic emulator", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Synthetic emulator attestation interrupted", e);
        }
    }
}
