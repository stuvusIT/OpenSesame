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
import android.widget.TextView;
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

    private enum State {
        UNKNOWN, LOCKED, UNLOCKED;
    }

    private State door_state = State.UNKNOWN;
    private String url = "https://door.stuvus.uni-stuttgart.de/castle/lock";
    private RequestQueue queue;
    private Handler handler = new Handler();
    private  Runnable runnableCode = new Runnable() {
        @Override
        public void run() {
            getDoorState();
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

    private void doRequest(final int method, JSONObject request, Response.Listener<JSONObject> response_listener, final Runnable error_listener) {
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
                                if(error_listener != null) {
                                    error_listener.run();
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


    public void buttonClicked(View v) {
        switch (door_state) {
            case LOCKED:
                controlDoor(true);
                break;
            case UNLOCKED:
                controlDoor(false);
                break;
            case UNKNOWN:
            default:
                handler.post(runnableCode);
                break;
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
        doRequest(Request.Method.PUT, json, null, null);
    }


    private void updateUi(State state) {
        Button door_button = findViewById(R.id.door_button);
        TextView state_text = findViewById(R.id.state_text);
        ImageView icon = findViewById(R.id.nili_icon);
        switch (state) {
            case LOCKED:
                door_button.setText(R.string.open_door);
                door_button.setTextColor(Color.parseColor("#FFFFFF"));
                state_text.setText(R.string.locked);
                state_text.setTextColor(Color.parseColor("#FFFFFF"));
                state_text.setBackgroundColor(getResources().getColor(R.color.colorPrimaryDark));
                icon.setImageDrawable(getDrawable(R.drawable.stuvus_icon_locked));
                door_button.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.colorPrimaryDark)));
                break;
            case UNLOCKED:
                door_button.setText(R.string.close_door);
                door_button.setTextColor(Color.parseColor("#000000"));
                state_text.setText(R.string.unlocked);
                state_text.setTextColor(Color.parseColor("#000000"));
                state_text.setBackgroundColor(getResources().getColor(R.color.colorPrimary));
                door_button.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.colorPrimary)));
                icon.setImageDrawable(getDrawable(R.drawable.stuvus_icon_open));
                break;
            case UNKNOWN:
            default:
                door_button.setText(R.string.reconnect);
                door_button.setTextColor(Color.parseColor("#FFFFFF"));
                state_text.setText(R.string.unknown);
                state_text.setTextColor(Color.parseColor("#FFFFFF"));
                state_text.setBackgroundColor(getResources().getColor(R.color.colorAccentGrey));
                icon.setImageDrawable(getDrawable(R.drawable.stuvus_icon_unknown));
                door_button.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.colorAccentGrey)));
                break;
        }
    }

    private void updateState(State state) {
        if (state != door_state) {
            door_state = state;
            updateUi(door_state);
        }
    }

    private void getDoorState() {
        doRequest(Request.Method.GET, null, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
                Log.v("Main", response.toString());
                try {
                    if (response.getString("state").equals("Locked")) {
                        updateState(State.LOCKED);
                        handler.postDelayed(runnableCode, 500);
                    } else {
                        updateState(State.UNLOCKED);
                        handler.postDelayed(runnableCode, 500);
                    }
                } catch (JSONException e) {
                    toast(R.string.error_response_invalid);
                    updateState(State.UNKNOWN);
                    e.printStackTrace();
                }
            }
        }, new Runnable() {
            @Override
            public void run() {
                updateState(State.UNKNOWN);
            }
        });
    }
}
