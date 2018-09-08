package com.tcl.a2group.niceplayer.fragment;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.tcl.a2group.niceplayer.activity.PlayActivity;
import com.tcl.a2group.niceplayer.adapter.PlayListAdapter;
import com.tcl.a2group.niceplayer.entity.MusicAttribute;
import com.tcl.a2group.niceplayer.util.PictureUtil;
import com.tcl.a2group.niceplayer.viewholder.PlayListViewHolder;
import com.tcl.a2group.niceplayer.R;

import java.util.ArrayList;
import java.util.List;

public class PlayListFragment extends Fragment {
    private static final String TAG = "PlayListFragment";

    private List<MusicAttribute> mPlayList;
    private RecyclerView mRecyclerView;

    public PlayListFragment() {
        super();
    }

    public void setPlayList(List<MusicAttribute> mPlayList) {
        this.mPlayList = mPlayList;
    }

    @Override
    public void setArguments(@Nullable Bundle args) {
        super.setArguments(args);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PlayActivity playActivity = ((PlayActivity) getActivity());
        if(playActivity != null)
            mPlayList = playActivity.getMusicAttributeList();
        else mPlayList = new ArrayList<>();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_play_list, container, false);
        mRecyclerView = view.findViewById(R.id.play_list_view);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        mRecyclerView.setAdapter(new PlayListAdapter(mPlayList, getContext()));
        mRecyclerView.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        mRecyclerView.setFocusable(true);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "onViewCreated: recycler view child count: " + mRecyclerView.getChildCount());
//        PictureUtil.blur(PictureUtil.drawableToBitmap(view.getBackground()), view);
    }

    public int getFocusItemIndex(){
        PlayListViewHolder playListViewHolder =((PlayListViewHolder) mRecyclerView.findContainingViewHolder(mRecyclerView.getFocusedChild()));
        if (playListViewHolder != null) {
            return playListViewHolder.getIndex();
        }else return -1;
    }
}
