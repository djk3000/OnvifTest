package com.example.onviftest.utiliy;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.List;

public class PermissionCheckUtil {
    private static final String TAG = "PermissionCheckUtil";
    private String[] mPermissions;
    private int mRequestCode;
    private Object object;
    private OnPermissionCheckListener mCheckListener;

    private PermissionCheckUtil(Object object) {
        this.object = object;
    }
    public static PermissionCheckUtil with(Activity activity) {
        return new PermissionCheckUtil(activity);
    }

    public static PermissionCheckUtil with(Fragment fragment) {
        return new PermissionCheckUtil(fragment);
    }

    public PermissionCheckUtil permissions(String... permissions){
        this.mPermissions = permissions;
        return this;
    }
    public void check(int requestCode, OnPermissionCheckListener listener) {
        mRequestCode = requestCode;
        mCheckListener = listener;
        if (mPermissions != null && mPermissions.length > 0) {
            requestPermissions();
        } else {
            notifyPermissionGranted(mRequestCode);
        }
    }

    /**
     * 判断是否已经获取权限
     */

    @RequiresApi(api = Build.VERSION_CODES.M)
    public static boolean isPermissionGranted(Context context, String permission) {
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions(){
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            notifyPermissionGranted(mRequestCode);
            return;
        }
        Context context = object instanceof Fragment?((Fragment) object).getActivity(): (Context) object;
        List<String> requestList = getDeniedPermissions(context,mPermissions);
        if (requestList == null || requestList.size() == 0) {
            notifyPermissionGranted(mRequestCode);
        } else {
            if (object instanceof Activity) {
                ((Activity)object).requestPermissions(requestList.toArray(new String[0]), mRequestCode);
            } else if (object instanceof Fragment) {
                ((Fragment)object).requestPermissions(requestList.toArray(new String[0]), mRequestCode);
            }
        }
    }

    /**
     * 获取用户未允许的权限
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    private static List<String> getDeniedPermissions(Context context, String[] permissions) {
        if (permissions == null || permissions.length ==0) {
            return null;
        }
        List requestList = new ArrayList<String>();
        for (final String permission : permissions) {
            if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                requestList.add(permission);
            }
        }
        return requestList;
    }

    /**
     * 获取用户勾选不再询问的权限
     */
    private static List<String> getNeverAskedPermissions(Object context,String[] permissions) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                permissions == null || permissions.length ==0) {
            return null;
        }
        List requestList = new ArrayList<String>();
        for (final String permission : permissions) {
            if (context instanceof Activity) {
                if (!((Activity) context).shouldShowRequestPermissionRationale(permission)) {
                    requestList.add(permission);
                }
            } else if (context instanceof Fragment) {
                if (!((Fragment) context).shouldShowRequestPermissionRationale(permission)) {
                    requestList.add(permission);
                }
            }
        }
        return requestList;
    }

    /**
     * App授权结果回调，需要在{@link Activity#onRequestPermissionsResult(int, String[], int[])}或
     * {@link Fragment#onRequestPermissionsResult(int, String[], int[])}中调用
     */
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        List<String> deniedList = new ArrayList();
        for (int i = 0; i < grantResults.length; i++) {
            if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                deniedList.add(permissions[i]);
            }
        }
        if (deniedList.size() > 0) {
            notifyPermissionDenied(requestCode,deniedList.toArray(new String[0]));
            //获取被用户拒绝且勾选了不再询问的权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                List<String> neverAskList = getNeverAskedPermissions(object,deniedList.toArray(new String[0]));
                if (neverAskList != null && neverAskList.size() > 0) {
                    notifyNeverAsked(requestCode,neverAskList.toArray(new String[0]));
                }
            }
        } else {
            notifyPermissionGranted(requestCode);
        }
    }

    private void notifyPermissionGranted(int requestCode) {
        if (mCheckListener != null) {
            mCheckListener.onPermissionGranted(requestCode);
        }
    }

    private void notifyPermissionDenied(int requestCode,String[] permissions) {
        if (mCheckListener != null) {
            mCheckListener.onPermissionDenied(requestCode,permissions);
        }
    }

    private void notifyNeverAsked(int requestCode,String[] mPermissions) {
        if (mCheckListener != null) {
            mCheckListener.onNeverAsked(requestCode,mPermissions);
        }
    }

    public interface OnPermissionCheckListener {
        public void onPermissionGranted(int requestCode);
        public void onPermissionDenied(int requestCode,String[] deniedPermissions);
        public void onNeverAsked(int requestCode,String[] deniedPermissions);
    }
}
