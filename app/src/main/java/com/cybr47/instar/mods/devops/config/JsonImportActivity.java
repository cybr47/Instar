package com.cybr47.instar.mods.devops.config;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import com.cybr47.instar.R;

public class JsonImportActivity extends Activity {

    private static final int PICK_JSON_FILE = 1234;
    static final String ACTION_IMPORT_CONFIG = "com.cybr47.instar.ACTION_IMPORT_CONFIG";
    public static final String EXTRA_IMPORT_TYPE = "import_type";
    public static final String IMPORT_TYPE_MAPPING = "mapping";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/json");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        int title = isMappingImport() ? R.string.json_select_mapping : R.string.json_select_config;
        startActivityForResult(Intent.createChooser(intent, getString(title)), PICK_JSON_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PICK_JSON_FILE) {
            if (resultCode == RESULT_OK && data != null) {
                Uri uri = data.getData();
                try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
                    String json = readStream(inputStream).trim();
                    boolean mappingImport = isMappingImport();
                    boolean validEnvelope = mappingImport
                            ? json.startsWith("[") && json.endsWith("]")
                            : json.startsWith("{") && json.endsWith("}");
                    if (validEnvelope) {
                        String targetPackage = getIntent().getStringExtra("target_package");
                        if (targetPackage == null || targetPackage.isEmpty()) {
                            Toast.makeText(this, getString(R.string.json_target_not_specified), Toast.LENGTH_LONG).show();
                        } else {
                            String action = getIntent().getStringExtra("broadcast_action");
                            if (action == null || action.isEmpty()) {
                                action = mappingImport
                                        ? MappingManager.ACTION_IMPORT_MAPPING
                                        : ACTION_IMPORT_CONFIG;
                            }
                            Intent broadcast = new Intent(action);
                            broadcast.setPackage(targetPackage);
                            broadcast.putExtra("json_content", json);
                            sendBroadcast(broadcast);
                            Toast.makeText(this, getString(R.string.json_sent), Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(this, getString(R.string.json_not_valid), Toast.LENGTH_LONG).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(this, getString(R.string.json_read_failed, e.getMessage()), Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(this, getString(R.string.json_cancelled), Toast.LENGTH_SHORT).show();
            }
        }
        finish();
    }

    private boolean isMappingImport() {
        return IMPORT_TYPE_MAPPING.equals(getIntent().getStringExtra(EXTRA_IMPORT_TYPE));
    }

    @SuppressLint("NewApi")
    private String readStream(InputStream inputStream) {
        Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8).useDelimiter("\\A");
        return scanner.hasNext() ? scanner.next() : "";
    }
}
