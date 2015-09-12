package com.sapra.codered;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Parcelable;
import android.provider.ContactsContract;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ListAdapter;
import android.content.ClipData.Item;
import android.widget.ListView;
import android.widget.TextView;

import com.github.rahatarmanahmed.cpv.CircularProgressView;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ContactPicker extends AppCompatActivity {
    private List<Contact> allContacts = new ArrayList<Contact>();
    private List<Contact> prevSavedContacts = new ArrayList<Contact>();
    private CircularProgressView progressView;
    private TextView progressText;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_picker);
        prevSavedContacts = this.getIntent().getParcelableArrayListExtra("CONTACTS");
        progressView = (CircularProgressView) findViewById(R.id.progress_view);
        progressText = (TextView) findViewById(R.id.progress_text);
        new LoadContactTask().execute();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_contact_picker, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_complete) {
            ArrayList<Contact> checked = new ArrayList<Contact>();
            for (Contact c : allContacts) {
                if (c.isChecked()) {
                    checked.add(c);
                }
            }
            Intent intent = this.getIntent();
            intent.putParcelableArrayListExtra("CONTACTS", checked);
            this.setResult(RESULT_OK, intent);
        }
        finish();
        return super.onOptionsItemSelected(item);
    }
    public class ListAdapter extends ArrayAdapter<Contact> {

        private int resourceID;
        public ListAdapter(Context context, int textViewResourceId) {
            super(context, textViewResourceId);
            this.resourceID = textViewResourceId;
        }

        private class ViewHolder {
            TextView name;
            TextView number;
            CheckBox selected;
            View parentView;
        }

        @Override
        public int getCount() {
            return allContacts.size();
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            ViewHolder holder = null;

            if (convertView == null) {
                LayoutInflater vi = (LayoutInflater)getSystemService(
                        Context.LAYOUT_INFLATER_SERVICE);
                convertView = vi.inflate(resourceID, null);

                holder = new ViewHolder();
                holder.number = (TextView) convertView.findViewById(R.id.number);
                holder.name = (TextView) convertView.findViewById(R.id.name);
                holder.selected = (CheckBox) convertView.findViewById(R.id.checkBox);
                holder.parentView = (View) convertView.findViewById(R.id.parent_contact);
                convertView.setTag(holder);
            }
            else {
                holder = (ViewHolder) convertView.getTag();
            }

            final CheckBox contact_select = holder.selected;

            holder.selected.setOnClickListener(new View.OnClickListener(){
                public void onClick(View v){
                    Contact c = allContacts.get(position);
                    c.setChecked(contact_select.isChecked());
                    Log.d("SAVED", Boolean.toString(allContacts.get(position).isChecked()));
                }
            });
            holder.parentView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Contact c = allContacts.get(position);
                    contact_select.setChecked(!contact_select.isChecked());
                    c.setChecked(contact_select.isChecked());
                    Log.d("SAVED", Boolean.toString(allContacts.get(position).isChecked()));
                }
            });

            Contact c = allContacts.get(position);
            holder.name.setText(c.getName());
            contact_select.setChecked(c.isChecked());
            holder.number.setText(c.getNumber());

            return convertView;
        }
    }

    private class LoadContactTask extends AsyncTask<Void, Void, List<Contact>> {

        protected List<Contact> doInBackground(Void... params) {
            Cursor cursor = getContentResolver().query(ContactsContract.Contacts.CONTENT_URI,null, null, null, null);
            while (cursor.moveToNext()) {
                String contactId = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts._ID));
                String hasPhone = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER));
                String name = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
                if("1".equals(hasPhone) || Boolean.parseBoolean(hasPhone)) {
                    Cursor phones = getApplicationContext().getContentResolver().query( ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, ContactsContract.CommonDataKinds.Phone.CONTACT_ID +" = "+ contactId, null, null);
                    while (phones.moveToNext()) {
                        String phoneNumber = phones.getString(phones.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
                        Uri person = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, Long
                                .parseLong(contactId));
                        Uri image_url = Uri.withAppendedPath(person, ContactsContract.Contacts.Photo.CONTENT_DIRECTORY);
                        Contact contact_from_phone = new Contact(name, phoneNumber, image_url);
                        Object previous = "";
                        if (allContacts.size() > 0){
                            previous = (Contact) allContacts.get(allContacts.size() - 1);
                        }
                        boolean dup = previous.equals(contact_from_phone);

                        if (!dup){
                            for (Contact saved_contact : prevSavedContacts){
                                if (saved_contact.equals(contact_from_phone)){
                                    contact_from_phone.setChecked(saved_contact.isChecked());
                                }
                            }
                            allContacts.add(contact_from_phone);
                        }
                    }
                    phones.close();
                }
            }
            return allContacts;
        }

        protected void onPostExecute(List<Contact> contacts){
            Collections.sort(contacts, new ContactComparator());

            ListView contact_lv = (ListView) findViewById(R.id.contact_view);

            ListAdapter customAdapter = new ListAdapter(getApplicationContext(), R.layout.contact_list_item);
            contact_lv.setAdapter(customAdapter);

            progressView.setVisibility(View.GONE);
            progressText.setVisibility(View.GONE);
        }
    }
}
