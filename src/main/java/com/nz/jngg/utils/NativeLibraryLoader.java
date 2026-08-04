package com.nz.jngg.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

public final class NativeLibraryLoader {

    private static final String DLL_RESOURCE = "/dll/windows-x86_64/nng.dll";

    private static boolean loaded;

    private NativeLibraryLoader() {
    }

    public static synchronized void load() {
        if (loaded) {
            return;
        }

        ensureSupportedPlatform();

        try (InputStream dllStream = NativeLibraryLoader.class.getResourceAsStream(DLL_RESOURCE)) {
            if (dllStream == null) {
                throw new UnsatisfiedLinkError("Missing native resource: " + DLL_RESOURCE);
            }

            Path extractedDll = Files.createTempFile("Ngg", ".dll");
            Files.copy(dllStream, extractedDll, StandardCopyOption.REPLACE_EXISTING);
            extractedDll.toFile().deleteOnExit();
            load(extractedDll);
        } catch (IOException exception) {
            UnsatisfiedLinkError error = new UnsatisfiedLinkError("Failed to extract native library");
            error.initCause(exception);
            throw error;
        }
    }

    public static synchronized void load(Path dllPath) {
        if (loaded) {
            return;
        }

        System.load(dllPath.toAbsolutePath().toString());
        loaded = true;
    }

    private static void ensureSupportedPlatform() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String osArch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);

        if (!osName.contains("win")) {
            throw new UnsupportedOperationException("ngg-java native library is only bundled for Windows");
        }

        if (!osArch.equals("amd64") && !osArch.equals("x86_64")) {
            throw new UnsupportedOperationException("ngg-java native library is only bundled for Windows x64");
        }
    }
}
