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

    private Uri targetUri = null;
    private Uri sourceUri = null;
    private Uri lastTargetUri = null;
    private Uri lastSourceUri = null;

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
                            if (binding != null) {
                                binding.targetUriText.setText(getDisplayName(targetUri));
                            }
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
                            if (binding != null) {
                                binding.sourceUriText.setText(getDisplayName(sourceUri));
                            }
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

        binding.executeButton.setOnClickListener(v -> {
            if (targetUri == null || sourceUri == null) {
                Snackbar.make(v, "Both source and target image should be selected.", Snackbar.LENGTH_LONG).show();
                return;
            }

            binding.executeButton.setEnabled(false);
            Snackbar.make(v, "Copying Exif tags...", Snackbar.LENGTH_SHORT).show();

            executorService.execute(() -> {
                ExifCopyResult result = copyExifFromSourceToTarget();
                if (getActivity() == null) return;
                requireActivity().runOnUiThread(() -> {
                    if (binding != null) {
                        binding.executeButton.setEnabled(true);
                    }
                    View currentView = getView();
                    if (currentView == null) return;

                    switch (result) {
                        case SUCCESS:
                            Snackbar.make(currentView, "Exif tags were successfully copied.", Snackbar.LENGTH_LONG).show();
                            break;
                        case FILE_NOT_SELECTED:
                            Snackbar.make(currentView, "Both source and target image should be selected.", Snackbar.LENGTH_LONG).show();
                            break;
                        case READ_ONLY_URI:
                            Snackbar.make(currentView, "Selected target image URI is read-only. Please select target from actual storage paths.", Snackbar.LENGTH_LONG).show();
                            break;
                        default:
                            Snackbar.make(currentView, "Something wrong with CopyExif method. Please check the log messages.", Snackbar.LENGTH_LONG).show();
                            break;
                    }
                });
            });
        });

        excludeOrientation = binding.excludeOrientationCheckbox.isChecked();
        binding.excludeOrientationCheckbox.setOnCheckedChangeListener((compoundButton, isChecked) -> {
            excludeOrientation = isChecked;
            if (excludeOrientation) {
                Snackbar.make(view, "Orientation tag will not be copied.", Snackbar.LENGTH_SHORT).show();
            } else {
                Snackbar.make(view, "Orientation tag will be copied.", Snackbar.LENGTH_SHORT).show();
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
}