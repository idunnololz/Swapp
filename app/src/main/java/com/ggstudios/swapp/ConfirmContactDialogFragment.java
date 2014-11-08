package com.ggstudios.swapp;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.OperationApplicationException;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;

public class ConfirmContactDialogFragment extends DialogFragment {

    public static final String EXTRA_CONTACT_NAME = "c_name";
    public static final String EXTRA_PHONE_NUMBER = "p_num";

    String name, number;

    public static ConfirmContactDialogFragment newInstance(String contactName, String number) {
        Bundle args = new Bundle();
        ConfirmContactDialogFragment fragment = new ConfirmContactDialogFragment();
        args.putString(EXTRA_CONTACT_NAME, contactName);
        args.putString(EXTRA_PHONE_NUMBER, number);
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);

        setStyle(DialogFragment.STYLE_NO_TITLE, android.R.style.Theme_Holo_Dialog);

        return dialog;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.dialog_confirm_contact, container, false);

        Bundle args = getArguments();

        TextView txtContactName = (TextView) rootView.findViewById(R.id.txt_contact_name);
        TextView txtPhoneNumber = (TextView) rootView.findViewById(R.id.txt_phone_number);
        Button btnCancel = (Button) rootView.findViewById(R.id.btn_cancel);
        Button btnOk = (Button) rootView.findViewById(R.id.btn_ok);

        name = args.getString(EXTRA_CONTACT_NAME);
        number = args.getString(EXTRA_PHONE_NUMBER);

        txtContactName.setText(name);
        txtPhoneNumber.setText(number);


        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getListener().onCancelClicked();
                dismiss();
            }
        });

        btnOk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                getListener().onOkClicked(name, number);
                dismiss();
            }
        });

        return rootView;
    }

    private ConfirmContactDialogListener getListener() {
        return ((ConfirmContactDialogListener) getActivity());
    }

    public static interface ConfirmContactDialogListener {
        public void onOkClicked(String name, String number);
        public void onCancelClicked();
    }
}
