package com.example.evan.scout;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;


import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;


public class MainActivity extends AppCompatActivity {
    //uuid for bluetooth connection
    private static final String uuid = "f8212682-9a34-11e5-8994-feff819cdc9f";

    //paired device to connect to as super:
    private String superName;
    private static final String redSuperName = "red super";
    private static final String blueSuperName = "blue super";

    //current list of sent files
    private FileListAdapter fileListAdapter;

    //current match the scout is on
    private int matchNumber;

    //whether the automatic match progression is overridden or not
    private boolean overridden = false;

    //schedule of matches
    private ScheduleHandler schedule;

    //shared preferences to receive previous matchNumber, scoutNumber
    private SharedPreferences preferences;
    private static final String PREFERENCES_FILE = "com.example.evan.scout";

    //the id of the scout.  1-3 is red, 4-6 is blue
    private int scoutNumber;

    //we highlight the edittext that has the team number that this scout needs to scout, but if they change their id we need to reset it
    //this is the original background that was with the edittext
    private Drawable originalEditTextDrawable;

    //initials of scout scouting
    private String scoutName;

    //save a reference to this activity for subclasses
    private final MainActivity context = this;

    FirebaseDatabase database = FirebaseDatabase.getInstance();
    DatabaseReference myRef = database.getReference();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //lock screen horizontal
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        //see comment on this variable above
        originalEditTextDrawable = findViewById(R.id.teamNumber1Edit).getBackground();



        //get any values received from other activities
        preferences = getSharedPreferences(PREFERENCES_FILE, 0);
        overridden = getIntent().getBooleanExtra("overridden", false);
        matchNumber = getIntent().getIntExtra("matchNumber", -1);
        //if matchNumber was not passed from a previous activity, load it from hard disk
        if (matchNumber == -1) {
            matchNumber = preferences.getInt("matchNumber", 1);
            //otherwise, save it to hard disk
        } else {
            if (getIntent().getStringExtra("previousData") != null) {
                matchNumber++;
            }
            SharedPreferences.Editor editor = preferences.edit();
            editor.putInt("matchNumber", matchNumber);
            editor.commit();
        }
        //scout initials
        if(getIntent().getStringExtra("scoutName")!=null){
            scoutName = getIntent().getStringExtra("scoutName");
        }else{
            scoutName = "NO_NAME_ASSIGNED";
        }

        //set up schedule
        schedule = new ScheduleHandler(this);
        schedule.getScheduleFromDisk();
        //if we don't have the schedule, they must enter the team numbers and it must be overridden
        if (!schedule.hasSchedule()) {
            overridden = true;
        }

        scoutNumber = preferences.getInt("scoutNumber", -1);
        //if we don't have scout id, get it
        if (scoutNumber == -1) {
            setScoutNumber();
            //if we have it, change edittexts accordingly
        } else {
            highlightTeamNumberTexts();
        }

