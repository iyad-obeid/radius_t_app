package com.ficat.sample;

import android.annotation.SuppressLint;
import android.os.AsyncTask;

import com.ficat.easyble.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class NetworkOperation extends AsyncTask<String, Void, String> {
    @SuppressLint("NewApi")
    @Override
    protected String doInBackground(String... params) {
        String https_url = "https://register.bpchildresearch.org/wstg/tempsensor/temp";
        //HFKjzEZjHnea

        String token = "NcdaTXkbTDTh";
        String data = Arrays.toString(params);
        byte[] input = new byte[1];
        input = data.getBytes(StandardCharsets.UTF_8);
        URL url = null;

        try {
            url = new URL(https_url);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection)url.openConnection();
        } catch (IOException e) {
            e.printStackTrace();
        }
        //
        try {
            con.setRequestMethod("POST");
        } catch (ProtocolException e) {
            e.printStackTrace();
        }
        con.setDoOutput(true);
            con.setRequestProperty("Accept", "application/json");
            con.setRequestProperty("Authorization", "Bearer "+ token);

            con.setRequestProperty("Content-Type", "application/json");

        OutputStream os = null;
        try {
            con.connect();
            os = con.getOutputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            os.write(input);
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            os.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            Logger.e(con.getResponseCode()+" "+con.getResponseMessage());
        } catch (IOException e) {
            e.printStackTrace();
        }
        con.disconnect();
        Logger.e(data);


        return https_url;
    }

    @Override
    protected void onPostExecute(String result) {

    }

}