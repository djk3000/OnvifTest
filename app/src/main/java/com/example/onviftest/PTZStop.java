package com.example.onviftest;

import be.teletask.onvif.listeners.OnvifResponseListener;
import be.teletask.onvif.models.OnvifMediaProfile;
import be.teletask.onvif.models.OnvifType;
import be.teletask.onvif.requests.AbsoluteMoveRequest;
import be.teletask.onvif.requests.OnvifRequest;

public class PTZStop implements OnvifRequest {
    public static final String TAG = AbsoluteMoveRequest.class.getSimpleName();

    //Attributes
    private OnvifResponseListener listener;
    private OnvifMediaProfile mediaProfile;

    public PTZStop(OnvifMediaProfile mediaProfile) {
        this.mediaProfile = mediaProfile;
    }

    @Override
    public String getXml() {
        return "<Stop xmlns=\"http://www.onvif.org/ver20/ptz/wsdl\">" +
                "<ProfileToken>" + mediaProfile.getToken() + "</ProfileToken>" +
                "</Stop>";
    }

    @Override
    public OnvifType getType() {
        return OnvifType.CUSTOM;
    }
}
