package com.example.mediastock.activities;

import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.Toast;

import com.example.mediastock.R;
import com.example.mediastock.data.DBController;
import com.example.mediastock.data.DBHelper;
import com.example.mediastock.data.ImageBean;
import com.example.mediastock.model.FavoriteImageAdapter;
import com.example.mediastock.model.ImagesSpinnerRowAdapter;
import com.example.mediastock.util.ColorHelper;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Activity to display the favorite images. It also filters the images by color or by another image.
 */
public class FavoriteImagesActivity extends AppCompatActivity implements View.OnClickListener {
    private final static String[] colors = {"Black", "White", "Red", "Blue", "Green", "Yellow", "Orange", "Magenta", "Cyan"};
    private final ArrayList<String> rows = new ArrayList<>();
    private ImagesSpinnerRowAdapter spinnerRowAdapter;
    private FavoriteImageAdapter adapter;
    private FloatingActionButton fabFilter;
    private DBController db;
    private Cursor cursor;
    private int width;
    private boolean selectImageForFilter = false;
    private int cursorTempCount = 0;
    private int colorSelectedPosition;
    private MenuItem menuAcceptSelectedImgs;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.favorite_images);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        setTitle("Favorite images");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

        // get images from db
        db = new DBController(this);
        cursor = db.getImagesInfo();

        // create the spinner model rows and the adapter
        createSpinnerModelRows();

        // layout init
        fabFilter = (FloatingActionButton) this.findViewById(R.id.fab_options);
        fabFilter.setOnClickListener(this);
        width = getResources().getDisplayMetrics().widthPixels;

        final GridView gridView = (GridView) this.findViewById(R.id.gridView_fav_images);
        gridView.setColumnWidth(width / 2);
        adapter = new FavoriteImageAdapter(this);
        gridView.setAdapter(adapter);
        gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                if (!selectImageForFilter)
                    goToDisplayImageActivity(position);
                else {

                    // mark the image as selected
                    ImageView checkImage = (ImageView) view.findViewById(R.id.checkImage);

                    if (checkImage.getVisibility() == View.VISIBLE) {
                        checkImage.setVisibility(View.GONE);
                        adapter.removeSelectedImagePosition(position);
                    } else {
                        checkImage.setVisibility(View.VISIBLE);
                        adapter.addSelectedImagePosition(position, position);
                    }
                }
            }
        });

        if (cursor.getCount() == 0)
            Toast.makeText(getApplicationContext(), "There are no images saved!", Toast.LENGTH_SHORT).show();
        else
            new AsyncDBWork(this, 1).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR); // get the favorite images
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.favorites_menu, menu);

        menuAcceptSelectedImgs = menu.findItem(R.id.item_ok);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);

        // refresh the image list
        if (item.getItemId() == R.id.item_refresh) {

            if (selectImageForFilter)
                dismissSelectImageOption();

            refreshGridView();
        }

        // filter the images by the image selected
        if (item.getItemId() == R.id.item_ok) {

            if (adapter.getSelectedImagesSize() > 1) {
                Toast.makeText(getApplicationContext(), "You can select only one image!", Toast.LENGTH_SHORT).show();
                return true;
            }

            dismissSelectImageOption();
            filterImagesByImage();
        }

        return true;
    }

    /**
     * Is starts a thread to filter the images by an image selected by the user
     */
    private void filterImagesByImage() {
        int selectedPos = adapter.getSelectedImagePosition();

        if (selectedPos == -1) {
            Toast.makeText(getApplicationContext(), "You have to select an image!", Toast.LENGTH_SHORT).show();
            return;
        }

        // get bean at the position selected
        final ImageBean bean = (ImageBean) adapter.getItem(selectedPos);

        // first remove the images from the adapter
        adapter.deleteBeansList();

        // start thread to filter the images
        new AsyncDBWork(this, 3).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, bean.getId());
    }

    /**
     * Method to start the DisplayImageActivity to see the details of the image selected.
     *
     * @param position the position of the selected image
     */
    private void goToDisplayImageActivity(int position) {
        final Bundle bundle = new Bundle();
        final ImageBean bean = (ImageBean) adapter.getItem(position);

        Intent intent = new Intent(getApplicationContext(), DisplayImageActivity.class);
        bundle.putInt("type", 2);
        bundle.putParcelable("bean", bean);
        intent.putExtra("bean", bundle);

        startActivity(intent);
        overridePendingTransition(R.anim.trans_corner_from, R.anim.trans_corner_to);
    }

    @Override
    protected void onStop() {
        super.onStop();

        // save cursor count and then close it
        cursorTempCount = cursor.getCount();
        cursor.close();
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
        overridePendingTransition(R.anim.trans_corner_from, R.anim.trans_corner_to);
    }


    @Override
    protected void onRestart() {
        super.onRestart();

        // get the current cursor
        cursor = db.getImagesInfo();

        if (cursor.getCount() == 0) {
            adapter.deleteBeansList();

            Toast.makeText(getApplicationContext(), "There are no images saved", Toast.LENGTH_SHORT).show();
            return;
        }

        // an image has been removed from favorites
        if (cursorTempCount > cursor.getCount())
            refreshGridView();

        // a new image has been added to favorites
        if (cursorTempCount < cursor.getCount())
            refreshGridView();
    }

    @Override
    public void onClick(View v) {

        if (v.getId() == R.id.fab_options) {
            fabFilter.setClickable(false);

            showPopupMenu();
        }
    }

    /**
     * Method to show a popup menu to filter the images
     */
    private void showPopupMenu() {
        if (selectImageForFilter)
            dismissSelectImageOption();

        int layoutWidth = (width / 2) + (width / 3);

        LayoutInflater layoutInflater = (LayoutInflater) this.getSystemService(LAYOUT_INFLATER_SERVICE);
        final View popupView = layoutInflater.inflate(R.layout.popup_window_images, null);

        Spinner rows = (Spinner) popupView.findViewById(R.id.spinner_rows);
        rows.setAdapter(spinnerRowAdapter);
        rows.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

                // the selected color position
                colorSelectedPosition = position;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        final PopupWindow popupWindow = new PopupWindow(popupView, layoutWidth, RelativeLayout.LayoutParams.WRAP_CONTENT);

        Button buttonDismiss = (Button) popupView.findViewById(R.id.dismiss);
        Button buttonOK = (Button) popupView.findViewById(R.id.accept);
        Button buttonSelectImg = (Button) popupView.findViewById(R.id.button_selectImage);

        // select images
        buttonSelectImg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                selectImageForFilter = true;
                popupWindow.dismiss();
                fabFilter.setClickable(true);

                showSelectImageOption();
            }
        });

        // dismiss
        buttonDismiss.setOnClickListener(new Button.OnClickListener() {

            @Override
            public void onClick(View v) {

                popupWindow.dismiss();
                fabFilter.setClickable(true);
            }
        });

        // filter by color
        buttonOK.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // first remove the images from the adapter
                adapter.deleteBeansList();

                // thread to filter the images by color
                new AsyncDBWork(FavoriteImagesActivity.this, 2).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, colorSelectedPosition);

                popupWindow.dismiss();
                fabFilter.setClickable(true);
            }
        });

        popupWindow.showAtLocation(fabFilter, Gravity.CENTER, 0, 0);
    }


    /**
     * Method to refresh the gridView. It loads the images from the storage
     */
    private void refreshGridView() {

        // first remove current images
        adapter.deleteBeansList();

        // get the favorite images
        new AsyncDBWork(this, 1).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }


    private void dismissSelectImageOption() {

        // restore values
        setTitle("Favorite images");
        menuAcceptSelectedImgs.setVisible(false);
        selectImageForFilter = false;
    }

    private void showSelectImageOption() {
        setTitle("Select image");
        menuAcceptSelectedImgs.setVisible(true);

        // clear previous selected images
        adapter.clearSelectedImages();
    }

    private void createSpinnerModelRows() {
        rows.addAll(Arrays.asList(colors));

        // adapter for the spinner
        spinnerRowAdapter = new ImagesSpinnerRowAdapter(FavoriteImagesActivity.this, R.layout.images_spinner_rows, rows);
    }


    /**
     * Class to do asynchronous operations. It loads the images from the storage or it filters the images.
     */
    private static class AsyncDBWork extends AsyncTask<Integer, ImageBean, Void> {
        private static WeakReference<FavoriteImagesActivity> activity;
        private final int type;
        private boolean success = false;

        /**
         * Constructor.
         *
         * @param context the context
         * @param type    1 - get favorite images; 2 - filter images; 3 - filter images by another image
         */
        public AsyncDBWork(FavoriteImagesActivity context, int type) {
            activity = new WeakReference<>(context);
            this.type = type;
        }


        @Override
        protected Void doInBackground(Integer... params) {

            switch (type) {

                case 1:
                    getFavoriteImages();
                    break;

                case 2:
                    filterImages(params[0]);
                    break;

                case 3:
                    filterImagesByImage(params[0]);
                    break;

                default:
                    break;

            }

            return null;
        }

        /**
         * It gets the images info from database and then it adds the result to the adapters list to load the images from storage
         */
        private void getFavoriteImages() {
            FavoriteImagesActivity context = activity.get();
            int pos = 0;

            context.cursor.moveToFirst();
            do {

                final ImageBean bean = new ImageBean();
                bean.setPos(pos);
                bean.setId(context.cursor.getInt(context.cursor.getColumnIndex(DBHelper.IMG_ID)));
                bean.setDescription(context.cursor.getString(context.cursor.getColumnIndex(DBHelper.DESCRIPTION_IMG)));
                bean.setAuthor(context.cursor.getString(context.cursor.getColumnIndex(DBHelper.AUTHOR_IMG)));
                bean.setPath(context.cursor.getString(context.cursor.getColumnIndex(DBHelper.IMG_PATH)));

                // add the image bean to the adapter to update the UI
                publishProgress(bean);

                pos++;
            } while (context.cursor.moveToNext());
        }

        /**
         * Iterate over all images and find out if the color colorTarget is similar to the color palette of each image.
         * When an image is found, we add the images info to the adapters list, to load the image from storage
         */
        private void filterImages(int colorTarget) {
            FavoriteImagesActivity context = activity.get();
            int pos = 0;

            // get from the db the color palettes of the images
            Cursor cursorColor = context.db.getColorPalettes();

            ColorHelper colorHelper = new ColorHelper();
            colorHelper.setTargetColorFromArray(colorTarget);
            do {

                int vibrant = cursorColor.getInt(cursorColor.getColumnIndex(DBHelper.VIBRANT));
                int lightVibrant = cursorColor.getInt(cursorColor.getColumnIndex(DBHelper.LIGHT_VIBRANT));
                int darkVibrant = cursorColor.getInt(cursorColor.getColumnIndex(DBHelper.DARK_VIBRANT));
                int muted = cursorColor.getInt(cursorColor.getColumnIndex(DBHelper.MUTED));
                int lightMuted = cursorColor.getInt(cursorColor.getColumnIndex(DBHelper.LIGHT_MUTED));
                int darkMuted = cursorColor.getInt(cursorColor.getColumnIndex(DBHelper.DARK_MUTED));

                // if the color palette of the current image is similar to the color chosen by the user
                if (colorHelper.getColorSimilarity(vibrant, darkVibrant, lightVibrant, muted, darkMuted, lightMuted)) {
                    context.cursor.moveToPosition(cursorColor.getPosition());

                    final ImageBean bean = new ImageBean();
                    bean.setPos(pos);
                    bean.setId(context.cursor.getInt(context.cursor.getColumnIndex(DBHelper.IMG_ID)));
                    bean.setDescription(context.cursor.getString(context.cursor.getColumnIndex(DBHelper.DESCRIPTION_IMG)));
                    bean.setAuthor(context.cursor.getString(context.cursor.getColumnIndex(DBHelper.AUTHOR_IMG)));
                    bean.setPath(context.cursor.getString(context.cursor.getColumnIndex(DBHelper.IMG_PATH)));

                    // we add the image bean to the adapter to update the UI
                    publishProgress(bean);

                    success = true;
                    pos++;
                }

            } while (cursorColor.moveToNext());

            cursorColor.close();
        }

        /**
         * First we get the dominant color of the image selected and then we iterate over all images and find out if the
         * dominant color of the image selected is similar to the color palette of each image.
         * When an image is found, we add the images info to the adapters list, to load the image from storage
         *
         * @param imageID the id of the image
         */
        private void filterImagesByImage(int imageID) {
            FavoriteImagesActivity context = activity.get();
            int pos = 0;

            // get from database the color palette of the images
            Cursor cursorColor = context.db.getColorPalettes();

            // get the dominant color of the image selected
            int dominantColor = context.db.getDominantColorOfImage(imageID);

            ColorHelper colorHelper = new ColorHelper();
            colorHelper.setTargetColor(dominantColor);
            do {

                int vibrant = cursorColor.getInt(cursorColor.getColumnIndex(DBHelper.VIBRANT));
                int lightVibrant = cursorColor.getInt(cursorColor.getColumnIndex(DBHelper.LIGHT_VIBRANT));
                int darkVibrant = cursorColor.getInt(cursorColor.getColumnIndex(DBHelper.DARK_VIBRANT));
                int muted = cursorColor.getInt(cursorColor.getColumnIndex(DBHelper.MUTED));
                int lightMuted = cursorColor.getInt(cursorColor.getColumnIndex(DBHelper.LIGHT_MUTED));
                int darkMuted = cursorColor.getInt(cursorColor.getColumnIndex(DBHelper.DARK_MUTED));

                // if the color palette of the current image is similar to the dominant color of the image selected
                if (colorHelper.getColorSimilarity(vibrant, darkVibrant, lightVibrant, muted, darkMuted, lightMuted)) {
                    context.cursor.moveToPosition(cursorColor.getPosition());

                    final ImageBean bean = new ImageBean();
                    bean.setPos(pos);
                    bean.setId(context.cursor.getInt(context.cursor.getColumnIndex(DBHelper.IMG_ID)));
                    bean.setDescription(context.cursor.getString(context.cursor.getColumnIndex(DBHelper.DESCRIPTION_IMG)));
                    bean.setAuthor(context.cursor.getString(context.cursor.getColumnIndex(DBHelper.AUTHOR_IMG)));
                    bean.setPath(context.cursor.getString(context.cursor.getColumnIndex(DBHelper.IMG_PATH)));

                    // we add the image bean to adapter to update the UI
                    publishProgress(bean);

                    success = true;
                    pos++;
                }

            } while (cursorColor.moveToNext());

            cursorColor.close();
        }

        @Override
        protected void onProgressUpdate(ImageBean... values) {
            super.onProgressUpdate(values);

            activity.get().adapter.addImageBean(values[0]);
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            if (type != 1)
                if (!success)
                    Toast.makeText(activity.get().getApplicationContext(), "No image was found!", Toast.LENGTH_SHORT).show();

            activity = null;
        }
    }
}
