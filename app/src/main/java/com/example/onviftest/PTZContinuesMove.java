package com.example.onviftest;

import be.teletask.onvif.listeners.OnvifResponseListener;
import be.teletask.onvif.models.OnvifMediaProfile;
import be.teletask.onvif.models.OnvifType;
import be.teletask.onvif.requests.AbsoluteMoveRequest;
import be.teletask.onvif.requests.OnvifRequest;

public class PTZContinuesMove implements OnvifRequest {

    public static final String TAG = AbsoluteMoveRequest.class.getSimpleName();

    //Attributes
    private OnvifResponseListener listener;
    private OnvifMediaProfile mediaProfile;
    private double pan = 0, tilt = 0, zoom = 0;

    public PTZContinuesMove(OnvifMediaProfile mediaProfile, double pan, double tilt, double zoom) {
        this.mediaProfile = mediaProfile;
        this.pan = pan;
        this.tilt = tilt;
        this.zoom = zoom;
    }

    @Override
    public String getXml() {
        return "<ContinuousMove xmlns=\"http://www.onvif.org/ver20/ptz/wsdl\">" +
                "<ProfileToken>" + mediaProfile.getToken() + "</ProfileToken>" +
                "<Velocity>" +
                // PanTilt (optional)
                "<PanTilt x=\"" + pan + "\" y=\"" + tilt + "\" xmlns=\"http://www.onvif.org/ver10/schema\" />" +

                // Zoom (optional)
                "<Zoom x=\"" + zoom + "\" xmlns=\"http://www.onvif.org/ver10/schema\" />" +
                "</Velocity>" +
//                // Timeout (optional)
//                "<Timeout>PT10S</Timeout>" +
                "</ContinuousMove>";
    }

    @Override
    public OnvifType getType() {
        return OnvifType.CUSTOM;
    }
}
