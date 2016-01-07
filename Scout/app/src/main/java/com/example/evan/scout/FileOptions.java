package com.example.evan.scout;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;


public class FileOptions extends AppCompatActivity {
    private static final String uuid = "f8212682-9a34-11e5-8994-feff819cdc9f";
    private static final String superName = "red super";
    private String name;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_options);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        TextView textView = (TextView) findViewById(R.id.fileTitle);
        name = getIntent().getStringExtra("matchName");
        if (name != null) {
            textView.setText(name);
        } else {
            Log.e("File Error", "Failed To Open File");
            Toast.makeText(this, "Failed To Open File", Toast.LENGTH_LONG).show();
        }
    }



    //'back' button on ui
    public void backToViewer(View view) {
        startActivity(new Intent(this, MainActivity.class));
    }



    //'delete' button on ui
    public void deleteFile(View view) {
        File file = new File(android.os.Environment.getExternalStorageDirectory().getAbsolutePath() + "/Android/MatchData/" + name);
        if (!file.delete()) {
            Log.e("File Error", "Failed To Delete File");
            Toast.makeText(this, "Failed To Delete File", Toast.LENGTH_LONG).show();
            return;
        }
        (findViewById(R.id.backButton2)).performClick();
    }



    //'resend' button on ui
    public void resendFile(View view) {
        BufferedReader file;
        try {
            file = new BufferedReader(new InputStreamReader(new FileInputStream(
                    new File(android.os.Environment.getExternalStorageDirectory().getAbsolutePath() + "/Android/MatchData/" + name))));
        } catch (IOException ioe) {
            Log.e("File Error", "Failed To Open File");
            Toast.makeText(this, "Failed To Open File", Toast.LENGTH_LONG).show();
            return;
        }
        String text = "";
        String buf;
        try {
            while ((buf = file.readLine()) != null) {
                text = text.concat(buf + "\n");
            }
        } catch (IOException ioe) {
            Log.e("File Error", "Failed To Read From File");
            Toast.makeText(this, "Failed To Read From File", Toast.LENGTH_LONG).show();
            return;
        }
        new ConnectThread(this, superName, uuid, name, text).start();
    }
}