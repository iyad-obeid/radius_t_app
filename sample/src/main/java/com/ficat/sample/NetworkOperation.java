package com.ficat.sample;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.ficat.easyble.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class NetworkOperation extends AsyncTask<String, Void, String> {

    String token;

    @SuppressLint("NewApi")
    @Override
    protected String doInBackground(String... params) {
        String https_url = "https://register.bpchildresearch.org/wstg/tempsensor/temp";
        //HFKjzEZjHnea
        //String token = "NcdaTXkbTDTh";
        String token = params[1];
        String data = "["+params[0]+"]";
        Logger.e(data);
        byte[] input = new byte[1];
        input = data.getBytes(StandardCharsets.UTF_8);
        URL url = null;

        try {
            url = new URL(https_url);

        HttpURLConnection con = null;

            con = (HttpURLConnection)url.openConnection();

            con.setRequestMethod("POST");

        con.setDoOutput(true);
            con.setRequestProperty("Accept", "application/json");
            con.setRequestProperty("Authorization", "Bearer "+ token);

            con.setRequestProperty("Content-Type", "application/json");

        OutputStream os = null;
            con.connect();
            os = con.getOutputStream();
            os.write(input);

            os.flush();

            Logger.e(con.getResponseCode()+" "+con.getResponseMessage());

        con.disconnect();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return https_url;
    }

    @Override
    protected void onPostExecute(String result) {

    }




}