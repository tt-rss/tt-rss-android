package org.fox.ttrss.glide;

import android.graphics.BitmapFactory;
import android.util.Log;
import android.util.Size;

import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.resource.transcode.ResourceTranscoder;
import com.bumptech.glide.load.engine.Resource;
import com.bumptech.glide.load.resource.SimpleResource;

class OptionsSizeResourceTranscoder implements ResourceTranscoder<BitmapFactory.Options, Size> {
    @Override
    public Resource<Size> transcode(Resource<BitmapFactory.Options> resource, Options options) {
        BitmapFactory.Options bmOptions = resource.get();
        Size size = new Size(bmOptions.outWidth, bmOptions.outHeight);
        return new SimpleResource<>(size);
    }
}

