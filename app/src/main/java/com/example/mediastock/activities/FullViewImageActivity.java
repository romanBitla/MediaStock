package com.example.mediastock.activities;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.ImageView;

import com.example.mediastock.R;

/**
 * Created by dinu on 25/10/15.
 */
public class FullViewImageActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.full_view_activity);

        ImageView image = (ImageView) findViewById(R.id.full_view_image);

        //image.setImageBitmap(getIntent().getParcelableExtra("image"));

    }
}