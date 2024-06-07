package com.example.onviftest.recycle;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.onviftest.R;
import com.example.onviftest.model.Device;

import java.util.ArrayList;
import java.util.List;

// MyAdapter.java
public class MyAdapter extends RecyclerView.Adapter<MyViewHolder> {
    private ArrayList<Device> deviceList;
    private OnItemClickListener listener;
    private int selectedPosition = RecyclerView.NO_POSITION;

    public MyAdapter(ArrayList<Device> deviceList, OnItemClickListener listener) {
        this.deviceList = deviceList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_view, parent, false);
        return new MyViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MyViewHolder holder, int position) {
        Device device = deviceList.get(position);
        holder.bind(device, position == selectedPosition);

        holder.itemView.setOnClickListener(v -> {
            // 更新选中的位置
            notifyItemChanged(selectedPosition);
            selectedPosition = holder.getAdapterPosition();
            notifyItemChanged(selectedPosition);

            // 调用监听器
            listener.onItemClick(device);
        });
    }

    @Override
    public int getItemCount() {
        return deviceList.size();
    }

    public void setSelectedPosition(int position) {
        if ((position >= 0 && position < deviceList.size()) || position == -1) {
            notifyItemChanged(selectedPosition);
            selectedPosition = position;
            notifyItemChanged(selectedPosition);
        }
    }

    public interface OnItemClickListener {
        void onItemClick(Device device);
    }
}
