package com.example.msecureafservice;

import static android.view.View.AUTOFILL_TYPE_TEXT;

import android.app.Service;
import android.app.assist.AssistStructure;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.IBinder;
import android.service.autofill.AutofillService;
import android.service.autofill.Dataset;
import android.service.autofill.FillCallback;
import android.service.autofill.FillContext;
import android.service.autofill.FillRequest;
import android.service.autofill.FillResponse;
import android.service.autofill.SaveCallback;
import android.service.autofill.SaveInfo;
import android.service.autofill.SaveRequest;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;
import android.view.View;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillValue;
import android.widget.RemoteViews;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.List;
import java.util.Map;

@RequiresApi(api = Build.VERSION_CODES.O)
public class mSecureAFService extends AutofillService {
    private static final String TAG = "DebugService";
    private SharedPreferences mPrefs;

    public mSecureAFService() {

    }

    @Override
    public void onConnected() {
        super.onConnected();
        mPrefs = getApplicationContext().getSharedPreferences("my-settings", Context.MODE_PRIVATE);
    }

    @Override
    public void onFillRequest(FillRequest request, CancellationSignal cancellationSignal, FillCallback callback) {
        // Get the structure from the request
        List<FillContext> context = request.getFillContexts();
        AssistStructure structure = context.get(context.size() - 1).getStructure();

        // Traverse the structure looking for nodes to fill out.
        ParsedStructure parsedStructure = parseStructure(structure);

        if (parsedStructure.usernameId == null && parsedStructure.passwordId == null) {
            toast("No autofill hints found");
            callback.onSuccess(null);
            return;
        }
        // Fetch user data that matches the fields.
        UserData userData = fetchUserData(parsedStructure);

        // Build the presentation of the datasets
        RemoteViews usernamePresentation = new RemoteViews(getPackageName(), android.R.layout.simple_list_item_1);
        usernamePresentation.setTextViewText(android.R.id.text1, "my_username");
        RemoteViews passwordPresentation = new RemoteViews(getPackageName(), android.R.layout.simple_list_item_1);
        passwordPresentation.setTextViewText(android.R.id.text1, "Password for my_username");

        // Add a dataset to the response
        Dataset.Builder builder = new Dataset.Builder();
        if(parsedStructure.usernameId!=null){
            builder.setValue(parsedStructure.usernameId,AutofillValue.forText(userData.username), usernamePresentation);
        }
        if(parsedStructure.passwordId!=null) {
            builder.setValue(parsedStructure.passwordId, AutofillValue.forText(userData.password), passwordPresentation);
        }
        Dataset dataset = builder.build();
        FillResponse fillResponse = new FillResponse.Builder()
                .addDataset(dataset)
                .setSaveInfo(new SaveInfo.Builder(
                        SaveInfo.SAVE_DATA_TYPE_PASSWORD,
                        new AutofillId[] {parsedStructure.passwordId})
                        .build())
                .build();

        // If there are no errors, call onSuccess() and pass the response
        callback.onSuccess(fillResponse);
    }

    private UserData fetchUserData(ParsedStructure parsedStructure) {
        UserData userData = new UserData();
        userData.username = "no";
       userData.password="no";
        if (parsedStructure.usernameId != null) {
            userData.username = "admin";
        }
        if (parsedStructure.passwordId != null) {
            userData.password = getPassword(parsedStructure.packageName);
        }
        return userData;
    }

    private ParsedStructure parseStructure(AssistStructure structure) {
        ParsedStructure parsedStructure = new ParsedStructure();
        parsedStructure.packageName = structure.getActivityComponent().getPackageName();
        int nodes = structure.getWindowNodeCount();
        for (int i = 0; i < nodes; i++) {
            AssistStructure.ViewNode node = structure.getWindowNodeAt(i).getRootViewNode();
            addAutofillableFields(parsedStructure, node);
        }
        return parsedStructure;
    }

