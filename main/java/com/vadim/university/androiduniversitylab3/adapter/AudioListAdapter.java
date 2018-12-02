package com.vadim.university.androiduniversitylab3.adapter;

import android.content.Context;
import android.graphics.Color;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.vadim.university.androiduniversitylab3.R;
import com.vadim.university.androiduniversitylab3.util.TimeUtils;
import com.vadim.university.androiduniversitylab3.model.Audio;

import java.util.List;

public class AudioListAdapter extends RecyclerView.Adapter<AudioListAdapter.ViewHolder> {

    private List<Audio> audioList;
    private Context mContext;
    private static OnItemClickListener clickListener;
    private int selectedIndex = -1;

    public AudioListAdapter(List<Audio> audios, Context context){
        audioList = audios;
        mContext = context;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View rowView = LayoutInflater.from(parent.getContext()).inflate(R.layout.row_audio_list, parent, false);

        return new ViewHolder(rowView);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, final int position) {
        holder.title.setText(audioList.get(position).getTitle());
        holder.author.setText(audioList.get(position).getArtist());
        holder.time.setText(TimeUtils.milliSecondsToTimer(audioList.get(position).getDuration()));

        if(selectedIndex == position) {
            holder.itemView.setBackgroundColor(Color.parseColor("#000000"));
        } else {
            holder.itemView.setBackgroundColor(Color.parseColor("#ffffff"));
        }
    }

    @Override
    public int getItemCount() {
        return audioList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener{
        private final TextView title;
        private final TextView author;
        private final TextView time;

        public ViewHolder(View itemView) {
            super(itemView);

            title = (TextView) itemView.findViewById(R.id.audio_title);
            author = (TextView) itemView.findViewById(R.id.audio_author);
            time = (TextView) itemView.findViewById(R.id.audio_time);
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            clickListener.onClick(view, getAdapterPosition());
        }
    }

    public void setClickListener(OnItemClickListener itemClickListener) {
        clickListener = itemClickListener;
    }

    public void setSelectedItem(int position) {
        selectedIndex = position;
        notifyDataSetChanged();
    }

    public interface OnItemClickListener {
        void onClick(View view, int position);
    }
}
