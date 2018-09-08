package com.tcl.a2group.niceplayer.adapter;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import com.tcl.a2group.niceplayer.viewholder.PlayListViewHolder;
import com.tcl.a2group.niceplayer.R;
import com.tcl.a2group.niceplayer.entity.MusicAttribute;

import java.util.List;

public class PlayListAdapter extends RecyclerView.Adapter {
    private static final String TAG = "PlayListAdapter";
    private List<MusicAttribute> musicAttributeList;
    private Context context;

    public PlayListAdapter(List<MusicAttribute> musicAttributeList, Context context) {
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
        Log.d(TAG, "onBindViewHolder: index " + i);
        PlayListViewHolder playListViewHolder = (PlayListViewHolder) viewHolder;
        playListViewHolder.setIndex(i);
        playListViewHolder.getImageView().setImageDrawable(context.getDrawable(R.drawable.default_music_cover));
        MusicAttribute musicAttribute = musicAttributeList.get(i);
        playListViewHolder.getInfo1().setText(musicAttribute.getName());
        playListViewHolder.getInfo2().setText(musicAttribute.getArtist() + " - " + musicAttribute.getAlbum());
    }

    @Override
    public int getItemCount() {
        return musicAttributeList.size();
    }

    public void requestFocusForChild(int index){

    }
}
