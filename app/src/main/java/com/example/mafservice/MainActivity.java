package com.example.mafservice;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.ViewGroup;
import android.view.autofill.AutofillManager;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private AutofillManager mAutofillManager;
    private static final int REQUEST_CODE_SET_DEFAULT = 1;
    Switch switchView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mAutofillManager = getSystemService(AutofillManager.class);

        setupSettingsSwitch(R.id.settingsSetServiceContainer,
                R.id.settingsSetServiceLabel,
                R.id.settingsSetServiceSwitch,
                mAutofillManager.hasEnabledAutofillServices(),
                (compoundButton, serviceSet) -> setService(serviceSet));
        }
    }

    private void setupSettingsSwitch(int containerId, int labelId, int switchId, boolean checked,
                                     CompoundButton.OnCheckedChangeListener checkedChangeListener) {
        ViewGroup container = findViewById(containerId);
        String switchLabel = ((TextView) container.findViewById(labelId)).getText().toString();
        switchView = container.findViewById(switchId);
        switchView.setContentDescription(switchLabel);
        switchView.setChecked(checked);
        container.setOnClickListener((view) -> switchView.performClick());
        switchView.setOnCheckedChangeListener(checkedChangeListener);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void setService(boolean enableService) {
        if (enableService) {
            startEnableService();
        } else {
            disableService();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void disableService() {
        if (mAutofillManager != null && mAutofillManager.hasEnabledAutofillServices()) {
            mAutofillManager.disableAutofillServices();
            showMessage(getString(R.string.settings_autofill_disabled_message));
        } else {
            showMessage("Sample service already disabled.");
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void startEnableService() {
        if (mAutofillManager != null && !mAutofillManager.hasEnabledAutofillServices()) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE);
            intent.setData(Uri.parse("package:com.example.android.autofill.service"));
            logd(TAG, "enableService(): intent=%s", intent);
            startActivityForResult(intent, REQUEST_CODE_SET_DEFAULT);
        } else {
            logd("Sample service already enabled.");
        }
    }

    public static void logd(String message, Object... params) {
        Log.d(TAG, String.format(message, params));
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        logd(TAG, "onActivityResult(): req=%s", requestCode);
        if (requestCode == REQUEST_CODE_SET_DEFAULT) {
            onDefaultServiceSet(resultCode);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void onDefaultServiceSet(int resultCode) {
        logd(TAG, "resultCode=%d", resultCode);
//        switch (resultCode) {
//            case RESULT_OK:
        boolean isEnabled = mAutofillManager.hasEnabledAutofillServices();
        if (isEnabled) {
            showMessage("Autofill service set.");
        } else {
            switchView.setChecked(false);
            showMessage("Another Autofill service was selected.");
//                    //switchView.setChecked(false);

        }
//                break;
//            case RESULT_CANCELED:
//                break;
//        }
    }

    void showMessage(String message) {
        logd(message);
        Snackbar.make(findViewById(R.id.settings_layout), message, Snackbar.LENGTH_SHORT)
                .show();
    }

}