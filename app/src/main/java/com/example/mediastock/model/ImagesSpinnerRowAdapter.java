package com.example.mediastock.model;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.mediastock.R;
import com.example.mediastock.activities.FavoriteImagesActivity;

import java.util.ArrayList;


public class ImagesSpinnerRowAdapter extends AbstractSpinnerRowAdapter {
    // black, white, red, blue, green, yellow, orange, magenta, cyan
    private final static String[] colorsID = {"#000000", "#ffffff", "#dc020e", "#0226dc", "#15a415", "#ffea00", "#ff8800", "#ff00ff", "#00ffff"};
    private final ArrayList<String> data;

    public ImagesSpinnerRowAdapter(FavoriteImagesActivity context, int textViewResourceId, ArrayList<String> objects) {
        super(context, textViewResourceId, objects);

        this.data = objects;
    }

    @Override
    public View getCustomView(int position, View covertView, ViewGroup parent) {
        View row = getInflater().inflate(R.layout.images_spinner_rows, parent, false);

        TextView color = (TextView) row.findViewById(R.id.text_spinner_row);
        ImageView image = (ImageView) row.findViewById(R.id.im_spinner_row);

        color.setText(data.get(position));
        GradientDrawable gradientDrawable = (GradientDrawable) image.getDrawable();
        gradientDrawable.setColor(Color.parseColor(colorsID[position]));

        return row;
    }
}
