package com.example.mafservice;

import android.app.assist.AssistStructure;
import android.content.Context;
import android.os.Build;
import android.os.CancellationSignal;
import android.service.autofill.AutofillService;
import android.service.autofill.Dataset;
import android.service.autofill.FillCallback;
import android.service.autofill.FillContext;
import android.service.autofill.FillRequest;
import android.service.autofill.FillResponse;
import android.service.autofill.SaveCallback;
import android.service.autofill.SaveRequest;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;
import android.view.View;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillValue;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.List;
import java.util.Map;

@RequiresApi(api = Build.VERSION_CODES.O)
public class mAFService extends AutofillService {
    private static final String TAG = mAFService.class.getSimpleName();

    @Override
    public void onFillRequest(FillRequest request, CancellationSignal cancellationSignal, FillCallback callback) {
        List<FillContext> context = request.getFillContexts();
        AssistStructure structure = context.get(context.size() - 1).getStructure();

        ArrayMap<String, AutofillId> fields = new ArrayMap<>();
        int nodes = structure.getWindowNodeCount();
        for (int i = 0; i < nodes; i++) {
            AssistStructure.ViewNode node = structure.getWindowNodeAt(i).getRootViewNode();
            String webDomain = node.getWebDomain();
            if (webDomain != null){
                Log.d(TAG,"domain: " + webDomain);
            }
            addAutofillableFields(fields, node);
        }

        FillResponse response = createResponse(this, fields);
        callback.onSuccess(response);
    }

    private void addAutofillableFields(@NonNull Map<String, AutofillId> fields, @NonNull AssistStructure.ViewNode node) {
        String hint = getHint(node);
        if (hint != null) {
            fields.put(hint, node.getAutofillId());
        }
        int childrenSize = node.getChildCount();
        for (int i = 0; i < childrenSize; i++) {
            addAutofillableFields(fields, node.getChildAt(i));
        }
    }

    private FillResponse createResponse(@NonNull Context context, @NonNull ArrayMap<String, AutofillId> fields) {
        String contextPackageName = context.getPackageName();
        FillResponse.Builder response = new FillResponse.Builder();
        Dataset unlockedDataset = newUnlockedDataset(fields, contextPackageName);
        if (unlockedDataset != null) {
            response.addDataset(unlockedDataset);
        }
        return response.build();
    }

    private Dataset newUnlockedDataset(@NonNull Map<String, AutofillId> fields, @NonNull String packageName) {
        Dataset.Builder dataset = new Dataset.Builder();
        for (Map.Entry<String, AutofillId> field : fields.entrySet()) {
            String hint = field.getKey();
            AutofillId id = field.getValue();
            String value = hint + " item";
            RemoteViews presentation = new RemoteViews(packageName, android.R.layout.simple_list_item_1);
            presentation.setTextViewText(android.R.id.text1, value);
            dataset.setValue(id, AutofillValue.forText(value), presentation);
        }
        return dataset.build();
    }

    @Nullable
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

    @Override
    public void onSaveRequest(@NonNull SaveRequest request, @NonNull SaveCallback callback) {
    }
}