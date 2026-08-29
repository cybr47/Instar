/*
 * Piko-derived MetaConfig mapping support.
 * Copyright (C) 2026 piko contributors <https://github.com/crimera/piko>
 * See NOTICE for the GPLv3 section 7(b) attribution requirement.
 */
package com.cybr47.instar.mods.devops.config;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.json.JSONArray;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import com.cybr47.instar.R;
import com.cybr47.instar.utils.i18n.I18n;
import com.cybr47.instar.utils.log.ModuleLog;

/** Installs Instagram's id-to-name mapping used by the MetaConfig UI. */
public final class MappingManager {

    public static final String ACTION_IMPORT_MAPPING =
            "com.cybr47.instar.ACTION_IMPORT_MAPPING";
    public static final String ACTION_DOWNLOAD_MAPPING =
            "com.cybr47.instar.ACTION_DOWNLOAD_MAPPING";

    private static final long MIN_MAPPING_BYTES = 10L * 1024L;
    private static final int MAX_MAPPING_BYTES = 8 * 1024 * 1024;
    private static final String[] MAPPING_BASE_URLS = {
            "https://raw.githubusercontent.com/crimera/piko/dev/docs/mappings/",
            "https://raw.githubusercontent.com/crimera/piko/main/docs/mappings/"
    };

    private static volatile boolean requestRunning;

    private MappingManager() {}

    public static void ensureMappingAsync(Context context) {
        if (hasUsableMapping(context)) return;
        downloadMappingAsync(context, false);
    }

    public static void downloadMappingAsync(Context context, boolean notifyUser) {
        Context appContext = context.getApplicationContext();
        if (requestRunning) {
            if (notifyUser) showToast(appContext, R.string.ig_mapping_download_in_progress);
            return;
        }
        requestRunning = true;

        new Thread(() -> {
            try {
                String versionName = getInstagramVersion(appContext);
                String json = downloadVersionMapping(versionName);
                writeMappingJson(appContext, json);
                ModuleLog.line("(Instar | Mapping): Installed Piko mapping for Instagram "
                        + versionName + " (" + json.getBytes(StandardCharsets.UTF_8).length + " bytes)");
                if (notifyUser) showToast(appContext, R.string.ig_mapping_download_success, versionName);
            } catch (Throwable error) {
                ModuleLog.line("(Instar | Mapping): Download failed: " + error.getMessage());
                if (notifyUser) showToast(appContext, R.string.ig_mapping_download_failed,
                        String.valueOf(error.getMessage()));
            } finally {
                requestRunning = false;
            }
        }, "Instar-mapping-download").start();
    }

    public static void importMappingFromJson(Context context, String json) {
        Context appContext = context.getApplicationContext();
        new Thread(() -> {
            try {
                writeMappingJson(appContext, json);
                ModuleLog.line("(Instar | Mapping): Imported id_name_mapping.json");
                showToast(appContext, R.string.ig_mapping_import_success);
            } catch (Throwable error) {
                ModuleLog.line("(Instar | Mapping): Import failed: " + error.getMessage());
                showToast(appContext, R.string.ig_mapping_import_failed,
                        String.valueOf(error.getMessage()));
            }
        }, "Instar-mapping-import").start();
    }

    public static boolean hasUsableMapping(Context context) {
        File file = mappingFile(context);
        return file.isFile() && file.length() > MIN_MAPPING_BYTES;
    }

    private static String getInstagramVersion(Context context) throws Exception {
        PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        String version = info.versionName;
        if (version == null || !version.matches("[0-9]+(?:\\.[0-9]+)+")) {
            throw new IllegalStateException("Unsupported Instagram version name: " + version);
        }
        return version;
    }

    private static String downloadVersionMapping(String versionName) throws Exception {
        Throwable lastError = null;
        for (String baseUrl : MAPPING_BASE_URLS) {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(baseUrl + versionName + ".json")
                        .openConnection();
                connection.setConnectTimeout(12_000);
                connection.setReadTimeout(25_000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("User-Agent", "Instar/0.1");
                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new IllegalStateException("HTTP " + responseCode);
                }
                try (InputStream input = connection.getInputStream()) {
                    return readLimited(input);
                }
            } catch (Throwable error) {
                lastError = error;
            } finally {
                if (connection != null) connection.disconnect();
            }
        }
        throw new IllegalStateException(lastError == null
                ? "No mapping source available" : lastError.getMessage(), lastError);
    }

    private static String readLimited(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > MAX_MAPPING_BYTES) throw new IllegalArgumentException("Mapping is too large");
            output.write(buffer, 0, read);
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }

    private static void writeMappingJson(Context context, String rawJson) throws Exception {
        if (rawJson == null) throw new IllegalArgumentException("Empty mapping");
        String json = rawJson.trim();
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= MIN_MAPPING_BYTES) {
            throw new IllegalArgumentException("Mapping is empty or incomplete");
        }

        JSONArray entries = new JSONArray(json);
        if (entries.length() < 100) throw new IllegalArgumentException("Too few mapping entries");
        for (int i = 0; i < Math.min(entries.length(), 50); i++) {
            Object entry = entries.get(i);
            if (!(entry instanceof String) || !((String) entry).contains(":")) {
                throw new IllegalArgumentException("Invalid mapping entry at index " + i);
            }
        }

        File destination = mappingFile(context);
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Could not create mobileconfig directory");
        }

        File temporary = new File(parent, "id_name_mapping.json.tmp");
        try (FileOutputStream output = new FileOutputStream(temporary, false)) {
            output.write(bytes);
            output.flush();
            output.getFD().sync();
        }
        try {
            Files.move(temporary.toPath(), destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary.toPath(), destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            if (temporary.exists()) temporary.delete();
        }
    }

    private static File mappingFile(Context context) {
        return new File(context.getFilesDir(), "mobileconfig/id_name_mapping.json");
    }

    private static void showToast(Context context, int stringId, Object... args) {
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(context,
                I18n.t(context, stringId, args), Toast.LENGTH_LONG).show());
    }
}
