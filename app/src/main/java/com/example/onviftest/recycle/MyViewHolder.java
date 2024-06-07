package com.example.onviftest.recycle;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.onviftest.R;
import com.example.onviftest.model.Device;

public class MyViewHolder extends RecyclerView.ViewHolder {
    public TextView nameTextView;
    public TextView ipTextView;

    public MyViewHolder(View itemView) {
        super(itemView);
        nameTextView = itemView.findViewById(R.id.nameTextView);
    }

    public void bind(Device device, boolean isSelected) {
        nameTextView.setText(device.getName());

        itemView.setBackgroundColor(isSelected ? Color.LTGRAY : Color.WHITE);
    }
}
