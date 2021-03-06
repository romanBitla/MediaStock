package com.example.mediastock.activities;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.example.mediastock.R;
import com.example.mediastock.data.ImageBean;
import com.example.mediastock.model.ImageAdapter;
import com.example.mediastock.util.DownloadResultReceiver;
import com.example.mediastock.util.DownloadService;
import com.example.mediastock.util.Utilities;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.Charset;


/**
 * Fragment which displays a gallery of images.
 *
 * @author Dinu
 */
public class ImagesFragment extends AbstractFragment implements DownloadResultReceiver.Receiver {
    public static final String IMG_RECEIVER = "ireceiver";
    // the keywords to search for
    private static String keyWord1;
    private static String keyWord2;
    private static Context context;
    private static boolean working = false;
    private DownloadResultReceiver resultReceiver;
    private ImageView buttonRefresh;
    private ProgressBar progressBar;
    private ProgressBar progressBar_bottom;
    private RecyclerView recyclerView;
    private ImageAdapter adapter;
    private View view;


    /**
     * Method to create an instance of this fragment for the viewPager.
     */
    public static ImagesFragment createInstance() {
        return new ImagesFragment();
    }


    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = this.getActivity().getApplicationContext();

        view = inflater.inflate(R.layout.images_fragment, container, false);

        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        compute();
    }

    /**
     * Method to initialize the UI components and to get the recent images.
     */
    private void compute() {
        final ImagesFragment fragment = this;

        buttonRefresh = (ImageView) view.findViewById(R.id.buttonRefreshInternet);

        if (Utilities.deviceOnline(context))
            buttonRefresh.setVisibility(View.GONE);

        buttonRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (!Utilities.deviceOnline(context))
                    Toast.makeText(context.getApplicationContext(), "There is no internet connection", Toast.LENGTH_SHORT).show();
                else {
                    buttonRefresh.setVisibility(View.GONE);
                    getRecentImages();
                }
            }
        });

        progressBar = (ProgressBar) view.findViewById(R.id.p_img_bar);
        progressBar_bottom = (ProgressBar) view.findViewById(R.id.p_img_bar_bottom);
        recyclerView = (RecyclerView) view.findViewById(R.id.gridView_displayImage);
        recyclerView.setHasFixedSize(true);
        GridLayoutManager grid = new GridLayoutManager(context, 2);
        recyclerView.setLayoutManager(grid);
        adapter = new ImageAdapter(context, 1);
        recyclerView.setAdapter(adapter);

        // on image click
        adapter.setOnItemClickListener(new ImageAdapter.OnItemClickListener() {

            @Override
            public void onItemClick(View view, int position) {

                if (!Utilities.deviceOnline(context)) {
                    Toast.makeText(context.getApplicationContext(), "Not online", Toast.LENGTH_SHORT).show();
                    return;
                }

                goToDisplayImageActivity((ImageBean) adapter.getBeanAt(position));
            }
        });

        // endless list which loads more data when reaching the bottom of the view
        adapter.setOnBottomListener(new ImageAdapter.OnBottomListener() {

            @Override
            public void onBottomLoadMoreData(int loadingType, int loadingPageNumber) {
                progressBar_bottom.setVisibility(View.VISIBLE);

                // recent images
                if (loadingType == 1)
                    new AsyncWork(fragment, 1, loadingPageNumber).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                else
                    new AsyncWork(fragment, 2, loadingPageNumber).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, keyWord1, keyWord2);  // search images by key
            }
        });

        getRecentImages();
    }


    /**
     * Method to get the recent images asynchronously
     */
    public void getRecentImages() {
        if (!Utilities.deviceOnline(context) || working)
            return;

        showProgressBar();
        deleteItems();
        new AsyncWork(this, 1, 50).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * It searches asynchronously the images by one or two keys
     */
    public void searchImagesByKey(String key1, String key2) {
        if (working)
            return;

        keyWord1 = key1;
        keyWord2 = key2;

        showProgressBar();
        deleteItems();
        new AsyncWork(this, 2, 50).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, key1, key2);
    }


    /**
     * Start the filter search asynchronously. The bundle contains alla the users input.
     * We pass all the info to DownloadService service to start to download the images.
     */
    public void startFilterSearch(Bundle bundle) {
        if (!Utilities.deviceOnline(context) || working)
            return;

        adapter.setLoadingType(3);
        showProgressBar();
        deleteItems();

        resultReceiver = new DownloadResultReceiver(new Handler());
        resultReceiver.setReceiver(this);
        Intent intent = new Intent(Intent.ACTION_SYNC, null, context, DownloadService.class);

        // query info
        intent.putExtra(IMG_RECEIVER, resultReceiver);
        intent.putExtra(FilterImageFragment.CATEGORY, bundle.getString(FilterImageFragment.CATEGORY));
        intent.putExtra(FilterImageFragment.ORIENTATION, bundle.getString(FilterImageFragment.ORIENTATION));
        intent.putExtra(FilterImageFragment.SORT_BY, bundle.getString(FilterImageFragment.SORT_BY));
        intent.putExtra(FilterImageFragment.PER_PAGE, bundle.getString(FilterImageFragment.PER_PAGE));

        getActivity().startService(intent);
    }


    /**
     * Method to delete the list of images and to notify the adapter
     */
    private void deleteItems() {
        adapter.deleteItems();
    }


    /**
     * It shows the progress bar
     */
    private void showProgressBar() {
        recyclerView.scrollToPosition(0);
        recyclerView.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);
    }

    /**
     * Method to dismiss the progress bar
     */
    private void dismissProgressBar() {
        progressBar.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);
    }


    /**
     * Method which redirects the user to DisplayImageActivity to see the details of the image.
     *
     * @param bean the image bean
     */
    private void goToDisplayImageActivity(ImageBean bean) {
        Intent intent = new Intent(context, DisplayImageActivity.class);
        final Bundle bundle = new Bundle();
        bundle.putParcelable("bean", bean);
        intent.putExtra("bean", bundle);

        startActivity(intent);
        getActivity().overridePendingTransition(R.anim.trans_corner_from, R.anim.trans_corner_to);
    }

    /**
     * It handles the result of the DownloadService service.
     * Method used when we do a filter search. It updates the UI.
     */
    @Override
    public void onReceiveResult(int resultCode, Bundle resultData) {
        switch (resultCode) {
            case 1:
                if (progressBar.isShown())
                    dismissProgressBar();

                ImageBean bean = resultData.getParcelable(DownloadService.IMG_BEAN);

                // update UI with the image
                adapter.addItem(bean);
                break;

            case 2:
                showProgressBar();
                break;

            default:
                dismissProgressBar();
                Toast.makeText(context, "Search failed", Toast.LENGTH_LONG).show();
                break;
        }
    }

    /**
     * Static inner class to do asynchronous operations.
     * This class is used to search for images and to get the recent images from the server.
     *
     * @author Dinu
     */
    private static class AsyncWork extends AsyncTask<String, ImageBean, String> {
        private static WeakReference<ImagesFragment> activity;
        private final int type;
        private final int loadingPageNumber;
        private boolean searchSuccess = true;

        public AsyncWork(ImagesFragment activity, int type, int loadingPageNumber) {
            this.type = type;
            this.loadingPageNumber = loadingPageNumber;
            AsyncWork.activity = new WeakReference<>(activity);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            activity.get().working = true;
            activity.get().adapter.setLoadingType(type);
            activity.get().adapter.setPageNumber(loadingPageNumber);
        }

        @Override
        protected String doInBackground(String... params) {

            switch (type) {

                case 1:
                    getRecentImages(0, loadingPageNumber);
                    break;

                case 2:
                    searchImagesByKey(params[0], params[1], loadingPageNumber);
                    return params[1] != null ? params[0] + " " + params[1] : params[0];

                default:
                    break;
            }

            return null;
        }

        /**
         * Method to search images by one or two keys
         *
         * @param key1 the first key
         * @param key2 the second key
         * @param loadingPageNumber the number of items to get
         */
        private void searchImagesByKey(String key1, String key2, int loadingPageNumber) {
            String urlStr = "https://@api.shutterstock.com/v2/images/search?per_page=";
            urlStr += loadingPageNumber + "&query=";

            if (key2 != null)
                urlStr += key1 + "/" + key2;
            else
                urlStr += key1;

            InputStream is = null;
            HttpURLConnection con = null;
            try {
                URL url = new URL(urlStr);
                con = (HttpURLConnection) url.openConnection();
                con.setRequestProperty("Authorization", "Basic " + Utilities.getLicenseKey());
                con.setConnectTimeout(25000);
                con.setReadTimeout(25000);

                if (con.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    is = con.getInputStream();

                    BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
                    String jsonText = Utilities.readAll(rd);
                    rd.close();

                    // parse json text
                    JsonElement json = new JsonParser().parse(jsonText);
                    JsonObject o = json.getAsJsonObject();
                    JsonArray array = o.get("data").getAsJsonArray();

                    if (array.size() == 0) {
                        searchSuccess = false;
                        con.disconnect();
                        is.close();

                        return;
                    }

                    // get objects
                    for (int i = loadingPageNumber - 50; i < array.size(); i++) {
                        JsonObject jsonObj = array.get(i).getAsJsonObject();
                        JsonObject assets = jsonObj.get("assets") == null ? null : jsonObj.get("assets").getAsJsonObject();
                        final ImageBean bean = new ImageBean();

                        if (assets != null) {
                            bean.setId(jsonObj.get("id") == null ? null : jsonObj.get("id").getAsInt());
                            bean.setDescription(jsonObj.get("description") == null ? null : jsonObj.get("description").getAsString());
                            bean.setIdContributor(jsonObj.get("contributor") == null ? null : jsonObj.get("contributor").getAsJsonObject().get("id").getAsInt());
                            bean.setUrl(assets.get("preview") == null ? null : assets.get("preview").getAsJsonObject().get("url").getAsString());
                        }

                        bean.setPos(i);

                        // update UI
                        publishProgress(bean);
                    }
                }
            } catch (SocketTimeoutException e) {
                if (con != null)
                    con.disconnect();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (is != null)
                        is.close();

                    if (con != null)
                        con.disconnect();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        /**
         * Method to get the recent images
         *
         * @param day it represents the day
         * @param loadingPageNumber the number of items to get
         */
        private void getRecentImages(int day, int loadingPageNumber) {
            String urlStr = "https://@api.shutterstock.com/v2/images/search?per_page=";
            urlStr += loadingPageNumber + "&added_date=";
            urlStr += Utilities.getDate(day);

            Log.i("url", urlStr);

            InputStream is = null;
            HttpURLConnection con = null;
            try {
                URL url = new URL(urlStr);
                con = (HttpURLConnection) url.openConnection();
                con.setRequestProperty("Authorization", "Basic " + Utilities.getLicenseKey());
                con.setConnectTimeout(25000);
                con.setReadTimeout(25000);

                if (con.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    is = con.getInputStream();

                    BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
                    String jsonText = Utilities.readAll(rd);
                    rd.close();

                    JsonElement json = new JsonParser().parse(jsonText);
                    JsonObject o = json.getAsJsonObject();
                    JsonArray array = o.get("data").getAsJsonArray();

                    if (array.size() == 0) {
                        int yesterday = day;
                        yesterday += 1;

                        con.disconnect();
                        is.close();

                        getRecentImages(yesterday, loadingPageNumber);
                        return;
                    }

                    for (int i = loadingPageNumber - 50; i < array.size(); i++) {
                        JsonObject jsonObj = array.get(i).getAsJsonObject();
                        JsonObject assets = jsonObj.get("assets") == null ? null : jsonObj.get("assets").getAsJsonObject();
                        final ImageBean bean = new ImageBean();

                        if (assets != null) {
                            bean.setId(jsonObj.get("id") == null ? null : jsonObj.get("id").getAsInt());
                            bean.setDescription(jsonObj.get("description") == null ? null : jsonObj.get("description").getAsString());
                            bean.setIdContributor(jsonObj.get("contributor") == null ? null : jsonObj.get("contributor").getAsJsonObject().get("id").getAsInt());
                            bean.setUrl(assets.get("preview") == null ? null : assets.get("preview").getAsJsonObject().get("url").getAsString());
                        }

                        bean.setPos(i);

                        // update UI
                        publishProgress(bean);
                    }
                }
            } catch (SocketTimeoutException e) {
                if (con != null)
                    con.disconnect();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    // clean resources
                    if (is != null)
                        is.close();

                    if (con != null)
                        con.disconnect();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        /**
         * We update the UI
         */
        @Override
        protected void onProgressUpdate(ImageBean... bean) {
            if (activity.get().progressBar.isShown())
                activity.get().dismissProgressBar();

            if (activity.get().progressBar_bottom.isShown())
                activity.get().progressBar_bottom.setVisibility(View.GONE);

            activity.get().adapter.addItem(bean[0]);
        }


        @Override
        protected void onPostExecute(String result) {
            ImagesFragment context = activity.get();

            if (!searchSuccess) {
                context.dismissProgressBar();
                Toast.makeText(ImagesFragment.context, "Sorry, no image with " + result + " was found!", Toast.LENGTH_LONG).show();
            }

            if (context.progressBar.isShown())
                context.dismissProgressBar();


            if (context.progressBar_bottom.isShown())
                context.progressBar_bottom.setVisibility(View.GONE);

            working = false;
            activity = null;
        }
    }
}
