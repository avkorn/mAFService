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
import android.util.ArrayMap;
import android.util.Log;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillValue;
import android.widget.RemoteViews;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.util.List;
import java.util.Map;

@RequiresApi(api = Build.VERSION_CODES.O)
public class mAFService extends AutofillService {
    String webDomain = null;

    @Override
    public void onFillRequest(FillRequest request, CancellationSignal cancellationSignal, FillCallback callback) {
        List<FillContext> context = request.getFillContexts();
        AssistStructure structure = context.get(context.size() - 1).getStructure();

        ArrayMap<String, AutofillId> fields = new ArrayMap<>();
        int nodes = structure.getWindowNodeCount();
        for (int i = 0; i < nodes; i++) {
            AssistStructure.ViewNode node = structure.getWindowNodeAt(i).getRootViewNode();
            addAutofillableFields(fields, node);
        }

        FillResponse response = createResponse(this, fields);
        callback.onSuccess(response);
    }

    private void addAutofillableFields(@NonNull Map<String, AutofillId> fields, @NonNull AssistStructure.ViewNode node) {
        String wDomain = node.getWebDomain();
        if (webDomain == null)
            webDomain = wDomain;
        String[] hints = node.getAutofillHints();
        if (hints != null) {
            String hint = hints[0].toLowerCase();
            if (hint != null)
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
        if (webDomain != null) {
            Dataset unlockedDataset = newUnlockedDataset(fields, contextPackageName);
            if (unlockedDataset != null) {
                response.addDataset(unlockedDataset);
            }
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

    @Override
    public void onSaveRequest(@NonNull SaveRequest request, @NonNull SaveCallback callback) {
    }
}