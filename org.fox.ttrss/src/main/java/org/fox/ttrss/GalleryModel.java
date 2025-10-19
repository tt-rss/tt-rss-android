package org.fox.ttrss;

import android.app.Application;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import org.fox.ttrss.types.GalleryEntry;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GalleryModel extends AndroidViewModel {
    private static final String TAG = GalleryModel.class.getSimpleName();

    private MutableLiveData<List<GalleryEntry>> m_items = new MutableLiveData<>(new ArrayList<>());
    private MutableLiveData<Integer> m_checkProgress = new MutableLiveData<>(Integer.valueOf(0));
    private MutableLiveData<Integer> m_itemsToCheck = new MutableLiveData<>(Integer.valueOf(0));
    private MutableLiveData<Boolean> m_isChecking = new MutableLiveData<>(Boolean.valueOf(false));

    public GalleryModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<List<GalleryEntry>> getItems() {
        return m_items;
    }

    private ExecutorService m_executor = Executors.newSingleThreadExecutor();
    private Handler m_mainHandler = new Handler(Looper.getMainLooper());

    public LiveData<Integer> getItemsToCheck() {
        return m_itemsToCheck;
    }

    public LiveData<Integer> getCheckProgress() {
        return m_checkProgress;
    }

    private boolean isDataUri(String src) {
        try {
            Uri uri = Uri.parse(src);

            return "data".equalsIgnoreCase(uri.getScheme());

        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    public void update(List<GalleryEntry> items) {
        m_items.postValue(items);
    }

    public LiveData<Boolean> getIsChecking() {
        return m_isChecking;
    }

    public void collectItems(String articleText, String srcFirst) {
        m_executor.execute(() -> {

            Document doc = Jsoup.parse(articleText);

            List<GalleryEntry> checkList = new ArrayList<>();

            Log.d(TAG, "looking for srcFirst=" + srcFirst);

            Elements elems = doc.select("img,video");

            m_itemsToCheck.postValue(elems.size());

            int currentItem = 0;
            boolean firstFound = false;

            m_isChecking.postValue(true);

            for (Element elem : elems) {
                ++currentItem;

                if ("video".equalsIgnoreCase(elem.tagName())) {
                    Element source = elem.select("source").first();
                    String poster = elem.attr("abs:poster");

                    if (source != null) {
                        String src = source.attr("abs:src");

                        Log.d(TAG, "checking vid src=" + src + " poster=" + poster);

                        if (src != null && src.equals(srcFirst)) {
                            Log.d(TAG, "first item found, vid=" + src);

                            firstFound = true;

                            GalleryEntry item = new GalleryEntry(src, GalleryEntry.GalleryEntryType.TYPE_VIDEO, poster);

                            checkList.add(item);

                            m_items.postValue(checkList);
                        } else {
                            if (!isDataUri(src)) {
                                checkList.add(new GalleryEntry(src, GalleryEntry.GalleryEntryType.TYPE_VIDEO, poster));
                                m_items.postValue(checkList);
                            }
                        }
                    }
                } else {
                    String src = elem.attr("abs:src");

                    Log.d(TAG, "checking img src=" + src);

                    if (src != null && src.equals(srcFirst)) {
                        Log.d(TAG, "first item found, img=" + src);

                        firstFound = true;

                        GalleryEntry item = new GalleryEntry(src, GalleryEntry.GalleryEntryType.TYPE_IMAGE, null);

                        checkList.add(item);

                        m_items.postValue(checkList);
                    } else {
                        if (!isDataUri(src)) {
                            Log.d(TAG, "checking image with glide: " + src);

                            try {
                                Bitmap bmp = Glide.with(getApplication().getApplicationContext())
                                        .asBitmap()
                                        .load(src)
                                        .skipMemoryCache(false)
                                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                                        .into(HeadlinesFragment.FLAVOR_IMG_MIN_SIZE, HeadlinesFragment.FLAVOR_IMG_MIN_SIZE)
                                        .get();

                                if (bmp != null && bmp.getWidth() >= HeadlinesFragment.FLAVOR_IMG_MIN_SIZE && bmp.getHeight() >= HeadlinesFragment.FLAVOR_IMG_MIN_SIZE) {
                                    Log.d(TAG, "image matches gallery criteria, adding...");

                                    checkList.add(new GalleryEntry(src, GalleryEntry.GalleryEntryType.TYPE_IMAGE, null));
                                    m_items.postValue(checkList);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }

                m_checkProgress.postValue(currentItem);
            }

            // if we didn't find it in the document, let's add insert to the list anyway so shared transition
            // would hopefully work
            if (!firstFound) {
                checkList.add(0, new GalleryEntry(srcFirst, GalleryEntry.GalleryEntryType.TYPE_IMAGE, null));
                m_items.postValue(checkList);
            }

            m_isChecking.postValue(false);
        });
    }
}
