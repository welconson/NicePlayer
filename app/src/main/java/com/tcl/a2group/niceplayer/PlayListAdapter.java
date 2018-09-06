package com.tcl.a2group.niceplayer;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import java.util.List;

public class PlayListAdapter extends RecyclerView.Adapter {
    private static final String TAG = "PlayListAdapter";
    private List<PlayActivity.MusicAttribute> musicAttributeList;
    private Context context;

    public PlayListAdapter(List<PlayActivity.MusicAttribute> musicAttributeList, Context context) {
        this.musicAttributeList = musicAttributeList;
        this.context = context;
    }

    @NonNull
    @Override
    public PlayListViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        return new PlayListViewHolder(LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.play_list_item, viewGroup, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int i) {
        PlayListViewHolder playListViewHolder = (PlayListViewHolder) viewHolder;
        playListViewHolder.getImageView().setImageDrawable(context.getDrawable(R.drawable.default_music_cover));
        PlayActivity.MusicAttribute musicAttribute = musicAttributeList.get(i);
        playListViewHolder.getInfo1().setText(musicAttribute.name);
        playListViewHolder.getInfo2().setText(musicAttribute.artist + " - " + musicAttribute.album);
    }

    @Override
    public int getItemCount() {
        return musicAttributeList.size();
    }

    public void requestFocusForChild(int index){
        
    }
}
