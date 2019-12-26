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

import android.location.Location;

import com.mendhak.gpslogger.common.AppSettings;
import com.mendhak.gpslogger.common.events.UploadEvents;
import com.mendhak.gpslogger.senders.FileSender;
import com.birbit.android.jobqueue.CancelResult;
import com.birbit.android.jobqueue.JobManager;
import com.birbit.android.jobqueue.TagConstraint;
import com.mendhak.gpslogger.common.PreferenceHelper;
import com.mendhak.gpslogger.common.Strings;
import com.mendhak.gpslogger.senders.FileSender;
import com.mendhak.gpslogger.common.slf4j.Logs;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.io.FileNotFoundException;
import org.slf4j.Logger;

public class CustomUrlSender extends FileSender {
    private static String name = "URL";
    private static String customLoggingUrl;
    private static String httpHeaders;
    private static String basicAuthUsername;
    private static String basicAuthPassword;

    private static PreferenceHelper preferenceHelper;
    private static final Logger LOG = Logs.of(CustomUrlSender.class);

    public CustomUrlSender(PreferenceHelper helper) {
        this.preferenceHelper = helper;
    }

    @Override
    public void uploadFile(List<File> files) {


        final ArrayList<File> filesToSend = new ArrayList<>();
        this.customLoggingUrl = this.preferenceHelper.getAutoSendCustomURLPath();
        this.httpHeaders = this.preferenceHelper.getAutoSendCustomURLHeaders();
        this.basicAuthUsername = this.preferenceHelper.getAutoSendCustomURLUsername();
        this.basicAuthPassword = this.preferenceHelper.getAutoSendCustomURLPassword();


        final JobManager jobManager = AppSettings.getJobManager();

        jobManager.addJobInBackground( new CustomUrlUploadJob(customLoggingUrl,
                            httpHeaders,  basicAuthUsername,  basicAuthPassword,  files, new UploadEvents.AutoCustomUrl()));


    }

    @Override
    public boolean isAvailable() {

        return isValid( preferenceHelper.getAutoSendCustomURLPath());
    }

    @Override
    public boolean hasUserAllowedAutoSending() {
        return preferenceHelper.isCustomURLAutoSendEnabled();
    }



    @Override
    public boolean accept(File dir, String name) {
        return true;
    }

    public boolean isValid(String url) {
                return !Strings.isNullOrEmpty(url) ;

    }
}


