/*
 * Copyright (C) 2016 mendhak
 *
 * This file is part of GPSLogger for Android.
 *
 * GPSLogger for Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * GPSLogger for Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with GPSLogger for Android.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.mendhak.gpslogger.senders.customurl;


import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.net.Uri;
import android.support.v4.util.Pair;

import com.birbit.android.jobqueue.Job;
import com.birbit.android.jobqueue.Params;
import com.birbit.android.jobqueue.RetryConstraint;
import com.mendhak.gpslogger.common.AppSettings;
import com.mendhak.gpslogger.common.network.Networks;
import com.mendhak.gpslogger.common.Strings;
import com.mendhak.gpslogger.common.events.UploadEvents;
import com.mendhak.gpslogger.common.slf4j.Logs;
import com.mendhak.gpslogger.loggers.Files;

import de.greenrobot.event.EventBus;
import okhttp3.*;

import java.io.File;
import org.slf4j.Logger;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.FileNotFoundException;
import java.util.List;

public class CustomUrlUploadJob extends Job {

    private static final Logger LOG = Logs.of(CustomUrlUploadJob.class);
    private UploadEvents.BaseUploadEvent callbackEvent;

    private static String customLoggingUrl;
    private static String basicAuthUsername;
    private static String basicAuthPassword;
    List<File> files;
    private HashMap<String, String> httpHeaders = new HashMap<String, String>();


    public CustomUrlUploadJob(String customLoggingUrl,
                           String httpHeaders, String basicAuthUsername, String basicAuthPassword, List<File> files, UploadEvents.BaseUploadEvent callbackEvent) {
        super(new Params(1).requireNetwork().persist());

        this.customLoggingUrl = customLoggingUrl;
        this.basicAuthUsername = basicAuthUsername;
        this.basicAuthPassword = basicAuthPassword;
        this.files = files;
        this.callbackEvent = callbackEvent;
        Pair<String, String> urlCredentials = getBasicAuthCredentialsFromUrl(this.customLoggingUrl);
        addAuthorizationHeader(urlCredentials);
        removeCredentialsFromUrl(urlCredentials);

        addAuthorizationHeader(new Pair<String, String>(basicAuthUsername, basicAuthPassword));       
        
        this.httpHeaders.putAll(getHeadersFromTextBlock(httpHeaders));
    }

    @Override
    public void onAdded() {
    }

    @Override
    public void onRun() throws Throwable {

        LOG.info("HTTP Request - " + customLoggingUrl);

        OkHttpClient.Builder okBuilder = new OkHttpClient.Builder();
        okBuilder.sslSocketFactory(Networks.getSocketFactory(AppSettings.getInstance()));
        Request.Builder requestBuilder = new Request.Builder().url(customLoggingUrl);

        okhttp3.MultipartBody.Builder multipartBodyBuilder = new okhttp3.MultipartBody.Builder();
        for(Map.Entry<String,String> header : this.httpHeaders.entrySet()){
            requestBuilder.addHeader(header.getKey(), header.getValue());
        }
        int i=0;
        for (File f : files) {
            //File myFile = new File(f);

            try {
                multipartBodyBuilder.addFormDataPart(
                        "File-"+Integer.toString(i),
                        f.getName(),
                        okhttp3.RequestBody.create(MediaType.parse(Files.getMimeType(f.getName())), f)
                );
            }catch(Exception e) {}
        
        }         
        requestBuilder.post(multipartBodyBuilder.build());


        Request request = requestBuilder.build();
        Response response = okBuilder.build().newCall(request).execute();

        if (response.isSuccessful()) {
            LOG.debug("HTTP request complete with successful response code " + response);
            EventBus.getDefault().post(callbackEvent.succeeded());
        }
        else {
            LOG.error("HTTP request complete with unexpected response code " + response );
            EventBus.getDefault().post(callbackEvent.failed("Unexpected code " + response,new Throwable(response.body().string())));
        }

        response.body().close();
    }

    @Override
    protected void onCancel(int cancelReason, @Nullable Throwable throwable) {
        EventBus.getDefault().post(callbackEvent.failed("Could not send to custom URL", throwable));
        LOG.error("Custom URL: maximum attempts failed, giving up", throwable);
    }

    @Override
    protected RetryConstraint shouldReRunOnThrowable(@NonNull Throwable throwable, int runCount, int maxRunCount) {
        LOG.warn(String.format("Custom URL: attempt %d failed, maximum %d attempts", runCount, maxRunCount));
        return RetryConstraint.createExponentialBackoff(runCount, 5000);
    }


    @Override
    protected int getRetryLimit() {
        return 5;
    }

    
    private void addAuthorizationHeader(Pair<String, String> creds) {

        if(!Strings.isNullOrEmpty(creds.first) && !Strings.isNullOrEmpty(creds.second)){
            String credential = okhttp3.Credentials.basic(creds.first, creds.second);
            this.httpHeaders.put("Authorization", credential);
        }

    }
    
    private void removeCredentialsFromUrl(Pair<String, String> creds) {
        this.customLoggingUrl = this.customLoggingUrl.replace(creds.first + ":" + creds.second + "@","");
    }
    
    
    private Pair<String, String> getBasicAuthCredentialsFromUrl(String customLoggingUrl) {
        Pair<String, String> result  = new Pair<>("","");

        //Another possible match:  \/\/([^\/^:]+):(.+)@.+
        Pattern r = Pattern.compile("\\/\\/(.+):(\\w+)@.+");
        Matcher m = r.matcher(customLoggingUrl);
        while(m.find()){
            result = new Pair<>(m.group(1), m.group(2));
        }

        return result;

    }

    
    private Map<String,String> getHeadersFromTextBlock(String rawHeaders) {

        HashMap<String, String> map = new HashMap<>();
        String[] lines = rawHeaders.split("\\r?\\n");
        for (String line : lines){
            if(!Strings.isNullOrEmpty(line) && line.contains(":")){
                String[] lineParts = line.split(":");
                if(lineParts.length == 2){
                    String lineKey = line.split(":")[0].trim();
                    String lineValue = line.split(":")[1].trim();

                    if(!Strings.isNullOrEmpty(lineKey) && !Strings.isNullOrEmpty(lineValue)){
                        map.put(lineKey, lineValue);
                    }
                }

            }
        }

        return map;

    }
    
}
