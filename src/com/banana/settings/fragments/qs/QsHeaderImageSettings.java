/*
 * Copyright (C) 2023 crDroid Android Project
 * Copyright (C) 2023 AlphaDroid
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.banana.settings.fragments.qs;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.MediaStore;
import android.provider.SearchIndexableResource;
import android.provider.Settings;
import android.view.View;

import androidx.preference.Preference;
import androidx.preference.ListPreference;
import androidx.preference.SwitchPreference;

import com.android.internal.logging.nano.MetricsProto;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import com.banana.support.colorpicker.ColorPickerPreference;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import android.util.Log;

@SearchIndexable
public class QsHeaderImageSettings extends SettingsPreferenceFragment
             implements Preference.OnPreferenceChangeListener{

    private static final String KEY_TINT = "qs_header_image_tint";
    private static final String KEY_TINT_CUSTOM = "qs_header_image_tint_custom";
    private static final String KEY_HEADER_ENABLED = "qs_header_image_enabled";
    private static final String KEY_HEADER_IMAGE = "qs_header_image";
    private static final String KEY_HEADER_IMAGE_URI = "qs_header_image_uri";

    private static final int REQUEST_IMAGE_PICKER = 10001;

    private final int OPTION_TINT_CUSTOM = 4;
    private final int OPTION_DISABLED_VALUE = 0;
    private final int OPTION_CUSTOM_IMAGE_VALUE = -1;

    private static final String SHARED_PREFERENCES_NAME = "QsHeaderImageSettings";
    private SharedPreferences mSharedPreferences;

    private ListPreference mTintOptions;
    private ColorPickerPreference mColorPicker;
    private SwitchPreference mMasterSwitch;
    private Preference mQsHeaderImagePicker;

    private boolean mQsHeaderImageEnabled;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.qs_header_image_settings);
        ContentResolver resolver = getActivity().getContentResolver();

        mMasterSwitch = findPreference(KEY_HEADER_ENABLED);
        boolean enabled = Settings.System.getIntForUser(resolver,
                        Settings.System.QS_HEADER_IMAGE,
                        OPTION_DISABLED_VALUE, UserHandle.USER_CURRENT) != 0;
        mMasterSwitch.setChecked(enabled);
        mMasterSwitch.setOnPreferenceChangeListener(this);

        mTintOptions = findPreference(KEY_TINT);
        mTintOptions.setOnPreferenceChangeListener(this);

        mColorPicker = findPreference(KEY_TINT_CUSTOM);
        int color = Settings.System.getInt(getContentResolver(),
                Settings.System.QS_HEADER_IMAGE_TINT_CUSTOM, 0xffffffff);
        mColorPicker.setNewPreviewColor(color);
        mColorPicker.setAlphaSliderEnabled(true);
        String colorHex = String.format("#%08x", (0xffffffff & color));
        if (colorHex.equals("#ffffffff")) {
            mColorPicker.setSummary(R.string.color_default);
        } else {
            mColorPicker.setSummary(colorHex);
        }
        mColorPicker.setEnabled(Settings.System.getIntForUser(
                resolver, Settings.System.QS_HEADER_IMAGE_TINT,
                0, UserHandle.USER_CURRENT) == OPTION_TINT_CUSTOM);
        mColorPicker.setOnPreferenceChangeListener(this);

        mQsHeaderImagePicker = findPreference(KEY_HEADER_IMAGE_URI);

        mSharedPreferences = getActivity().getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference == mQsHeaderImagePicker) {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.setType("image/*");
            startActivityForResult(intent, 10001);
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        ContentResolver resolver = getActivity().getContentResolver();
        if (preference == mMasterSwitch) {
            String uri = "";
            mQsHeaderImageEnabled = (Boolean) newValue;
            if (mQsHeaderImageEnabled) {
                int savedHeaderValue = mSharedPreferences.getInt(KEY_HEADER_IMAGE, OPTION_DISABLED_VALUE);
                if (savedHeaderValue != OPTION_DISABLED_VALUE) {
                    Settings.System.putIntForUser(resolver,
                            Settings.System.QS_HEADER_IMAGE,
                            savedHeaderValue, UserHandle.USER_CURRENT);
                }

                if (savedHeaderValue == OPTION_CUSTOM_IMAGE_VALUE) {
                    uri = mSharedPreferences.getString(KEY_HEADER_IMAGE_URI, "");
                    Settings.System.putStringForUser(resolver,
                            Settings.System.QS_HEADER_IMAGE_URI, uri,
                            UserHandle.USER_CURRENT);
                }
            }
            else {
                int currentHeaderValue = Settings.System.getIntForUser(resolver,
                        Settings.System.QS_HEADER_IMAGE, OPTION_DISABLED_VALUE,
                        UserHandle.USER_CURRENT);
                mSharedPreferences.edit().putInt(KEY_HEADER_IMAGE, currentHeaderValue).apply();
                Settings.System.putIntForUser(resolver, Settings.System.QS_HEADER_IMAGE,
                        OPTION_DISABLED_VALUE, UserHandle.USER_CURRENT);

                if (currentHeaderValue == OPTION_CUSTOM_IMAGE_VALUE)
                    uri = Settings.System.getStringForUser(resolver,
                            Settings.System.QS_HEADER_IMAGE_URI,
                            UserHandle.USER_CURRENT);
                            if (uri != null) {
                                mSharedPreferences.edit().putString(
                                        KEY_HEADER_IMAGE_URI, uri).apply();
                    Settings.System.putStringForUser(resolver,
                            Settings.System.QS_HEADER_IMAGE_URI, "",
                            UserHandle.USER_CURRENT);
                }
            }
            return true;
        } else if (preference == mTintOptions) {
            int tintOption = Integer.valueOf((String) newValue);
            mColorPicker.setEnabled(tintOption == OPTION_TINT_CUSTOM);
            return true;
        } else if (preference == mColorPicker) {
            int color = (Integer) newValue;
            String colorHex = ColorPickerPreference.convertToARGB(color);
            if (colorHex.equals("#ffffffff")) {
                preference.setSummary(R.string.color_default);
            } else {
                preference.setSummary(colorHex);
            }
            Settings.System.putIntForUser(resolver,
                    Settings.System.QS_HEADER_IMAGE_TINT_CUSTOM,
                    color, UserHandle.USER_CURRENT);
            return true;
        }
        return false;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent result) {
        super.onActivityResult(requestCode, resultCode, result);

        if (requestCode == 10001) {
            if (resultCode != Activity.RESULT_OK) {
                return;
            }
            final Uri imgUri = result.getData();
            if (imgUri != null) {
                String savedImagePath = saveImageToInternalStorage(getContext(), imgUri);
                if (savedImagePath != null) {
                ContentResolver resolver = getContext().getContentResolver();
                Settings.System.putIntForUser(resolver,
                        Settings.System.QS_HEADER_IMAGE, OPTION_CUSTOM_IMAGE_VALUE,
                        UserHandle.USER_CURRENT);
                Settings.System.putStringForUser(resolver,
                        Settings.System.QS_HEADER_IMAGE_URI, savedImagePath,
                        UserHandle.USER_CURRENT);
                }
            }
        }
    }


  18 changes: 18 additions & 0 deletions18  
res/values/strings.xml
@@ -756,4 +756,22 @@
    <string name="statusbar_top_padding">Top padding</string>
    <string name="statusbar_left_padding">Left padding</string>
    <string name="statusbar_right_padding">Right padding</string>

    <!-- QS Header -->
    <string name="qs_header_image_title">QS header image</string>
    <string name="qs_header_image_summary">Select QS background header image</string>
    <string name="qs_header_random_image_title">QS Header Random Image</string>
    <string name="qs_header_random_image_summary">Enable random QS header image</string>
    <string name="qs_header_image_blur_level_title">QS header image blur effect level</string>
    <string name="qs_header_image_blur_level_summary">Strength of the blur effect applied to the QS header image</string>
    <string name="qs_header_image_fade_level_level_title">QS header image fade level</string>
    <string name="qs_header_image_fade_level_level_summary">Fade level of the QS header image</string>
    <string name="qs_header_image_opacity_level_title">QS header image opacity level</string>
    <string name="qs_header_image_opacity_level_summary">Opacity level of the QS header image</string>
    <string name="qs_header_image_filter_color_title">QS header image filter color</string>
    <string name="qs_header_image_height_title">QS header image height</string>
    <string name="qs_header_custom_image_uri_enabled_title">Custom QS header image</string>
    <string name="qs_header_custom_image_uri_enabled_summary">Apply custom QS image header</string>
    <string name="file_header_select_title">Select local image</string>
    <string name="file_header_select_summary">Select image from storage</string>
</resources>
  7 changes: 7 additions & 0 deletions7  
res/xml/bg_quicksettings.xml
@@ -66,6 +66,13 @@
    <PreferenceCategory
        android:title="@string/interface_title">

        <!-- QS Header image -->
        <Preference
            android:key="qs_header_image"
            android:title="@string/qs_header_image_title"
            android:summary="@string/qs_header_image_summary"
            android:fragment="com.banana.settings.fragments.qs.QsHeaderImageSettings" />

        <!-- QS Tiles -->
        <Preference
            android:key="qs_tiles"
 58 changes: 58 additions & 0 deletions58  
res/xml/qs_header_image_settings.xml
@@ -0,0 +1,58 @@
<?xml version="1.0" encoding="utf-8"?>
<PreferenceScreen
    android:title="@string/qs_header_image_title"
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:settings="http://schemas.android.com/apk/res/com.android.settings">

     <com.banana.support.preferences.SystemSettingSeekBarPreference
        android:key="qs_header_image"
        android:title="@string/qs_header_image_title"
        android:defaultValue="0"
        settings:interval="1"
        android:max="24"
        android:min="0" />

    <com.banana.support.preferences.SystemSettingSwitchPreference
        android:key="qs_header_custom_image_uri_enabled"
        android:title="@string/qs_header_custom_image_uri_enabled_title"
        android:summary="@string/qs_header_custom_image_uri_enabled_summary" />

    <Preference
        android:key="qs_header_custom_image_uri"
        android:title="@string/file_header_select_title"
        android:summary="@string/file_header_select_summary"
        android:persistent="false"
        android:dependency="qs_header_custom_image_uri_enabled" />

    <com.banana.support.colorpicker.ColorPickerPreference
        android:key="qs_header_image_filter_color"
        android:title="@string/qs_header_image_filter_color_title"
        android:defaultValue="0xFFFFFFFF" />

    <com.banana.support.preferences.SystemSettingSeekBarPreference
        android:key="qs_header_image_fade_level"
        android:title="@string/qs_header_image_fade_level_level_title"
        android:summary="@string/qs_header_image_fade_level_level_summary"
        android:defaultValue="40"
        settings:interval="1"
        android:max="100"
        android:min="1" />

    <com.banana.support.preferences.SystemSettingSeekBarPreference
        android:key="qs_header_image_height"
        android:title="@string/qs_header_image_height_title"
        android:defaultValue="80"
        settings:interval="1"
        android:max="255"
        android:min="1" />

    <com.banana.support.preferences.SystemSettingSeekBarPreference
        android:key="qs_header_image_opacity_level"
        android:title="@string/qs_header_image_opacity_level_title"
        android:summary="@string/qs_header_image_opacity_level_summary"
        android:defaultValue="30"
        settings:interval="1"
        android:max="100"
        android:min="1" />

</PreferenceScreen>
 138 changes: 138 additions & 0 deletions138  
src/com/banana/settings/fragments/qs/QsHeaderImageSettings.java
@@ -0,0 +1,138 @@
package com.banana.settings.fragments.qs;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.MediaStore;
import android.provider.SearchIndexableResource;
import android.provider.Settings;
import android.view.View;

import androidx.preference.Preference;
import androidx.preference.ListPreference;
import androidx.preference.SwitchPreference;

import com.android.internal.logging.nano.MetricsProto;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import com.banana.support.colorpicker.ColorPickerPreference;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

@SearchIndexable
public class QsHeaderImageSettings extends SettingsPreferenceFragment
             implements Preference.OnPreferenceChangeListener{

    private static final String KEY_CUSTOM_QS_HEADER_IMAGE_URI = "qs_header_custom_image_uri";

    private Preference mQsHeaderCustomImagePicker;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.qs_header_image_settings);

        mQsHeaderCustomImagePicker = findPreference(KEY_CUSTOM_QS_HEADER_IMAGE_URI);
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference == mQsHeaderCustomImagePicker) {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.setType("image/*");
            startActivityForResult(intent, 10001);
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent result) {
        if (requestCode == 10001) {
            if (resultCode != Activity.RESULT_OK) {
                return;
            }

            final Uri imgUri = result.getData();
            if (imgUri != null) {
                String savedImagePath = saveImageToInternalStorage(getContext(), imgUri);
                if (savedImagePath != null) {
                    ContentResolver resolver = getContext().getContentResolver();
                    Settings.System.putIntForUser(resolver, Settings.System.QS_HEADER_IMAGE, 0, UserHandle.USER_CURRENT);
                    Settings.System.putStringForUser(resolver, Settings.System.QS_HEADER_CUSTOM_IMAGE_URI, savedImagePath, UserHandle.USER_CURRENT);
                }
            }
        }
    }

    private String saveImageToInternalStorage(Context context, Uri imgUri) {
        try {
            InputStream inputStream;
            if (imgUri.toString().startsWith("content://com.google.android.apps.photos.contentprovider")) {
                List<String> segments = imgUri.getPathSegments();
                if (segments.size() > 2) {
                    String mediaUriString = URLDecoder.decode(segments.get(2), StandardCharsets.UTF_8.name());
                    Uri mediaUri = Uri.parse(mediaUriString);
                    inputStream = context.getContentResolver().openInputStream(mediaUri);
                } else {
                    throw new FileNotFoundException("Failed to parse Google Photos content URI");
                }
            } else {
                inputStream = context.getContentResolver().openInputStream(imgUri);
            }
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String imageFileName = "QS_HEADER_" + timeStamp + ".png";
            File directory = new File("/sdcard/qsheaders");
            if (!directory.exists() && !directory.mkdirs()) {
                return null;
            }
            File[] files = directory.listFiles((dir, name) -> name.startsWith("QS_HEADER_") && name.endsWith(".png"));
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
            File file = new File(directory, imageFileName);
            try (FileOutputStream outputStream = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            }
            return file.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.BANANADROID;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.qs_header_image_settings);
}
