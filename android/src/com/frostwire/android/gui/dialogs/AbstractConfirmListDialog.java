/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011, 2014, FrostWire(TM). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.frostwire.android.gui.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface.OnCancelListener;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.*;
import com.frostwire.android.R;
import com.frostwire.android.gui.views.AbstractDialog;
import com.frostwire.android.gui.views.AbstractListAdapter;
import com.frostwire.logging.Logger;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * This dialog should evolve to allow us for reuse on a number of situations in which you
 * need a dialog that needs to display a list view control.
 * 
 * This would be the simplest version, in the future it will have a text editor to filter
 * the contents of the list, and it will also support different modes of selection.
 * 
 * For now it just uses an adapter to display the contents of the model data.
 * 
 * It's up to the user to implement the adapter (hmm, perhaps that's where the selection mode logic should be)
 * 
 * @author aldenml
 * @author gubatron
 * @author votaguz
 */
public abstract class AbstractConfirmListDialog<T> extends AbstractDialog implements AbstractListAdapter.OnItemCheckedListener {

    /**
     * TODOS: 1. Add an optional text filter control that will be connected to the adapter.
     */

    Logger LOGGER = Logger.getLogger(AbstractConfirmListDialog.class);
    private CompoundButton.OnCheckedChangeListener selectAllCheckboxOnCheckedChangeListener;

    public enum SelectionMode {
        NO_SELECTION,
        SINGLE_SELECTION, // still work to do, crashes on rotate, won't auto select 0
        MULTIPLE_SELECTION;

       public static SelectionMode fromInt(int n) {
           SelectionMode selectionMode = SelectionMode.NO_SELECTION;
           if (n == SelectionMode.MULTIPLE_SELECTION.ordinal()) {
               selectionMode = SelectionMode.MULTIPLE_SELECTION;
           } else if (n == SelectionMode.SINGLE_SELECTION.ordinal()) {
               selectionMode = SelectionMode.SINGLE_SELECTION;
           }
           return selectionMode;
       }
    }

    protected final static String TAG = "confirm_list_dialog";
    private String title;
    private String dialogText;
    private SelectionMode selectionMode;
    private Dialog dlg;
    private OnCancelListener onCancelListener;
    private OnClickListener onYesListener;
    private ConfirmListDialogDefaultAdapter<T> adapter;

    abstract protected OnClickListener createOnYesListener(AbstractConfirmListDialog dlg);

    /** rebuilds list of objects from json and does listView.setAdapter(YourAdapter(theObjectList)) */
    abstract public List<T> deserializeData(String listDataInJSON);

    public AbstractConfirmListDialog() {
        super(TAG, R.layout.dialog_confirm_list);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
    }

    public SelectionMode getSelectionMode() {
        return selectionMode;
    }

    protected void prepareArguments(int dialogIcon,
                                    String dialogTitle,
                                    String dialogText,
                                    String listDataInJSON,
                                    SelectionMode selectionMode) {
        Bundle bundle = new Bundle();
        bundle.putInt("dialogIcon", dialogIcon);
        bundle.putString("title", dialogTitle);
        bundle.putString("dialogText", dialogText);
        bundle.putString("listData", listDataInJSON);
        bundle.putInt("selectionMode", selectionMode.ordinal());
        this.selectionMode = selectionMode;
        setArguments(bundle);
    }

    @Override
    protected void initComponents(Dialog dlg, Bundle savedInstanceState) {
        this.dlg = dlg;
        Bundle bundle = getArguments();
        int dialogIcon = bundle.getInt("dialogIcon", -1);
        if (dialogIcon > 0) {
            dlg.setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, dialogIcon);
        }

        title = bundle.getString("title");
        dlg.setTitle(title);

        dialogText = bundle.getString("dialogText");
        TextView confirmTextView = findView(dlg, R.id.dialog_confirm_list_text);
        confirmTextView.setText(dialogText);

