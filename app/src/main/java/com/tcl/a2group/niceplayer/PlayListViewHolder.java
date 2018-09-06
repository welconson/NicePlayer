package com.tcl.a2group.niceplayer;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

public class PlayListViewHolder extends RecyclerView.ViewHolder {
    private ImageView imageView;
    private TextView info1, info2;
    public PlayListViewHolder(@NonNull View itemView) {
        super(itemView);
        imageView = itemView.findViewById(R.id.play_list_item_img);
        info1 = itemView.findViewById(R.id.play_list_text_info1);
        info2 = itemView.findViewById(R.id.play_list_text_info2);
    }

    public ImageView getImageView() {
        return imageView;
    }

    public TextView getInfo1() {
        return info1;
    }

    public TextView getInfo2() {
        return info2;
    }
}
