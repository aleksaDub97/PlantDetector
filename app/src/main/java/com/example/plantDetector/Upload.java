package com.example.plantDetector;

import com.google.firebase.database.Exclude;

public class Upload {
    private int mClassId;
    private String mImageUrl;

    private String mKey;

    public Upload() {
        //empty constructor needed
    }

    public Upload(int id, String imageUrl) {
        mClassId = id;
        mImageUrl = imageUrl;
    }

    public int getClassId() {
        return mClassId;
    }

    public void setName(int id) {
        mClassId = id;
    }

    public String getImageUrl() {
        return mImageUrl;
    }

    public void setImageUrl(String imageUrl) {
        mImageUrl = imageUrl;
    }

    @Exclude
    public String getKey() {
        return mKey;
    }

    @Exclude
    public void setKey(String key) {
        mKey = key;
    }
}
