package com.example.onviftest.ptz;

import be.teletask.onvif.listeners.OnvifResponseListener;
import be.teletask.onvif.models.OnvifMediaProfile;
import be.teletask.onvif.models.OnvifType;
import be.teletask.onvif.requests.AbsoluteMoveRequest;
import be.teletask.onvif.requests.OnvifRequest;

public class PTZAbsoluteMove implements OnvifRequest {
    public static final String TAG = AbsoluteMoveRequest.class.getSimpleName();

    //Attributes
    private OnvifResponseListener listener;
    private OnvifMediaProfile mediaProfile;
    private double pan = 0, tilt = 0, zoom = 0;

    public PTZAbsoluteMove(OnvifMediaProfile mediaProfile, double pan, double tilt, double zoom) {
        this.mediaProfile = mediaProfile;
        this.pan = pan;
        this.tilt = tilt;
        this.zoom = zoom;
    }

    @Override
    public String getXml() {
        return "<AbsoluteMove xmlns=\"http://www.onvif.org/ver20/ptz/wsdl\">" +
                "<ProfileToken>" + mediaProfile.getToken() + "</ProfileToken>" +
                "<Position> " +
                "<PanTilt x=\"" + pan + "\" y=\"" + tilt + "\" xmlns=\"http://www.onvif.org/ver10/schema\" />" +
                "<Zoom x=\"" + zoom + "\" xmlns=\"http://www.onvif.org/ver10/schema\" />" +
                "</Position>" +
                "<Speed> " +
                "<Zoom x=\"" + 1 + "\"/>" +
                "</Speed>" +
                "</AbsoluteMove>";
    }

    @Override
    public OnvifType getType() {
        return OnvifType.CUSTOM;
    }
}
