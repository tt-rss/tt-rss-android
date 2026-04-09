package org.fox.ttrss.types;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.Serializable;
import java.util.Objects;

public class GalleryEntry implements Serializable, Parcelable {
    @Override
    public int describeContents() {
        return 0;
    }

    @SuppressWarnings("rawtypes")
    public static final Parcelable.Creator CREATOR =
            new Parcelable.Creator() {
                @Override
                public GalleryEntry createFromParcel(Parcel in) {
                    return new GalleryEntry(in);
                }

                @Override
                public GalleryEntry[] newArray(int size) {
                    return new GalleryEntry[size];
                }
            };

    public enum GalleryEntryType {TYPE_IMAGE, TYPE_VIDEO}

    public String url;
    public GalleryEntryType type;
    public String coverUrl;

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(url);
        out.writeString(type.name());
        out.writeString(coverUrl);
    }

    public void readFromParcel(Parcel in) {
        url = in.readString();
        type = GalleryEntryType.valueOf(in.readString());
        coverUrl = in.readString();
    }

    public GalleryEntry(Parcel in) {
        readFromParcel(in);
    }

    public GalleryEntry() {
        //
    }

    public GalleryEntry(String url, GalleryEntryType type, String coverUrl) {
        this.url = url;
        this.type = type;
        this.coverUrl = coverUrl;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof GalleryEntry)) return false;
        GalleryEntry other = (GalleryEntry) obj;
        return Objects.equals(url, other.url);
    }

    @Override
    public int hashCode() {
        return url != null ? url.hashCode() : 0;
    }
}
