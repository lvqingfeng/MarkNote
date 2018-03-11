package me.shouheng.notepal.fragment.setting;

import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatCheckBox;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;

import com.afollestad.materialdialogs.MaterialDialog;

import org.polaric.colorful.PermissionUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

import me.shouheng.notepal.R;
import me.shouheng.notepal.activity.base.CommonActivity;
import me.shouheng.notepal.async.DataBackupIntentService;
import me.shouheng.notepal.listener.OnFragmentDestroyListener;
import me.shouheng.notepal.util.FileHelper;
import me.shouheng.notepal.util.LogUtils;
import me.shouheng.notepal.util.StringUtils;
import me.shouheng.notepal.util.ToastUtils;

/**
 * Created by wang shouheng on 2018/1/5.*/
public class SettingsBackup extends PreferenceFragment {

    private final static String KEY_BACKUP_TO_EXTERNAL_STORAGE = "backup_to_external_storage";
    private final static String KEY_IMPORT_FROM_EXTERNAL_STORAGE = "import_from_external_storage";
    private final static String KEY_DELETE_EXTERNAL_STORAGE_BACKUP = "delete_external_storage_backup";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configToolbar();

        addPreferencesFromResource(R.xml.preferences_data_backup);

        setPreferenceClickListeners();
    }

    private void configToolbar() {
        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (actionBar != null) actionBar.setTitle(R.string.setting_backup);
    }

    private void setPreferenceClickListeners() {
        findPreference(KEY_BACKUP_TO_EXTERNAL_STORAGE).setOnPreferenceClickListener(preference -> {
            PermissionUtils.checkStoragePermission((CommonActivity) getActivity(), this::showBackupNameEditor);
            return true;
        });
        findPreference(KEY_IMPORT_FROM_EXTERNAL_STORAGE).setOnPreferenceClickListener(preference -> {
            PermissionUtils.checkStoragePermission((CommonActivity) getActivity(), this::showExternalBackupImport);
            return true;
        });
        findPreference(KEY_DELETE_EXTERNAL_STORAGE_BACKUP).setOnPreferenceClickListener(preference -> {
            PermissionUtils.checkStoragePermission((CommonActivity) getActivity(), this::showExternalBackupDelete);
            return true;
        });
    }

    private void showBackupNameEditor() {
        View v = getActivity().getLayoutInflater().inflate(R.layout.dialog_backup_layout, null);

        String defName = StringUtils.getTimeFileName();
        EditText tvFileName = v.findViewById(R.id.export_file_name);
        tvFileName.setText(defName);

        AppCompatCheckBox cb = v.findViewById(R.id.backup_include_settings);

        new MaterialDialog.Builder(getActivity())
                .title(R.string.backup_data_export_message)
                .customView(v, false)
                .positiveText(R.string.confirm)
                .onPositive((dialog, which) -> {
                    String backupName;
                    if (TextUtils.isEmpty(backupName = tvFileName.getText().toString())) {
                        ToastUtils.makeToast(R.string.backup_data_export_name_empty);
                        backupName = defName;
                    }
                    Intent service = new Intent(getActivity(), DataBackupIntentService.class);
                    service.setAction(DataBackupIntentService.ACTION_DATA_EXPORT);
                    service.putExtra(DataBackupIntentService.INTENT_BACKUP_INCLUDE_SETTINGS, cb.isChecked());
                    service.putExtra(DataBackupIntentService.INTENT_BACKUP_NAME, backupName);
                    getActivity().startService(service);
                }).build().show();
    }

    private void showExternalBackupImport() {
        final String[] backups = getExternalBackups();
        if (backups.length == 0) {
            ToastUtils.makeToast(R.string.backup_no_backups_available);
            return;
        }

        new MaterialDialog.Builder(getActivity())
                .title(R.string.backup_data_import_message)
                .items(backups)
                .itemsCallbackSingleChoice(-1, (dialog, itemView, which, text) -> {
                    if (TextUtils.isEmpty(text)) {
                        ToastUtils.makeToast(R.string.backup_no_backup_data_selected);
                        return true;
                    }
                    showExternalBackupImportConfirm(text.toString());
                    return true;
                })
                .positiveText(R.string.confirm)
                .onPositive((dialog, which) -> {})
                .build().show();
    }

    private void showExternalBackupImportConfirm(String backup) {
        File backupDir = FileHelper.getBackupDir(backup);
        long size = FileHelper.getSize(backupDir) / 1024;
        String sizeString = size > 1024 ? size / 1024 + "Mb" : size + "Kb";

        String prefName = FileHelper.getSharedPreferencesFile(getActivity()).getName();
        boolean hasPreferences = (new File(backupDir, prefName)).exists();

        String message = getString(R.string.backup_data_import_message_warning) + "\n\n"
                + backup + " (" + sizeString + (hasPreferences ? " " + getString(R.string.backup_settings_included) : "") + ")";

        new MaterialDialog.Builder(getActivity())
                .title(R.string.backup_confirm_restoring_backup)
                .content(message)
                .positiveText(R.string.confirm)
                .onPositive((dialog, which) -> {
                    Intent service = new Intent(getActivity(), DataBackupIntentService.class);
                    service.setAction(DataBackupIntentService.ACTION_DATA_IMPORT);
                    service.putExtra(DataBackupIntentService.INTENT_BACKUP_NAME, backup);
                    getActivity().startService(service);
                }).build().show();
    }

    private String[] getExternalBackups() {
        String[] backups = FileHelper.getExternalStoragePublicDir().list();
        Arrays.sort(backups);
        return backups;
    }

    private void showExternalBackupDelete() {
        final String[] backups = getExternalBackups();
        if (backups.length == 0) {
            ToastUtils.makeToast(R.string.backup_no_backups_to_delete);
            return;
        }

        ArrayList<String> selected = new ArrayList<>();
        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.backup_data_delete_message)
                .setMultiChoiceItems(backups, new boolean[backups.length], (dialog, which, isChecked) -> {
                    if (isChecked) {
                        selected.add(backups[which]);
                    } else {
                        selected.remove(backups[which]);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, (dialog, which) -> {
                    LogUtils.d(selected);
                    if (selected.isEmpty()) {
                        ToastUtils.makeToast(R.string.backup_no_backup_data_selected);
                    } else {
                        showExternalBackupDeleteConfirm(selected);
                    }
                }).show();
    }

    private void showExternalBackupDeleteConfirm(ArrayList<String> selected) {
        new MaterialDialog.Builder(getActivity())
                .title(R.string.text_warning)
                .content(R.string.backup_confirm_removing_backup)
                .positiveText(R.string.confirm)
                .onPositive((dialog, which) -> {
                    Intent service = new Intent(getActivity(), DataBackupIntentService.class);
                    service.setAction(DataBackupIntentService.ACTION_DATA_DELETE);
                    service.putStringArrayListExtra(DataBackupIntentService.INTENT_BACKUP_NAME, selected);
                    getActivity().startService(service);
                }).build().show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (getActivity() instanceof OnFragmentDestroyListener) {
            ((OnFragmentDestroyListener) getActivity()).onFragmentDestroy();
        }
    }
}