        myRef.child("currentScouts").child(scoutName).setValue(1);
        //testing
        //implement ui stuff
        //set the match number edittext's onclick to open a dialog.  We do this so the screen does not shrink and the user can see what he/she types
        final EditText matchNumberTextView = (EditText) findViewById(R.id.matchNumberText);
        matchNumberTextView.setText("Q" + Integer.toString(matchNumber));
        matchNumberTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //display dialog if overridden
                if (overridden) {
                    final EditText editText = new EditText(context);
                    editText.setInputType(InputType.TYPE_CLASS_NUMBER);
                    editText.setHint("Match Number");
                    new AlertDialog.Builder(context)
                            .setTitle("Set Match")
                            .setView(editText)
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Done", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    //when they click done, we get the matchnumber from what they put
                                    try {
                                        matchNumber = Integer.parseInt(editText.getText().toString());
                                    } catch (NumberFormatException nfe) {
                                        matchNumber = 1;
                                    }
                                    SharedPreferences.Editor editor = preferences.edit();
                                    editor.putInt("matchNumber", matchNumber);
                                    editor.commit();
                                    matchNumberTextView.setText("Q" + Integer.toString(matchNumber));
                                    updateTeamNumbers();
                                }
                            })
                            .show();
                }
            }
        });

        //text watcher for listview search bar
        final EditText searchBar = (EditText) findViewById(R.id.searchBar);
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String text = searchBar.getText().toString();
                fileListAdapter.updateListView();
                if (!text.equals("")) {
                    //get list of files starting with text
                    //pass them off to filter fileListAdapter
                    fileListAdapter.filterListView(text);
                }
            }
        });

        //set up list of files
        ListView fileList = (ListView) findViewById(R.id.infoList);
        fileListAdapter = new FileListAdapter(this, fileList, uuid, superName);





        //teleop activity will send data here so errors show up on this screen
        String matchData = getIntent().getStringExtra("previousData");
        //if we have data from teleop activity
        if (matchData != null) {
            //if savedInstanceState is not null, it means that the onCreate has already been called for this activity.  We don't want to resend data
            if (savedInstanceState != null) {
                return;
            }
            String sendData;
            try {
                LocalTeamInMatchData previousData = (LocalTeamInMatchData)Utils.deserializeClass(matchData, LocalTeamInMatchData.class);
                sendData = Utils.serializeClass(previousData.getFirebaseData());
            } catch (Exception e) {
                sendData = null;
            }
            Log.i("JSON before send", wrapJson(sendData));
            //we want to send data fit for firebase, but we save a different file that will retain more information for future editing
            ConnectThread.ConnectThreadData data = new ConnectThread.ConnectThreadData(
                    getIntent().getStringExtra("matchName") + "_" + new SimpleDateFormat("dd-H:mm", Locale.US).format(new Date()) + ".txt", matchData,
                    wrapJson(sendData));
            new ConnectThread(this, superName, uuid, data).start();
        }
    }


    public static String wrapJson(String json) {
        if (json == null) {return null;}
        try {
            TeamInMatchData data = (TeamInMatchData)Utils.deserializeClass(json, TeamInMatchData.class);
            JSONObject wrapper = new JSONObject();
            wrapper.put(data.teamNumber + "Q" + data.matchNumber, new JSONObject(json));
            return wrapper.toString();
        } catch (Exception e) {
            Log.i("JSON Error", "Failed to deserialize JSON to wrap");
            return null;
        }
    }




    //highlight the edittext with the team number of the team that this scout will be scouting
    private void highlightTeamNumberTexts() {
        TextView scoutTeamText1 = (TextView) this.findViewById(R.id.teamNumber1Edit);
        TextView scoutTeamText2 = (TextView) this.findViewById(R.id.teamNumber2Edit);
        TextView scoutTeamText3 = (TextView) this.findViewById(R.id.teamNumber3Edit);
        if (scoutNumber%3 == 1) {
            scoutTeamText1.setBackgroundColor(Color.parseColor("#64FF64"));
            scoutTeamText2.setBackground(originalEditTextDrawable);
            scoutTeamText3.setBackground(originalEditTextDrawable);
        } else if (scoutNumber%3 == 2) {
            scoutTeamText2.setBackgroundColor(Color.parseColor("#64FF64"));
            scoutTeamText1.setBackground(originalEditTextDrawable);
            scoutTeamText3.setBackground(originalEditTextDrawable);
        } else if (scoutNumber%3 == 0) {
            scoutTeamText3.setBackgroundColor(Color.parseColor("#64FF64"));
            scoutTeamText1.setBackground(originalEditTextDrawable);
            scoutTeamText2.setBackground(originalEditTextDrawable);
        }



        //change ui depending on color
        if (scoutNumber < 4) {
            //update paired device name
            superName = redSuperName;

            //change actionbar color
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                //red
                actionBar.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#C40000")));
            }
        } else {
            //update paired device name
            superName = blueSuperName;

            //change actionbar color
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                //blue
                actionBar.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#4169e1")));
            }
        }
        if (fileListAdapter != null) {
            fileListAdapter.setSuperName(superName);
        }
        updateTeamNumbers();
    }



    //fill in the edittexts with the team numbers found in the schedule
    public void updateTeamNumbers() {
        if (schedule.hasSchedule()) {
            EditText teamNumber1Edit = (EditText) findViewById(R.id.teamNumber1Edit);
            EditText teamNumber2Edit = (EditText) findViewById(R.id.teamNumber2Edit);
            EditText teamNumber3Edit = (EditText) findViewById(R.id.teamNumber3Edit);
            Log.i("Schedule before display", schedule.getSchedule().toString());
            try {
                if (scoutNumber < 4) {
                    JSONArray red = schedule.getSchedule().getJSONObject("redTeamNumbers").getJSONArray(Integer.toString(matchNumber));
                    teamNumber1Edit.setText(red.getString(0));
                    teamNumber2Edit.setText(red.getString(1));
                    teamNumber3Edit.setText(red.getString(2));
                } else {
                    JSONArray blue = schedule.getSchedule().getJSONObject("blueTeamNumbers").getJSONArray(Integer.toString(matchNumber));
                    teamNumber1Edit.setText(blue.getString(0));
                    teamNumber2Edit.setText(blue.getString(1));
                    teamNumber3Edit.setText(blue.getString(2));
                }
            } catch (JSONException jsone) {
                Log.e("JSON error", "Failed to read JSON");
                Toast.makeText(this, "Match Not Available", Toast.LENGTH_LONG).show();
                teamNumber1Edit.setText("");
                teamNumber2Edit.setText("");
                teamNumber3Edit.setText("");
            }
        }
    }



    //update actionbar at top of screen, either giving them the option to override or automate
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        if (overridden) {
            MenuItem item = menu.findItem(R.id.mainOverride);
            item.setTitle("Automate Schedule");
        }
        return true;
    }



    //onclicks for buttons on actionbar
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        //override button
        if (item.getItemId() == R.id.mainOverride) {
            if (overridden && (!schedule.hasSchedule())) {
                Toast.makeText(this, "Schedule not available. Please get schedule", Toast.LENGTH_LONG).show();
                return false;
            }
            overridden = !overridden;
            if (overridden) {
                item.setTitle("Automate Schedule");
            } else {
                item.setTitle("Override Schedule");
            }

            //set scout id button
        } else if (item.getItemId() == R.id.setScoutIDButton) {
            setScoutNumber();


        }else if (item.getItemId() == R.id.setScoutName) {
            setScoutName(null);


            //get schedule button
        } else if (item.getItemId() == R.id.scheduleButton) {
            Toast.makeText(this, "Requesting Schedule. Please Wait...", Toast.LENGTH_LONG).show();
            schedule.getScheduleFromSuper(superName, uuid);
        }
        return true;
    }



    //display dialog to set scout number
    private void setScoutNumber() {
        final EditText editText = new EditText(this);
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        if (scoutNumber == -1) {
            editText.setHint("Scout ID");
        } else {
            editText.setHint(Integer.toString(scoutNumber));
        }
        new AlertDialog.Builder(this)
                .setTitle("Set Scout ID")
                .setView(editText)
                .setPositiveButton("Done", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            String text = editText.getText().toString();
                            if (text.equals("")) {
                                if (scoutNumber == -1) {
                                    throw new NumberFormatException();
                                }
                            } else {
                                int tmpScoutNumber = Integer.parseInt(text);
                                if ((tmpScoutNumber < 1) || (tmpScoutNumber > 6)) {
                                    throw new NumberFormatException();
                                }
                                scoutNumber = tmpScoutNumber;
                            }
                        } catch (NumberFormatException nfe) {
                            setScoutNumber();
                        }
                        highlightTeamNumberTexts();
                        SharedPreferences.Editor editor = preferences.edit();
                        editor.putInt("scoutNumber", scoutNumber);
                        myRef.child("scouts").child("scout"+scoutNumber).child("mostRecentUser").setValue(scoutName);
                        myRef.child("scouts").child("scout"+scoutNumber).child("team").setValue(matchNumber);
                        editor.commit();
                    }
                })
                .show();
    }



    //onclick for edittexts containing team numbers
    //again, we display dialogs to prevent screen shrinking
    public void editTeamNumber(final View view) {
        if (overridden) {
            final EditText editText = new EditText(this);
            editText.setInputType(InputType.TYPE_CLASS_NUMBER);
            editText.setHint("Team Number");
            new AlertDialog.Builder(this)
                    .setTitle("Set Team Number")
                    .setView(editText)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Done", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            TextView textView = (TextView) view;
                            int teamNum;
                            try {
                                teamNum = Integer.parseInt(editText.getText().toString());
                            } catch (NumberFormatException nfe) {
                                return;
                            }
                            textView.setText(Integer.toString(teamNum));
                        }
                    })
                    .show();
        }
    }



    //scout button on ui
    public void startScoutButton (View view) {
        startScout(null, matchNumber, -1);
    }


    //onclick for 'resend all unsent' button
    public void resendAllUnsent(View view) {
        resendAll("UNSENT_");
    }



    //onclick for 'resend all' button
    public void resendAll(View view) {
        resendAll("");
    }


    private void resendAll(String filter) {
        List<String> fileNames = new ArrayList<>();
        List<String> dataToSend = new ArrayList<>();
        List<String> dataToSave = new ArrayList<>();
        for (int i = 0; i < fileListAdapter.getCount(); i++) {
            String name = fileListAdapter.getItem(i);
            if (name.contains(filter)) {
                String content = Utils.readFile(context, name);
                if (content != null) {
                    try {
                        JSONObject data = new JSONObject(content);
                        fileNames.add(name);
                        dataToSave.add(data.toString());
                    } catch (JSONException jsone) {
                        Log.e("File Error", "Not a valid JSON in resend all");
                        Toast.makeText(context, "Invalid format in file", Toast.LENGTH_LONG).show();
                    }
                }
            }
        }
        for (int i = 0; i < dataToSave.size(); i++) {
            String matchData = dataToSave.get(i);
            String sendData;
            try {
                LocalTeamInMatchData previousData = (LocalTeamInMatchData)Utils.deserializeClass(matchData, LocalTeamInMatchData.class);
                sendData = Utils.serializeClass(previousData.getFirebaseData());
            } catch (Exception e) {
                sendData = null;
            }
            dataToSend.add(sendData);
        }
        ConnectThread.ConnectThreadData data;
        try {
            data = new ConnectThread.ConnectThreadData(fileNames, dataToSave, dataToSend);
        } catch (IllegalArgumentException iae) {
            Log.i("File Error", "Error in File Data");
            Toast.makeText(this, "Error in File Data", Toast.LENGTH_LONG).show();
            return;
        }
        if (data.size() != 0) {
            new ConnectThread(context, superName, uuid, data).start();
        }
    }



    public void startScout(String editJSON, int matchNumber, int teamNumber) {
        //collect the team number
        if (teamNumber == -1) {
            try {
                if (scoutNumber % 3 == 1) {
                    TextView scoutTeamText = (TextView) findViewById(R.id.teamNumber1Edit);
                    teamNumber = Integer.parseInt(scoutTeamText.getText().toString());
                } else if (scoutNumber % 3 == 2) {
                    TextView scoutTeamText = (TextView) findViewById(R.id.teamNumber2Edit);
                    teamNumber = Integer.parseInt(scoutTeamText.getText().toString());
                } else if (scoutNumber % 3 == 0) {
                    TextView scoutTeamText = (TextView) findViewById(R.id.teamNumber3Edit);
                    teamNumber = Integer.parseInt(scoutTeamText.getText().toString());
                } else {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException nfe) {
                Toast.makeText(this, "Please enter valid team numbers", Toast.LENGTH_LONG).show();
                return;
            }
        }
        fileListAdapter.stopFileObserver();
        //TODO
        final Intent nextActivity = new Intent(context, AutoActivity.class)
                .putExtra("matchNumber", matchNumber).putExtra("overridden", overridden)
                .putExtra("teamNumber", teamNumber).putExtra("scoutName", scoutName).putExtra("scoutNumber", scoutNumber).putExtra("previousData", editJSON);
        setScoutName(new Runnable() {
            @Override
            public void run() {
                startActivity(nextActivity.putExtra("scoutName", scoutName));
            }
        });
    }



    //in order to redisplay the dialog to ask for scout initials, we start a new method, and recursively call the method if the input is wrong
    //on Finish is what to happen on click
    private void setScoutName(final Runnable onFinish) {
        final EditText editText = new EditText(this);
        editText.setHint(scoutName);
        new AlertDialog.Builder(this)
                .setTitle("Set Scout Name")
                .setMessage("First name only, no caps.")
                .setView(editText)
                .setPositiveButton("Done", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String tmpScoutName = editText.getText().toString();
                        if (tmpScoutName.equals("")) {
                            if (scoutName == null) {
                                setScoutName(onFinish);
                            } else {
                                if (onFinish != null) {
                                    myRef.child("currentScouts").child(scoutName).setValue(1);
                                    onFinish.run();
                                }
                            }
                        } else if ((tmpScoutName.length() < 1) || (tmpScoutName.contains("\n")) || (tmpScoutName.contains("."))) {
                            setScoutName(onFinish);
                        } else {
                            scoutName = tmpScoutName;
                            if (onFinish != null) {
                                onFinish.run();
                            }
                        }
                    }
                })
                .show();
    }
}
