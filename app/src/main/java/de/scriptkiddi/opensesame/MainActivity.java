package de.scriptkiddi.opensesame;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.NoConnectionError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import de.scriptkiddi.opensesame.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private boolean door_unlocked = false;
    private String url = "https://door.stuvus.uni-stuttgart.de/castle/lock";
    private RequestQueue queue;
    private Handler handler = new Handler();
    private  Runnable runnableCode = new Runnable() {
        @Override
        public void run() {
            getDoorState();
            handler.postDelayed(runnableCode, 500);
        }
    };

    private void toast(final int text_id) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(),
                        getApplicationContext().getString(text_id),
                        Toast.LENGTH_LONG).show();
            }
        });

    }

    private void doRequest(final int method, JSONObject request, Response.Listener<JSONObject> response_listener) {
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest
                (method, url, request, response_listener,
                        new Response.ErrorListener() {
                            @Override
                            public void onErrorResponse(VolleyError error) {
                                Log.e("Volly Error", error.toString());
                                if (error instanceof TimeoutError) {
                                    toast(R.string.error_network_timeout);
                                } else if (error instanceof NoConnectionError) {
                                    toast(R.string.error_no_response);
                                } else if (error instanceof AuthFailureError) {
                                    toast(R.string.error_auth_failure);
                                } else {
                                    toast(R.string.error_unknown);
                                }
                                NetworkResponse networkResponse = error.networkResponse;
                                if (networkResponse != null) {
                                    Log.e("Status code", String.valueOf(networkResponse.statusCode));
                                }
                            }
                        }
                ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                // add headers <key,value>
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                String username = prefs.getString("username", "");
                String password = prefs.getString("password", "");
                String credentials = username + ":" + password;
                String auth = "Basic "
                        + Base64.encodeToString(credentials.getBytes(),
                        Base64.NO_WRAP);
                headers.put("Authorization", auth);
                return headers;
            }

            @Override
            protected Response<JSONObject> parseNetworkResponse(NetworkResponse response) {
                if (response.data.length == 0) {
                    byte[] responseData = "{}".getBytes();
                    response = new NetworkResponse(response.statusCode, responseData, response.notModified, response.networkTimeMs, response.allHeaders);
                }

                return super.parseNetworkResponse(response);
            }
        };
        queue.add(jsonObjectRequest);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(runnableCode);
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(runnableCode);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        queue = Volley.newRequestQueue(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.menu_settings:
                startActivity(new Intent(getBaseContext(), SettingsActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }


    public void toggleDoor(View v) {
        if(!door_unlocked){
            controlDoor(true);
        }else{
            controlDoor(false);
        }
    }

    private void controlDoor(boolean unlock) {
        JSONObject json = new JSONObject();
        try {
            if (unlock) {
                json.put("state", "Unlocked");
            } else {
                json.put("state", "Locked");
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        doRequest(Request.Method.PUT, json, null);
    }


    private void updateUi(boolean door_unlocked) {
        Button door_button = findViewById(R.id.door_button);
        ImageView icon = findViewById(R.id.nili_icon);
        if (door_unlocked) {
            door_button.setText(R.string.close_door);
            door_button.setTextColor(Color.parseColor("#000000"));
            door_button.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.colorPrimary)));
            icon.setImageDrawable(getDrawable(R.drawable.stuvus_icon_open));
        } else {
            door_button.setText(R.string.open_door);
            door_button.setTextColor(Color.parseColor("#FFFFFF"));
            icon.setImageDrawable(getDrawable(R.drawable.stuvus_icon_locked));
            door_button.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.colorPrimaryDark)));
        }

    }

    private void getDoorState() {
        doRequest(Request.Method.GET, null, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
                Log.v("Main", response.toString());
                boolean tmp_door_unlocked;
                try {
                    if (response.getString("state").equals("Locked")) {
                        tmp_door_unlocked = false;
                    } else {
                        tmp_door_unlocked = true;
                    }
                    if (tmp_door_unlocked != door_unlocked) {
                        door_unlocked = tmp_door_unlocked;
                        updateUi(door_unlocked);
                    }
                } catch (JSONException e) {
                    toast(R.string.error_response_invalid);
                    e.printStackTrace();
                }
            }
        });
    }
}
