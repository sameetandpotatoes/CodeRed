package com.sapra.codered;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Comparator;

/**
 * Created by ssapra on 8/29/15.
 */
public class Contact implements Parcelable {
    private String name;
    private String number;
    private Uri image_url;
    private boolean checked;

    public Contact(JSONObject json_contact){
        try{
            this.name = (String) json_contact.get("name");
            this.number = (String) json_contact.get("number");
            this.checked = (boolean) json_contact.getBoolean("checked");
            this.image_url = Uri.parse((String) json_contact.get("image_url"));
        } catch(JSONException je){
            this.name = "";
            this.number = "";
            this.checked = false;
            this.image_url = null;
        }
    }

    public Contact(String name, String number, Uri image_url){
        this.name = name;
        this.number = number;
        this.image_url = image_url;
        this.checked = false;
    }

    public String getName() {
        return name;
    }

    public String getNumber() {
        return number;
    }

    public boolean isChecked() {
        return checked;
    }

    public void setChecked(boolean checked) {
        this.checked = checked;
    }

    public Uri getImageUrl() {
        if (image_url == null){
            return Uri.parse("");
        } else{
            return image_url;
        }
    }

    public JSONObject toJSON(){

        JSONObject jsonObject= new JSONObject();
        try {
            jsonObject.put("name", getName());
            jsonObject.put("number", getNumber());
            jsonObject.put("checked", isChecked());
            jsonObject.put("image_url", getImageUrl());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonObject;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Contact)) {
            return false;
        } else {
            return this.number.equals(((Contact) other).getNumber()) || this.name.equals(((Contact) other).getName());
        }
    }
    public int describeContents() {return 0;}

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeString(number);
        dest.writeString(getImageUrl().toString());
        dest.writeInt(checked ? 1 : 0);
    }
    public static final Parcelable.Creator<Contact> CREATOR = new Parcelable.Creator<Contact>() {
        public Contact createFromParcel(Parcel in) {
            return new Contact(in);
        }

        public Contact[] newArray(int size) {
            return new Contact[size];
        }
    };
    @SuppressWarnings("unchecked")
    private Contact(Parcel in){
        this.name = in.readString();
        this.number = in.readString();
        this.image_url = Uri.parse(in.readString());
        this.checked = in.readInt() == 1;
    }

}

class ContactComparator implements Comparator<Contact> {
    @Override
    public int compare(Contact o1, Contact o2) {
        return o1.getName().compareTo(o2.getName());
    }
}
