package com.virtuallab.client.offline;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ZipSecurityUtils {

    private ZipSecurityUtils() {}

    public static void validateEntryName(String name) throws IOException {
        if (name == null || name.trim().isEmpty()) throw new IOException("Empty zip entry");
        if (name.contains("..") || name.startsWith("/") || name.startsWith("\\") || name.contains(":\\") || name.contains("%2e%2e")) {
            throw new IOException("Suspicious zip entry rejected: " + name);
        }
    }

    public static void unzipSecurely(InputStream zipInputStream, File outputDir) throws IOException {
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IOException("Unable to create output folder");
        }

        Path targetRoot = outputDir.getCanonicalFile().toPath();
        byte[] buffer = new byte[8192];
        try (ZipInputStream zis = new ZipInputStream(zipInputStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                validateEntryName(entry.getName());
                File outFile = new File(outputDir, entry.getName());
                Path normalized = outFile.getCanonicalFile().toPath();
                if (!normalized.startsWith(targetRoot)) {
                    throw new IOException("Zip path traversal blocked: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    if (!outFile.exists() && !outFile.mkdirs()) {
                        throw new IOException("Cannot create folder: " + outFile.getAbsolutePath());
                    }
                } else {
                    File parent = outFile.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) {
                        throw new IOException("Cannot create parent folder: " + parent.getAbsolutePath());
                    }
                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        int read;
                        while ((read = zis.read(buffer)) != -1) {
                            fos.write(buffer, 0, read);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }
}