        initListViewAndAdapter(bundle);
        initSelectAllCheckbox();
        initButtonListeners();
    }

    private void initButtonListeners() {
        final Dialog dialog = dlg;
        Button noButton = findView(dialog, R.id.dialog_confirm_list_button_no);
        noButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (onCancelListener != null) {
                    onCancelListener.onCancel(dialog);
                }
                dialog.dismiss();
            }
        });
        if (onCancelListener != null){
            dialog.setOnCancelListener(onCancelListener);
        }
        onYesListener = createOnYesListener(this);
        if (onYesListener != null) {
            Button yesButton = findView(dialog, R.id.dialog_confirm_list_button_yes);
            yesButton.setOnClickListener(onYesListener);
        }
    }

    private void initSelectAllCheckbox() {
        selectAllCheckboxOnCheckedChangeListener = new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (buttonView.isChecked()) {
                    adapter.checkAll();
                } else {
                    adapter.clearChecked();
                }
                updateSelectedCount();
            }
        };

        CheckBox selectAllCheckbox = findView(dlg, R.id.dialog_confirm_list_select_all_checkbox);
        selectAllCheckbox.setVisibility(selectionMode == SelectionMode.MULTIPLE_SELECTION ? View.VISIBLE : View.GONE);
        if (selectionMode == SelectionMode.MULTIPLE_SELECTION) {
            selectAllCheckbox.setVisibility(View.VISIBLE);
            selectAllCheckbox.setOnCheckedChangeListener(selectAllCheckboxOnCheckedChangeListener);
        }
    }

    public abstract ConfirmListDialogDefaultAdapter<T> createAdapter(Context context,
                                                                     List<T> listData,
                                                                     SelectionMode selectionMode,
                                                                     Bundle bundle);

    private void initListViewAndAdapter(Bundle bundle) {
        ListView listView = findView(dlg, R.id.dialog_confirm_list_list);
        String listDataString = bundle.getString("listData");
        List<T> listData = deserializeData(listDataString);

        if (selectionMode == null) {
            selectionMode = SelectionMode.fromInt(bundle.getInt("selectionMode"));
        }

        if (adapter == null &&
            listData != null  &&
            !listData.isEmpty()) {
            adapter = createAdapter(getActivity(), listData, selectionMode, bundle);
        } else if (adapter.getTotalCount() == 0 && !listData.isEmpty()) {
            adapter.addList(listData);
        }

        if (selectionMode == SelectionMode.MULTIPLE_SELECTION) {
            precheckCheckboxes(bundle);
        }

        if (adapter != null) {
            adapter.setOnItemCheckedListener(null);
            listView.setAdapter(adapter);
            if (selectionMode == SelectionMode.MULTIPLE_SELECTION) {
                updateSelectedCount();
            }
            adapter.setOnItemCheckedListener(this);
        }
    }

    private void precheckCheckboxes(Bundle bundle) {
        if (bundle.containsKey("checkedOffsets")) {
            final boolean[] checkedOffsets = bundle.getBooleanArray("checkedOffsets");
            for (int i=0; i < checkedOffsets.length; i++) {
                adapter.setChecked(i, checkedOffsets[i]);
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (adapter != null) {
            final Set checked = adapter.getChecked();
            if (outState != null && checked != null && !checked.isEmpty()) {
                outState.putBooleanArray("checkedOffsets", getSelected());
            }
        }
        super.onSaveInstanceState(outState);
    }

    public void setOnYesListener(OnClickListener listener) {
        onYesListener = listener;
    }

    public OnClickListener getOnYesListener() {
        return onYesListener;
    }

    public Set<T> getChecked() {
        Set<T> result = Collections.EMPTY_SET;
        if (adapter != null) {
            result = adapter.getChecked();
        }
        return result;
    }

    public List<T> getList() {
        List<T> result = Collections.EMPTY_LIST;
        if (adapter != null) {
            result = adapter.getList();
        }
        return result;
    }

    public boolean[] getSelected() {
        boolean[] result = new boolean[0];
        if (adapter != null) {
            Set<T> checked = adapter.getChecked();
            if (checked == null || checked.isEmpty()) {
                return result;
            }
            result = new boolean[adapter.getCount()];
            List<T> all = adapter.getList();
            Iterator<T> iterator = checked.iterator();
            while (iterator.hasNext()) {
                T item = iterator.next();
                int i = all.indexOf(item);
                if (i >= 0) {
                    result[i]=true;
                }
            }
        }
        return result;
    }

    public int getLastSelected(){
        return adapter.getLastSelectedRadioButtonIndex();
    }

    public void updateSelectedCount() {
        if (adapter == null || selectionMode != SelectionMode.MULTIPLE_SELECTION) {
            return;
        }

        int selected = adapter.getCheckedCount();
        updatedSelectedCount(selected);
        autoToggleSelectAllCheckbox(selected);

        final Bundle arguments = getArguments();
        if (arguments != null) {
            arguments.putBooleanArray("checkedOffsets", getSelected());
        }
    }

    private void updatedSelectedCount(int selected) {
        final TextView selectedCountTextView = findView(dlg, R.id.dialog_confirm_list_selected_count_textview);
        if (selectedCountTextView != null) {
            selectedCountTextView.setText(selected + " " + getString(R.string.selected));
            selectedCountTextView.setVisibility(selected > 0 ? View.VISIBLE : View.GONE);
        }
    }

    private void autoToggleSelectAllCheckbox(int selected) {
        // Change the state of the "Select All" checkbox only when necessary.
        final CheckBox selectAllCheckbox = findView(dlg, R.id.dialog_confirm_list_select_all_checkbox);
        selectAllCheckbox.setOnCheckedChangeListener(null);
        boolean wasChecked = selectAllCheckbox.isChecked();
        int total = adapter.getTotalCount();
        if (wasChecked && selected < total) {
            selectAllCheckbox.setChecked(false);
        } else if (!wasChecked && selected == total) {
            selectAllCheckbox.setChecked(true);
        }
        selectAllCheckbox.setOnCheckedChangeListener(selectAllCheckboxOnCheckedChangeListener);
    }

    // AbstractListAdapter.OnItemCheckedListener
    @Override
    public void onItemChecked(CompoundButton v, boolean checked) {
        updateSelectedCount();
    }
}