    private void addAutofillableFields(@NonNull ParsedStructure parsedStructure,
                                       @NonNull AssistStructure.ViewNode node) {
        int autofillType = node.getAutofillType();
        if(autofillType== AUTOFILL_TYPE_TEXT) {
            String hint = getHint(node);
            if (hint != null) {
                AutofillId id = node.getAutofillId();
                if (hint.contains("password")) {
                    if (parsedStructure.passwordId == null) {
                        Log.v(TAG, "Setting hint '" + hint + "' on " + id);
                        parsedStructure.passwordId = id;
                    } else {
                        Log.v(TAG, "Ignoring hint '" + hint + "' on " + id
                                + " because it was already set");
                    }
                } else if (hint.contains("username")) {
                    if (parsedStructure.usernameId == null) {
                        Log.v(TAG, "Setting hint '" + hint + "' on " + id);
                        parsedStructure.usernameId = id;
                    } else {
                        Log.v(TAG, "Ignoring hint '" + hint + "' on " + id
                                + " because it was already set");
                    }
                }
            }
        }
        int childrenSize = node.getChildCount();
        for (int i = 0; i < childrenSize; i++) {
            addAutofillableFields(parsedStructure, node.getChildAt(i));
        }
    }

    protected String getHint(@NonNull AssistStructure.ViewNode node) {

        // First try the explicit autofill hints...

        String[] hints = node.getAutofillHints();
        if (hints != null) {
            // We're simple, we only care about the first hint
            return hints[0].toLowerCase();
        }

        // Then try some rudimentary heuristics based on other node properties

        String viewHint = node.getHint();
        String hint = inferHint(node, viewHint);
        if (hint != null) {
            Log.d(TAG, "Found hint using view hint(" + viewHint + "): " + hint);
            return hint;
        } else if (!TextUtils.isEmpty(viewHint)) {
            Log.v(TAG, "No hint using view hint: " + viewHint);
        }

        String resourceId = node.getIdEntry();
        hint = inferHint(node, resourceId);
        if (hint != null) {
            Log.d(TAG, "Found hint using resourceId(" + resourceId + "): " + hint);
            return hint;
        } else if (!TextUtils.isEmpty(resourceId)) {
            Log.v(TAG, "No hint using resourceId: " + resourceId);
        }

        CharSequence text = node.getText();
        CharSequence className = node.getClassName();
        if (text != null && className != null && className.toString().contains("EditText")) {
            hint = inferHint(node, text.toString());
            if (hint != null) {
                // NODE: text should not be logged, as it could contain PII
                Log.d(TAG, "Found hint using text(" + text + "): " + hint);
                return hint;
            }
        } else if (!TextUtils.isEmpty(text)) {
            // NODE: text should not be logged, as it could contain PII
            Log.v(TAG, "No hint using text: " + text + " and class " + className);
        }
        return null;
    }

    @Nullable
    protected String inferHint(AssistStructure.ViewNode node, @Nullable String actualHint) {
        if (actualHint == null) return null;

        String hint = actualHint.toLowerCase();
        if (hint.contains("label") || hint.contains("container")) {
            Log.v(TAG, "Ignoring 'label/container' hint: " + hint);
            return null;
        }

        if (hint.contains("password")) return View.AUTOFILL_HINT_PASSWORD;
        if (hint.contains("username")
                || (hint.contains("login") && hint.contains("id")))
            return View.AUTOFILL_HINT_USERNAME;
        if (hint.contains("email")) return View.AUTOFILL_HINT_EMAIL_ADDRESS;
        if (hint.contains("name")) return View.AUTOFILL_HINT_NAME;
        if (hint.contains("phone")) return View.AUTOFILL_HINT_PHONE;

        // When everything else fails, return the full string - this is helpful to help app
        // developers visualize when autofill is triggered when it shouldn't (for example, in a
        // chat conversation window), so they can mark the root view of such activities with
        // android:importantForAutofill=noExcludeDescendants
        if (node.isEnabled() && node.getAutofillType() != View.AUTOFILL_TYPE_NONE) {
            Log.v(TAG, "Falling back to " + actualHint);
            return actualHint;
        }
        return null;
    }

    class ParsedStructure {
        AutofillId usernameId;
        AutofillId passwordId;
        String packageName;
    }

    class UserData {
        String username;
        String password;
    }


    @Override
    public void onSaveRequest(@NonNull SaveRequest saveRequest, @NonNull SaveCallback saveCallback) {
        Log.d(TAG, "onSaveRequest()");
        toast("Save not supported");
        saveCallback.onSuccess();
    }

    /**
     * Displays a toast with the given message.
     */
    private void toast(@NonNull CharSequence message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
    }

    private static String getPassword(String packageName){
        switch (packageName){
            case "com.mseven.msecure":
                return "12345";
            case "com.mseven.barolo":
                return "1234567";
            default:
                return "1111";
        }
    }
}