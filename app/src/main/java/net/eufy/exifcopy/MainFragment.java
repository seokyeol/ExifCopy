package net.eufy.exifcopy;

import static android.app.Activity.RESULT_OK;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;

import net.eufy.exifcopy.databinding.FragmentMainBinding;
import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainFragment extends Fragment {

    public enum ExifCopyResult {
        SUCCESS,
        FILE_NOT_SELECTED,
        READ_ONLY_URI,
        IO_EXCEPTION
    }

    private static final String TAG = "ExifCopy";
    private static final String PREFS_NAME = "ExifCopyPrefs";
    private static final String KEY_LAST_TARGET_URI = "last_target_uri";
    private static final String KEY_LAST_SOURCE_URI = "last_source_uri";
    private static final String KEY_LAST_SOURCE_FOLDER_URI = "last_source_folder_uri";

    private static final String[] EXIF_TAGS = {
        ExifInterface.TAG_APERTURE_VALUE,
        ExifInterface.TAG_ARTIST,
        ExifInterface.TAG_COPYRIGHT,
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_DEVICE_SETTING_DESCRIPTION,
        ExifInterface.TAG_DIGITAL_ZOOM_RATIO,
        ExifInterface.TAG_DNG_VERSION,
        ExifInterface.TAG_EXIF_VERSION,
        ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
        ExifInterface.TAG_EXPOSURE_INDEX,
        ExifInterface.TAG_EXPOSURE_MODE,
        ExifInterface.TAG_EXPOSURE_PROGRAM,
        ExifInterface.TAG_EXPOSURE_TIME,
        ExifInterface.TAG_FLASH,
        ExifInterface.TAG_FLASH_ENERGY,
        ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
        ExifInterface.TAG_FOCAL_PLANE_RESOLUTION_UNIT,
        ExifInterface.TAG_FOCAL_PLANE_X_RESOLUTION,
        ExifInterface.TAG_FOCAL_PLANE_Y_RESOLUTION,
        ExifInterface.TAG_F_NUMBER,
        ExifInterface.TAG_GAIN_CONTROL,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_AREA_INFORMATION,
        ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_GPS_DEST_BEARING,
        ExifInterface.TAG_GPS_DEST_BEARING_REF,
        ExifInterface.TAG_GPS_DEST_DISTANCE,
        ExifInterface.TAG_GPS_DEST_DISTANCE_REF,
        ExifInterface.TAG_GPS_DEST_LATITUDE,
        ExifInterface.TAG_GPS_DEST_LATITUDE_REF,
        ExifInterface.TAG_GPS_DEST_LONGITUDE,
        ExifInterface.TAG_GPS_DEST_LONGITUDE_REF,
        ExifInterface.TAG_GPS_DIFFERENTIAL,
        ExifInterface.TAG_GPS_DOP,
        ExifInterface.TAG_GPS_IMG_DIRECTION,
        ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_MAP_DATUM,
        ExifInterface.TAG_GPS_MEASURE_MODE,
        ExifInterface.TAG_GPS_PROCESSING_METHOD,
        ExifInterface.TAG_GPS_SATELLITES,
        ExifInterface.TAG_GPS_SPEED,
        ExifInterface.TAG_GPS_SPEED_REF,
        ExifInterface.TAG_GPS_STATUS,
        ExifInterface.TAG_GPS_TIMESTAMP,
        ExifInterface.TAG_GPS_TRACK,
        ExifInterface.TAG_GPS_TRACK_REF,
        ExifInterface.TAG_GPS_VERSION_ID,
        ExifInterface.TAG_IMAGE_DESCRIPTION,
        ExifInterface.TAG_IMAGE_UNIQUE_ID,
        ExifInterface.TAG_ISO_SPEED_RATINGS,
        ExifInterface.TAG_LIGHT_SOURCE,
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MAKER_NOTE,
        ExifInterface.TAG_MAX_APERTURE_VALUE,
        ExifInterface.TAG_METERING_MODE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_OFFSET_TIME,
        ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
        ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
        ExifInterface.TAG_SCENE_CAPTURE_TYPE,
        ExifInterface.TAG_SCENE_TYPE,
        ExifInterface.TAG_SENSING_METHOD,
        ExifInterface.TAG_SHUTTER_SPEED_VALUE,
        ExifInterface.TAG_SUBJECT_AREA,
        ExifInterface.TAG_SUBJECT_DISTANCE,
        ExifInterface.TAG_SUBJECT_DISTANCE_RANGE,
        ExifInterface.TAG_SUBJECT_LOCATION,
        ExifInterface.TAG_SUBSEC_TIME,
        ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
        ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
        ExifInterface.TAG_USER_COMMENT,
        ExifInterface.TAG_WHITE_BALANCE,
        ExifInterface.TAG_WHITE_POINT,
        ExifInterface.TAG_Y_CB_CR_COEFFICIENTS,
        ExifInterface.TAG_Y_CB_CR_POSITIONING,
        ExifInterface.TAG_Y_CB_CR_SUB_SAMPLING
    };

    private FragmentMainBinding binding;

    private ActivityResultLauncher<Intent> startGalleryForTargetIntent;
    private ActivityResultLauncher<Intent> startGalleryForSourceIntent;
    private ActivityResultLauncher<Intent> startTreeForSourceIntent;

    private Uri targetUri = null;
    private Uri sourceUri = null;
    private Uri lastTargetUri = null;
    private Uri lastSourceUri = null;
    private Uri lastSourceFolderUri = null;

    private boolean excludeOrientation = false;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        startGalleryForTargetIntent = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        targetUri = result.getData().getData();
                        if (targetUri != null) {
                            saveLastTargetUri(targetUri);
                            updateTargetUriDisplay();
                        }
                    }
                });

        startGalleryForSourceIntent = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        sourceUri = result.getData().getData();
                        if (sourceUri != null) {
                            saveLastSourceUri(sourceUri);
                            updateSourceUriDisplay();
                        }
                    }
                });

        startTreeForSourceIntent = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri treeUri = result.getData().getData();
                        if (treeUri != null) {
                            saveLastSourceFolderUri(treeUri);
                            try {
                                requireContext().getContentResolver().takePersistableUriPermission(
                                        treeUri,
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                );
                            } catch (SecurityException ignored) {
                            }
                            searchMatchingSourceInTree(treeUri);
                        }
                    }
                });

        binding = FragmentMainBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.selectTargetButton.setOnClickListener(v -> {
            try {
                startGalleryForTargetIntent.launch(createOpenImageIntent(getLastTargetUri()));
            } catch (Exception e) {
                Log.e(TAG, "Failed to launch target image picker", e);
            }
        });

        binding.selectSourceButton.setOnClickListener(v -> {
            try {
                startGalleryForSourceIntent.launch(createOpenImageIntent(getLastSourceUri()));
            } catch (Exception e) {
                Log.e(TAG, "Failed to launch source image picker", e);
            }
        });

        binding.autoFindSourceButton.setOnClickListener(v -> {
            if (targetUri == null) {
                showSnackbar(getString(R.string.target_needs_selection));
                return;
            }
            String targetTs = getExifTimestamp(targetUri);
            if (targetTs == null) {
                showSnackbar(getString(R.string.target_no_timestamp));
                return;
            }

            Intent treeIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Uri lastFolder = getLastSourceFolderUri();
                if (lastFolder != null) {
                    treeIntent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, lastFolder);
                }
            }
            try {
                startTreeForSourceIntent.launch(treeIntent);
            } catch (Exception e) {
                Log.e(TAG, "Failed to launch folder picker", e);
            }
        });

        binding.executeButton.setOnClickListener(v -> {
            if (targetUri == null || sourceUri == null) {
                showSnackbar("Both source and target image should be selected.");
                return;
            }

            binding.executeButton.setEnabled(false);
            showSnackbar("Copying Exif tags...");

            executorService.execute(() -> {
                ExifCopyResult result = copyExifFromSourceToTarget();
                if (getActivity() == null) return;
                requireActivity().runOnUiThread(() -> {
                    if (binding != null) {
                        binding.executeButton.setEnabled(true);
                    }
                    switch (result) {
                        case SUCCESS:
                            showSnackbar("Exif tags were successfully copied.");
                            break;
                        case FILE_NOT_SELECTED:
                            showSnackbar("Both source and target image should be selected.");
                            break;
                        case READ_ONLY_URI:
                            showSnackbar("Selected target image URI is read-only. Please select target from actual storage paths.");
                            break;
                        default:
                            showSnackbar("Something wrong with CopyExif method. Please check the log messages.");
                            break;
                    }
                });
            });
        });

        excludeOrientation = binding.excludeOrientationCheckbox.isChecked();
        binding.excludeOrientationCheckbox.setOnCheckedChangeListener((compoundButton, isChecked) -> {
            excludeOrientation = isChecked;
            if (excludeOrientation) {
                showSnackbar("Orientation tag will not be copied.");
            } else {
                showSnackbar("Orientation tag will be copied.");
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }

    private void updateTargetUriDisplay() {
        if (binding == null || targetUri == null) return;
        String name = getDisplayName(targetUri);
        String ts = getExifTimestamp(targetUri);
        if (ts != null) {
            binding.targetUriText.setText(name + "\n(Timestamp: " + ts + ")");
        } else {
            binding.targetUriText.setText(name);
        }
    }

    private void updateSourceUriDisplay() {
        if (binding == null || sourceUri == null) return;
        String name = getDisplayName(sourceUri);
        String ts = getExifTimestamp(sourceUri);
        if (ts != null) {
            binding.sourceUriText.setText(name + "\n(Timestamp: " + ts + ")");
        } else {
            binding.sourceUriText.setText(name);
        }
    }

    private void showSnackbar(String message) {
        View view = getView();
        if (view != null) {
            Snackbar.make(view, message, Snackbar.LENGTH_LONG).show();
        }
    }

    private Intent createOpenImageIntent(Uri initialUri) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && initialUri != null) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri);
        }
        return intent;
    }

    private String getDisplayName(Uri uri) {
        if (uri == null) return "";
        Context context = getContext();
        if (context != null) {
            try (Cursor cursor = context.getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        String name = cursor.getString(nameIndex);
                        if (name != null) return name;
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to query display name for URI: " + uri, e);
            }
        }
        return uri.getLastPathSegment() != null ? uri.getLastPathSegment() : uri.getPath();
    }

    private String getExifTimestamp(Uri uri) {
        Context context = getContext();
        if (context == null || uri == null) return null;
        try (InputStream ins = context.getContentResolver().openInputStream(uri)) {
            if (ins == null) return null;
            ExifInterface exif = new ExifInterface(ins);
            String dtOriginal = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL);
            if (isValidTimestamp(dtOriginal)) return dtOriginal.trim();

            String dt = exif.getAttribute(ExifInterface.TAG_DATETIME);
            if (isValidTimestamp(dt)) return dt.trim();

            String dtDigitized = exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED);
            if (isValidTimestamp(dtDigitized)) return dtDigitized.trim();
        } catch (Exception e) {
            Log.e(TAG, "Failed to read EXIF timestamp from " + uri, e);
        }
        return null;
    }

    private boolean isValidTimestamp(String ts) {
        return ts != null && !ts.trim().isEmpty() && !ts.startsWith("0000") && !ts.startsWith("    ");
    }

    private boolean isTimestampMatch(String t1, String t2) {
        if (t1 == null || t2 == null) return false;
        String clean1 = t1.trim();
        String clean2 = t2.trim();
        if (clean1.equals(clean2)) return true;

        String norm1 = clean1.replaceAll("[-/.]", ":");
        String norm2 = clean2.replaceAll("[-/.]", ":");
        if (norm1.equals(norm2)) return true;

        Long time1 = parseTimestampMillis(clean1);
        Long time2 = parseTimestampMillis(clean2);
        if (time1 != null && time2 != null) {
            return Math.abs(time1 - time2) <= 1000;
        }
        return false;
    }

    private Long parseTimestampMillis(String str) {
        String[] patterns = {
            "yyyy:MM:dd HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy:MM:dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss"
        };
        for (String pattern : patterns) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.US);
                Date date = sdf.parse(str);
                if (date != null) {
                    return date.getTime();
                }
            } catch (ParseException ignored) {
            }
        }
        return null;
    }

    private static class MatchedImageResult {
        final Uri uri;
        final String displayName;
        final String timestamp;

        MatchedImageResult(Uri uri, String displayName, String timestamp) {
            this.uri = uri;
            this.displayName = displayName;
            this.timestamp = timestamp;
        }
    }

    private static class ScanNode {
        final String docId;
        final int depth;

        ScanNode(String docId, int depth) {
            this.docId = docId;
            this.depth = depth;
        }
    }

    private void searchMatchingSourceInTree(Uri treeUri) {
        if (targetUri == null) {
            showSnackbar(getString(R.string.target_needs_selection));
            return;
        }

        String targetTimestamp = getExifTimestamp(targetUri);
        if (targetTimestamp == null) {
            showSnackbar(getString(R.string.target_no_timestamp));
            return;
        }

        if (binding != null) {
            binding.autoFindSourceButton.setEnabled(false);
            binding.selectSourceButton.setEnabled(false);
            binding.executeButton.setEnabled(false);
        }
        showSnackbar(getString(R.string.searching_timestamp, targetTimestamp));

        executorService.execute(() -> {
            MatchedImageResult match = scanTreeForTimestamp(treeUri, targetTimestamp);
            if (getActivity() == null) return;
            requireActivity().runOnUiThread(() -> {
                if (binding != null) {
                    binding.autoFindSourceButton.setEnabled(true);
                    binding.selectSourceButton.setEnabled(true);
                    binding.executeButton.setEnabled(true);
                }
                if (match != null) {
                    sourceUri = match.uri;
                    saveLastSourceUri(match.uri);
                    updateSourceUriDisplay();
                    showSnackbar(getString(R.string.match_found, match.displayName));
                } else {
                    showSnackbar(getString(R.string.no_match_found, targetTimestamp));
                }
            });
        });
    }

    private MatchedImageResult scanTreeForTimestamp(Uri treeUri, String targetTimestamp) {
        Context context = getContext();
        if (context == null || treeUri == null) return null;

        String rootDocId = DocumentsContract.getTreeDocumentId(treeUri);
        Queue<ScanNode> queue = new LinkedList<>();
        queue.add(new ScanNode(rootDocId, 0));

        final int MAX_DEPTH = 3;
        final String[] projection = new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
        };

        while (!queue.isEmpty() && !Thread.currentThread().isInterrupted()) {
            ScanNode current = queue.poll();
            Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, current.docId);

            try (Cursor cursor = context.getContentResolver().query(childrenUri, projection, null, null, null)) {
                if (cursor == null) continue;

                int idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
                int nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
                int mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE);

                while (cursor.moveToNext() && !Thread.currentThread().isInterrupted()) {
                    String docId = cursor.getString(idCol);
                    String displayName = cursor.getString(nameCol);
                    String mimeType = cursor.getString(mimeCol);

                    if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType)) {
                        if (current.depth < MAX_DEPTH) {
                            queue.add(new ScanNode(docId, current.depth + 1));
                        }
                    } else if (isImage(mimeType, displayName)) {
                        Uri childDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId);
                        String candidateTimestamp = getCandidateTimestampMatchingTarget(childDocUri, targetTimestamp);
                        if (candidateTimestamp != null) {
                            return new MatchedImageResult(childDocUri, displayName, candidateTimestamp);
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Error querying directory children for " + current.docId, e);
            }
        }
        return null;
    }

    private String getCandidateTimestampMatchingTarget(Uri uri, String targetTimestamp) {
        Context context = getContext();
        if (context == null || uri == null) return null;
        try (InputStream ins = context.getContentResolver().openInputStream(uri)) {
            if (ins == null) return null;
            ExifInterface exif = new ExifInterface(ins);
            String[] tags = {
                    ExifInterface.TAG_DATETIME_ORIGINAL,
                    ExifInterface.TAG_DATETIME_DIGITIZED,
                    ExifInterface.TAG_DATETIME
            };
            for (String tag : tags) {
                String val = exif.getAttribute(tag);
                if (isValidTimestamp(val) && isTimestampMatch(targetTimestamp, val)) {
                    return val.trim();
                }
            }
        } catch (Exception e) {
            // Ignore unreadable images and continue scanning
        }
        return null;
    }

    private boolean isImage(String mimeType, String displayName) {
        if (mimeType != null && mimeType.startsWith("image/")) {
            return true;
        }
        if (displayName != null) {
            String lower = displayName.toLowerCase(Locale.US);
            return lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                    || lower.endsWith(".png") || lower.endsWith(".webp")
                    || lower.endsWith(".heic") || lower.endsWith(".dng")
                    || lower.endsWith(".cr2") || lower.endsWith(".nef")
                    || lower.endsWith(".arw") || lower.endsWith(".rw2");
        }
        return false;
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private ExifCopyResult copyExifFromSourceToTarget() {
        if (targetUri == null || sourceUri == null) return ExifCopyResult.FILE_NOT_SELECTED;

        Context context = getContext();
        if (context == null) return ExifCopyResult.IO_EXCEPTION;

        // In Android 10 and older versions, SAF returns read-only URI when a user selects a file from some media categories
        // refer to https://commonsware.com/blog/2019/08/06/ACTION_OPEN_DOCUMENT-may-not-be-read-write.html
        if (!DocumentsContractApi19.canWrite(context, targetUri)) {
            return ExifCopyResult.READ_ONLY_URI;
        }

        try (InputStream ins = context.getContentResolver().openInputStream(sourceUri);
             ParcelFileDescriptor outFd = context.getContentResolver().openFileDescriptor(targetUri, "rw")) {
            if (ins == null || outFd == null) {
                return ExifCopyResult.IO_EXCEPTION;
            }

            ExifInterface originalExif = new ExifInterface(ins);
            ExifInterface newExif = new ExifInterface(outFd.getFileDescriptor());
            copyExifAttributes(originalExif, newExif);
            return ExifCopyResult.SUCCESS;
        } catch (IOException e) {
            Log.e(TAG, "Failed to copy EXIF tags: " + e.getMessage(), e);
            return ExifCopyResult.IO_EXCEPTION;
        }
    }

    private void copyExifAttributes(ExifInterface source, ExifInterface target) throws IOException {
        for (String tag : EXIF_TAGS) {
            copyExifAttribute(source, target, tag);
        }
        if (!excludeOrientation) {
            copyExifAttribute(source, target, ExifInterface.TAG_ORIENTATION);
        }
        target.saveAttributes();
    }

    private void copyExifAttribute(ExifInterface source, ExifInterface target, String tag) {
        String value = source.getAttribute(tag);
        if (value != null) {
            target.setAttribute(tag, value);
            Log.d(TAG, tag + ": " + value);
        }
    }

    private Uri getLastTargetUri() {
        if (lastTargetUri != null) {
            return lastTargetUri;
        }
        Context context = getContext();
        if (context != null) {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String uriStr = prefs.getString(KEY_LAST_TARGET_URI, null);
            if (uriStr != null) {
                lastTargetUri = Uri.parse(uriStr);
            }
        }
        return lastTargetUri;
    }

    private Uri getLastSourceUri() {
        if (lastSourceUri != null) {
            return lastSourceUri;
        }
        Context context = getContext();
        if (context != null) {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String uriStr = prefs.getString(KEY_LAST_SOURCE_URI, null);
            if (uriStr != null) {
                lastSourceUri = Uri.parse(uriStr);
            }
        }
        return lastSourceUri;
    }

    private Uri getLastSourceFolderUri() {
        if (lastSourceFolderUri != null) {
            return lastSourceFolderUri;
        }
        Context context = getContext();
        if (context != null) {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String uriStr = prefs.getString(KEY_LAST_SOURCE_FOLDER_URI, null);
            if (uriStr != null) {
                lastSourceFolderUri = Uri.parse(uriStr);
            }
        }
        return lastSourceFolderUri;
    }

    private void saveLastTargetUri(Uri uri) {
        lastTargetUri = uri;
        Context context = getContext();
        if (context != null && uri != null) {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(KEY_LAST_TARGET_URI, uri.toString()).apply();
        }
    }

    private void saveLastSourceUri(Uri uri) {
        lastSourceUri = uri;
        Context context = getContext();
        if (context != null && uri != null) {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(KEY_LAST_SOURCE_URI, uri.toString()).apply();
        }
    }

    private void saveLastSourceFolderUri(Uri uri) {
        lastSourceFolderUri = uri;
        Context context = getContext();
        if (context != null && uri != null) {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(KEY_LAST_SOURCE_FOLDER_URI, uri.toString()).apply();
        }
    }
}