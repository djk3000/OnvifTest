package com.example.onviftest.fragment;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatButton;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.example.onviftest.IOnBackPressed;
import com.example.onviftest.MainActivity;
import com.example.onviftest.R;
import com.example.onviftest.model.Camera;
import com.example.onviftest.model.Device;
import com.example.onviftest.recycle.MyAdapter;
import com.example.onviftest.utiliy.ConfigManger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SettingsFragment extends Fragment {
    private IOnBackPressed iOnBackPressed;
    ArrayList<Device> deviceList;
    private RecyclerView recyclerView;
    private MyAdapter adapter;
    private Camera camera;
    private EditText editTextName;
    private EditText editIP;
    private EditText editX;
    private EditText editY;
    private EditText editZ;
    private Device currentDevice;
    private ConstraintLayout rightLayout;

    public static SettingsFragment newInstance() {
        SettingsFragment fragment = new SettingsFragment();
        return fragment;
    }

    public SettingsFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);
        rightLayout = view.findViewById(R.id.edit_layout);
        button_Click(view);
        setInit(view);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        updateView();
    }

    private void setInit(View view) {
        //读取json
        camera = ConfigManger.loadDeviceFromJson(getContext());
        if (camera != null) {
            deviceList = camera.getDeviceList();
        } else {
            deviceList = new ArrayList<>();
            deviceList.add(setNew());
            camera = new Camera();
            camera.setCurrentDevice(0);
            camera.setDeviceList(deviceList);
            ConfigManger.saveDeviceToJson(getContext(), camera);
        }

        //设置recycle列表
        recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        //选择摄像头
        adapter = new MyAdapter(deviceList, device -> {
            currentDevice = device;
            rightLayout.setVisibility(View.VISIBLE);
            updateView();
        });
        recyclerView.setAdapter(adapter);

        //设置默认
        int defaultSelectedPosition = camera.getCurrentDevice();
        if(camera.getDeviceList().size()>0) {
            currentDevice = camera.getDeviceList().get(defaultSelectedPosition);
            adapter.setSelectedPosition(defaultSelectedPosition);
            recyclerView.scrollToPosition(defaultSelectedPosition);
        }
    }

    private void button_Click(View view) {
        //关联界面
        editTextName = view.findViewById(R.id.edit_name);
        editIP = view.findViewById(R.id.edit_ip);
        editX = view.findViewById(R.id.edit_x);
        editY = view.findViewById(R.id.edit_y);
        editZ = view.findViewById(R.id.edit_z);

        //添加新设备按钮
        AppCompatButton addButton = view.findViewById(R.id.add_device);
        addButton.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("NotifyDataSetChanged")
            @Override
            public void onClick(View v) {
                deviceList.add(setNew());
                updateView();
                rightLayout.setVisibility(View.VISIBLE);
                adapter.notifyDataSetChanged();
                adapter.setSelectedPosition(deviceList.size() - 1);
            }
        });

        //关闭按钮
        ImageView imageView = view.findViewById(R.id.close);
        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeFragment();
            }
        });

        //保存
        AppCompatButton saveButton = view.findViewById(R.id.save_button);
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Optional<Device> device = findDeviceByUuid(camera.getDeviceList(), currentDevice.getUuid());
                if (device.isPresent()) {
                    device.get().setName(editTextName.getText().toString());
                    device.get().setIp(editIP.getText().toString());
                    try {
                        device.get().setX(Double.parseDouble(editX.getText().toString()));
                        device.get().setY(Double.parseDouble(editY.getText().toString()));
                        device.get().setZoom(Double.parseDouble(editZ.getText().toString()));
                    } catch (NumberFormatException e) {
                        Log.e("Conversion", "无法将字符串转换为双精度浮点数", e);
                    }
                    ConfigManger.saveDeviceToJson(getContext(), camera);
                    adapter.notifyDataSetChanged();
                    Toast.makeText(getContext(), "保存成功", Toast.LENGTH_SHORT).show();
                }
            }
        });

        //删除
        AppCompatButton deleteButton = view.findViewById(R.id.delete_button);
        deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Iterator<Device> iterator = deviceList.iterator();
                while (iterator.hasNext()) {
                    Device device = iterator.next();
                    if (device.getUuid().equals(currentDevice.getUuid())) {
                        if (findDeviceIndexById(deviceList, currentDevice.getUuid()) <= camera.getCurrentDevice()) {
                            camera.setCurrentDevice(camera.getCurrentDevice() - 1);
                        }
                        iterator.remove();
                    }
                }
                adapter.setSelectedPosition(-1);
                rightLayout.setVisibility(View.GONE);
                updateView();
                ConfigManger.saveDeviceToJson(getContext(), camera);
                Toast.makeText(getContext(), "删除成功", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private int findDeviceIndexById(List<Device> devices, String id) {
        for (int i = 0; i < devices.size(); i++) {
            if (devices.get(i).getUuid().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    @SuppressLint({"NotifyDataSetChanged", "SetTextI18n"})
    private void updateView() {
        if(currentDevice == null) {
            rightLayout.setVisibility(View.GONE);
            return;
        }

        new Thread(() -> {
            requireActivity().runOnUiThread(() -> editTextName.setText(currentDevice.getName()));
            requireActivity().runOnUiThread(() -> editIP.setText(currentDevice.getIp()));
            requireActivity().runOnUiThread(() -> editX.setText(Double.toString(currentDevice.getX())));
            requireActivity().runOnUiThread(() -> editY.setText(Double.toString(currentDevice.getY())));
            requireActivity().runOnUiThread(() -> editZ.setText(Double.toString(currentDevice.getZoom())));
        }).start();
        adapter.notifyDataSetChanged();
    }

    private Optional<Device> findDeviceByUuid(ArrayList<Device> deviceList, String uuid) {
        return deviceList.stream()
                .filter(device -> device.getUuid().equals(uuid))
                .findFirst();
    }

    private Device setNew() {
        Device device = new Device();
        device.setUuid(UUID.randomUUID().toString());
        device.setName("新设备");
        device.setIp("192.168.1.1");
        device.setX(0.18);
        device.setY(0.64);
        device.setZoom(1);
        currentDevice = device;
        return device;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        try {
            iOnBackPressed = (IOnBackPressed) context;
        } catch (ClassCastException e) {
            Log.e("Settings Fragment", "Settings Fragment: " + context.toString() + " must implement OnBackPressedListener");
        }
    }

    public void closeFragment() {
        if (iOnBackPressed != null) {
            iOnBackPressed.onBackPressedFromFragment();
        }
    }
}