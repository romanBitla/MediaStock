package com.example.mediastock.activities;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.graphics.Palette;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import com.example.mediastock.R;
import com.example.mediastock.data.DBController;
import com.example.mediastock.data.VideoBean;
import com.example.mediastock.util.ExecuteExecutor;
import com.example.mediastock.util.Utilities;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * A simple video player activity
 *
 * @author Dinu
 */
public class VideoPlayerActivity extends Activity implements SurfaceHolder.Callback, OnSeekBarChangeListener, View.OnClickListener {
    private static Handler myHandler = new Handler();
    public int oneTimeOnly = 0;
    private LinearLayout layoutController, layoutDescription;
    private ProgressDialog progressDialog;
    private double startTime = 0;
    private double finalTime = 0;
    private boolean finished = false;
    private WeakReference<SurfaceHolder> surfaceHolder;
    private MediaPlayer mediaPlayer;
    private SurfaceView surfaceView;
    private Button pause, play;
    private SeekBar seekbar;
    private TextView tx1, tx2;
    private boolean isPaused = false;
    private FloatingActionButton favorites;
    private boolean videoToDB = false;
    private boolean offlineWork = false;
    private String url;
    private int videoID;
    private DBController db;
    private VideoBean bean;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.video_player_activity);

        // the database
        db = new DBController(this);

        layoutDescription = (LinearLayout) this.findViewById(R.id.layoutVideo_title);
        layoutController = (LinearLayout) this.findViewById(R.id.layout_controller);
        favorites = (FloatingActionButton) this.findViewById(R.id.fab_favorites);
        favorites.setOnClickListener(this);
        favorites.bringToFront();

        constructProgressDialog();
        showProgressDialog("Loading...");

        play = (Button) this.findViewById(R.id.button_playvideoplayer);
        pause = (Button) this.findViewById(R.id.button_pausevideoplayer);
        tx2 = (TextView) findViewById(R.id.textView2_video);
        tx1 = (TextView) findViewById(R.id.textView3_video);

        // get beans info
        bean = getBeanFromIntent();
        url = bean.getPreview();
        videoID = Integer.valueOf(bean.getId());

        // set description of the video
        final TextView description = (TextView) this.findViewById(R.id.textView_video_player_title);
        description.setText(bean.getDescription());

        // seekbar
        seekbar = (SeekBar) findViewById(R.id.seekBar_video);
        seekbar.setClickable(false);
        seekbar.setOnSeekBarChangeListener(this);
        seekbar.getThumb().setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_ATOP);
        seekbar.getProgressDrawable().setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_ATOP);

        // media player
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                progressDialog.dismiss();
                playVideo(surfaceHolder.get());
                getColors();
            }
        });

        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {

            @Override
            public void onCompletion(MediaPlayer mp) {
                play.setVisibility(View.VISIBLE);
                pause.setVisibility(View.GONE);
                finished = true;
            }
        });

        play.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                playVideo(surfaceHolder.get());
            }
        });

        pause.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                play.setVisibility(View.VISIBLE);
                pause.setVisibility(View.GONE);
                mediaPlayer.pause();
            }
        });

        // check if online
        if (getIntentType() == 2) {
            computeOfflineWork();

        } else {

            try {

                // set online source
                mediaPlayer.setDataSource(url);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // surface
        getWindow().setFormat(PixelFormat.UNKNOWN);
        surfaceView = (SurfaceView) findViewById(R.id.surfaceview);
        surfaceHolder = new WeakReference<>(surfaceView.getHolder());
        surfaceHolder.get().addCallback(this);
        surfaceHolder.get().setSizeFromLayout();
        surfaceView.setOnClickListener(this);

        // did we saved the video previously ? if so .. change the fab color
        checkExistingVideoInDB(videoID);
    }

    /**
     * Show/hide some views when the screen configuration changes
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // checks the orientation of the screen
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            layoutController.setVisibility(View.GONE);
            layoutDescription.setVisibility(View.GONE);

        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            layoutController.setVisibility(View.VISIBLE);
            layoutDescription.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Method to compute all the offline work. We play the video that is saved to the internal storage
     */
    private void computeOfflineWork() {
        offlineWork = true;

        // path of the video file
        String path = bean.getPath();

        try {

            FileInputStream fileInputStream = Utilities.loadMediaFromInternalStorage(Utilities.VIDEO_DIR, this, path);

            mediaPlayer.setDataSource(fileInputStream.getFD());

            fileInputStream.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * We check in the database if the video with id videoID exists.
     * If exists we change the fab color.
     *
     * @param videoID the id of the video
     */
    private void checkExistingVideoInDB(int videoID) {
        if (db.checkExistingVideo(videoID)) {
            videoToDB = true;
            favorites.setBackgroundTintList(ColorStateList.valueOf(Color.RED));
        }
    }

    private int getIntentType() {
        return getIntent().getBundleExtra("bean").getInt("type");
    }

    private VideoBean getBeanFromIntent() {
        return getIntent().getBundleExtra("bean").getParcelable("bean");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }

        if (surfaceView != null) {
            surfaceView.getHolder().getSurface().release();
            surfaceView.destroyDrawingCache();
            surfaceView = null;
            surfaceHolder = null;
        }
    }


    @Override
    protected void onStop() {
        super.onStop();

        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            isPaused = true;
            play.setVisibility(View.VISIBLE);
            pause.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            isPaused = true;
            play.setVisibility(View.VISIBLE);
            pause.setVisibility(View.GONE);
        }
    }

    @Override
    public void onBackPressed() {
        mediaPlayer.stop();

        if (offlineWork) {
            Intent i = new Intent(getApplicationContext(), FavoriteVideosActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY);
            startActivity(i);
        }

        super.onBackPressed();
        overridePendingTransition(R.anim.trans_corner_from, R.anim.trans_corner_to);
    }


    private void playVideo(SurfaceHolder holder) {
        pause.setVisibility(View.VISIBLE);
        play.setVisibility(View.GONE);

        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mediaPlayer.setDisplay(holder);

        finalTime = mediaPlayer.getDuration();

        if (finished) {
            finished = false;
            startTime = 0;
        } else
            startTime = mediaPlayer.getCurrentPosition();

        mediaPlayer.start();

        if (oneTimeOnly == 0) {
            seekbar.setMax((int) finalTime);
            oneTimeOnly = 1;
        }

        tx2.setText(String.format("%d:%d",
                        TimeUnit.MILLISECONDS.toMinutes((long) finalTime),
                        TimeUnit.MILLISECONDS.toSeconds((long) finalTime) -
                                TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes((long) finalTime)))
        );

        tx1.setText(String.format("%d:%d",
                        TimeUnit.MILLISECONDS.toMinutes((long) startTime),
                        TimeUnit.MILLISECONDS.toSeconds((long) startTime) -
                                TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes((long) startTime)))
        );

        seekbar.setProgress((int) startTime);
        myHandler.postDelayed(new UpdateTime(this), 100);
    }

    private void constructProgressDialog() {
        progressDialog = new ProgressDialog(VideoPlayerActivity.this);
        progressDialog.setCancelable(false);
    }

    private void showProgressDialog(String message) {
        progressDialog.setMessage(message);
        progressDialog.show();
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser)
            mediaPlayer.seekTo(progress);
    }


    @Override
    public void surfaceCreated(SurfaceHolder holder) {

        if (isPaused) {
            playVideo(holder);
            isPaused = false;

        } else
            mediaPlayer.prepareAsync();
    }


    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onClick(View v) {

        if (v.getId() == R.id.fab_favorites) {

            // if the video is already saved in the db, it means we want to remove the video
            if (videoToDB) {
                videoToDB = false;
                favorites.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#838383")));

                showProgressDialog("Removing video from favorites...");

                // thread to remove the video from db
                new ExecuteExecutor(this, 1, new ExecuteExecutor.CallableAsyncTask(this) {

                    @Override
                    public String call() {
                        VideoPlayerActivity activity = (VideoPlayerActivity) getContextRef();

                        // path of the video
                        String path = activity.db.getVideoPath(activity.videoID);

                        // delete videos info
                        activity.db.deleteVideoInfo(activity.videoID);

                        // delete video from storage
                        Utilities.deleteSpecificMediaFromInternalStorage(Utilities.VIDEO_DIR, activity, path);

                        activity.progressDialog.dismiss();

                        return "Video removed from favorites";
                    }
                });

                // we have to add the video to favorites
            } else {
                videoToDB = true;
                favorites.setBackgroundTintList(ColorStateList.valueOf(Color.RED));

                showProgressDialog("Adding video to favorites...");

                // thread to add the video to favorites
                new ExecuteExecutor(this, 1, new ExecuteExecutor.CallableAsyncTask(this) {

                    @Override
                    public String call() {
                        VideoPlayerActivity context = (VideoPlayerActivity) getContextRef();
                        HttpURLConnection con = null;
                        InputStream stream = null;
                        String path = "";

                        try {

                            URL urll = new URL(context.url);
                            con = (HttpURLConnection) urll.openConnection();
                            stream = con.getInputStream();

                            // save stream video to storage
                            path += Utilities.saveMediaToInternalStorage(Utilities.VIDEO_DIR, context, stream, context.videoID);

                        } catch (IOException e) {
                            e.printStackTrace();
                        } finally {
                            // clean the objects
                            try {
                                if (stream != null) {
                                    con.disconnect();
                                    stream.close();
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }

                        // add video info to database
                        context.addVideoInfoToDB(path, context.videoID, context.bean.getDescription());
                        context.progressDialog.dismiss();

                        return "Video added to favorites";
                    }
                });


            }
        }

        // click on the view
        if (v.getId() == R.id.surfaceview) {
            if (layoutController.getVisibility() == View.GONE)
                layoutController.setVisibility(View.VISIBLE);
            else if (layoutController.getVisibility() == View.VISIBLE)
                layoutController.setVisibility(View.GONE);
        }
    }

    /**
     * Thread to add to database the videos info
     *
     * @param path        the path where the video is stored
     * @param videoID     the id of the video
     * @param description the description of the video
     */
    private void addVideoInfoToDB(final String path, final int videoID, final String description) {

        new ExecuteExecutor(this, 2, new ExecuteExecutor.CallableAsyncTask(this) {

            @Override
            public String call() {
                VideoPlayerActivity context = (VideoPlayerActivity) getContextRef();
                HttpURLConnection con = null;
                InputStream is = null;
                String category = "";

                // first, get the category of the video
                try {
                    URL url = new URL("https://api.shutterstock.com/v2/videos/" + context.videoID);
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
                        JsonArray array = o.get("categories") != null ? o.get("categories").getAsJsonArray() : null;

                        if (array != null) {
                            if (array.size() > 0)
                                category += array.get(0).getAsJsonObject().get("name") != null ? array.get(0).getAsJsonObject().get("name").getAsString() : " - ";
                        }

                        // save video info to database
                        context.db.insertVideoInfo(path, videoID, description, category);
                    }
                } catch (SocketTimeoutException e) {
                    if (con != null)
                        con.disconnect();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    // clean the objects
                    try {
                        if (is != null) {
                            con.disconnect();
                            is.close();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                return null;
            }
        });


    }

    private void getColors() {
        final Set<Integer> set = new HashSet<>();

        // extract the frames from the favorite video
        new ExecuteExecutor(this, 2, new ExecuteExecutor.CallableAsyncTask(this) {

            @Override
            public String call() {
                final VideoPlayerActivity context = (VideoPlayerActivity) this.getContextRef();

                // get path of the video
                FileInputStream fileInputStream = Utilities.loadMediaFromInternalStorage(Utilities.VIDEO_DIR, context, context.bean.getPath());

                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                try {
                    retriever.setDataSource(fileInputStream.getFD());
                } catch (IOException e) {
                    e.printStackTrace();
                }

                // get frames
                final Bitmap[] img = new Bitmap[3];
                img[0] = retriever.getFrameAtTime(10);
                img[1] = retriever.getFrameAtTime(context.mediaPlayer.getDuration() / 2);
                img[2] = retriever.getFrameAtTime(context.mediaPlayer.getDuration() - 1);

                for (int i = 0; i < 3; i++) {

                    // get the color palette of the frames
                    Palette.from(img[0]).generate(new Palette.PaletteAsyncListener() {
                        @Override
                        public void onGenerated(Palette palette) {

                            // color palette
                            int vibrant = 0;
                            int lightVibrant = 0;
                            int darkVibrant = 0;
                            int muted = 0;
                            int lightMuted = 0;
                            int darkMuted = 0;

                            Palette.Swatch vibrantSwatch = palette.getVibrantSwatch();
                            Palette.Swatch darkVibrantSwatch = palette.getDarkVibrantSwatch();
                            Palette.Swatch lightVibrantSwatch = palette.getLightVibrantSwatch();

                            Palette.Swatch mutedSwatch = palette.getMutedSwatch();
                            Palette.Swatch lightMutedSwatch = palette.getLightMutedSwatch();
                            Palette.Swatch darkMutedSwatch = palette.getDarkMutedSwatch();

                            if (vibrantSwatch != null)
                                vibrant = vibrantSwatch.getRgb();

                            if (lightVibrantSwatch != null)
                                lightVibrant = lightVibrantSwatch.getRgb();

                            if (darkVibrantSwatch != null)
                                darkVibrant = darkVibrantSwatch.getRgb();

                            if (mutedSwatch != null)
                                muted = mutedSwatch.getRgb();

                            if (darkMutedSwatch != null)
                                darkMuted = darkMutedSwatch.getRgb();

                            if (lightMutedSwatch != null)
                                lightMuted = lightMutedSwatch.getRgb();

                            set.add(vibrant);
                            set.add(lightVibrant);
                            set.add(darkVibrant);
                            set.add(muted);
                            set.add(lightMuted);
                            set.add(darkMuted);
                        }
                    });
                }

                retriever.release();
                return null;
            }
        });


    }



    /**
     * Thread to update the time of the video
     */
    private static class UpdateTime implements Runnable {
        private static WeakReference<VideoPlayerActivity> activity;

        public UpdateTime(VideoPlayerActivity context) {
            activity = new WeakReference<>(context);
        }

        public void run() {
            VideoPlayerActivity context = activity.get();

            if (context.mediaPlayer != null && context.mediaPlayer.isPlaying()) {
                context.startTime = context.mediaPlayer.getCurrentPosition();
                context.tx1.setText(String.format("%d:%d",
                        TimeUnit.MILLISECONDS.toMinutes((long) context.startTime),
                        TimeUnit.MILLISECONDS.toSeconds((long) context.startTime) -
                                TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes((long) context.startTime))));

                context.seekbar.setProgress((int) context.startTime);
                myHandler.postDelayed(this, 100);
            }
        }
    }
}
