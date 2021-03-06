package com.walmartlabs.classwork.rideone.activities;

import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.parse.FindCallback;
import com.parse.GetCallback;
import com.parse.ParseException;
import com.parse.ParseObject;
import com.parse.ParseQuery;
import com.parse.SaveCallback;
import com.walmartlabs.classwork.rideone.R;
import com.walmartlabs.classwork.rideone.adapters.PassengerListAdapter;
import com.walmartlabs.classwork.rideone.fragments.TimePickerFragment;
import com.walmartlabs.classwork.rideone.models.Ride;
import com.walmartlabs.classwork.rideone.models.User;
import com.walmartlabs.classwork.rideone.util.ParseUtil;
import com.walmartlabs.classwork.rideone.util.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static android.view.View.VISIBLE;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.walmartlabs.classwork.rideone.models.Ride.COLUMN_DRIVER;
import static com.walmartlabs.classwork.rideone.models.User.COLUMN_ID;
import static com.walmartlabs.classwork.rideone.models.User.COLUMN_RIDE;
import static com.walmartlabs.classwork.rideone.models.User.Status.DRIVER;
import static com.walmartlabs.classwork.rideone.models.User.Status.NO_RIDE;
import static com.walmartlabs.classwork.rideone.models.User.Status.PASSENGER;
import static com.walmartlabs.classwork.rideone.util.ParseUtil.ERR_RECORD_NOT_FOUND;
import static com.walmartlabs.classwork.rideone.util.Utils.getLocalHourAndMinute;
import static com.walmartlabs.classwork.rideone.util.Utils.parseHourAndMinute;

/**
 * Activity for managing the ride and the driver's status
 * * set driver available/unavailable - which translates to creating a an active Ride or deactivating a Ride
 * * set Time
 * * set Destination
 * * set Start Point
 * * set number of spots available
 * * list passengers pending/confirmed
 * * confirm/remove a passenger
 * <p/>
 * Created by dmaskev on 11/8/15.
 */
public class DriverStatusActivity extends AppCompatActivity implements TimePickerDialog.OnTimeSetListener {

    public static final int DEFAULT_SPOTS = 2;

    private EditText etStartTime;
    private Ride ride;
    private User driver;
    //    private ParseProxyObject proxyRide;
    private Switch swAvailable;
    private Spinner spDestination;
    private Spinner spStartLoc;
    private TextView tvDestination;
    private TextView tvStartLoc;
    private Spinner spSpots;
    private TextView tvSpotsLeft;
    private ArrayAdapter<User> aPassengers;
    private List<User> riders = new ArrayList<>();
    private List<User> removePassengers = new ArrayList<>();
    private TextView tvPassengers;
    private TextView tvStartTime;
    private boolean readOnly;

    private Function<User, String> extractIdFunction = new Function<User, String>() {
        @Override
        public String apply(User input) {
            return input.getObjectId();
        }
    };


    PassengerListAdapter.PassengerListListener passengerListListener = new PassengerListAdapter.PassengerListListener() {
        @Override
        public void onAccept(User user, PassengerListAdapter.ViewHolder vh) {
            int spotsLeft = ride.getSpotsLeft();

            if (spotsLeft > 0) {
                vh.ivAccept.setVisibility(View.INVISIBLE);
                vh.tvAccept.setVisibility(View.INVISIBLE);
                user.setStatus(PASSENGER);
                populateSpotsLeftTextView();
            }
        }

        @Override
        public void onRemove(User user, PassengerListAdapter.ViewHolder vh) {
//                //TODO: alert with confirmation
            aPassengers.remove(user);
            user.setStatus(NO_RIDE);
            removePassengers.add(user);
            populateSpotsLeftTextView();
            ride.setLastUpdatedBy();
        }

        @Override
        public void onPhoneCall(User user, PassengerListAdapter.ViewHolder vh) {
            Intent callIntent = new Intent(Intent.ACTION_CALL);
            callIntent.setData(Uri.parse("tel:" + user.getPhone()));
            Context context = DriverStatusActivity.this;
            if (Utils.checkCallPermission(context)) {
                context.startActivity(callIntent);
            } else {
                Toast.makeText(context, "Phone call is not permitted", Toast.LENGTH_LONG).show();
            }

        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_status);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        tvStartTime = (TextView) findViewById(R.id.tvStartTime);

//        Ride rideArg = ((Ride) getIntent().getSerializableExtra("ride"));
        User userArg = (User) getIntent().getSerializableExtra("user");
        readOnly = getIntent().getBooleanExtra("readonly", false);

        tvPassengers = (TextView) findViewById(R.id.tvPassengers);

//        if(rideArg != null) {
//            driver = userArg.rebuild();
//            ride = rideArg.rebuild();
//            setupRideInfo();
//        } else {
            driver = userArg.rebuild();
            getRideFromDb(driver);

//        }



    }

    private void getRideFromDb(final User driver) {
        ParseQuery<Ride> query = ParseQuery.getQuery(Ride.class);
        query.whereEqualTo(COLUMN_DRIVER, driver.getObjectId());
        query.getFirstInBackground(new GetCallback<Ride>() {
            public void done(Ride rideDb, ParseException e) {
                if (e != null && e.getCode() != ERR_RECORD_NOT_FOUND) {
                    Log.e(DriverStatusActivity.class.getSimpleName(), "Failed fetching rides for driver " + driver.getObjectId());
                    alert(R.string.alert_network_error);
                    return;
                }

                if (rideDb == null) {
                    ride = createDefaultRide(driver);
                    ride.saveInBackground(new SaveCallback() {
                        @Override
                        public void done(ParseException e) {
                            if (e != null) {
                                Log.e(DriverStatusActivity.class.getSimpleName(), "Failed to create new ride ");
                                alert(R.string.alert_network_error);
                                return;
                            }
                            setupRideInfo();
                        }
                    });
                } else {
                    ride = rideDb;
                    setupRideInfo();
                }
            }
        });
    }

    private Ride createDefaultRide(User driver) {
        Ride ride = new Ride();
        ride.setAvailable(true);
        ride.setDate(new Date());
        ride.setRiderIds(new ArrayList<String>());
        ride.setSpots(DEFAULT_SPOTS);
        ride.setSpotsLeft(DEFAULT_SPOTS);
        ride.setDriverId(driver.getObjectId());
        ride.setLastUpdatedBy();
        return ride;
    }

    private void setupRideInfo() {
        setupPassengerList();
        setupStartTimeWidget();
        setupSwitchWidget();
        setupSpinners();

        tvSpotsLeft = (TextView)findViewById(R.id.tvSpotsLeft);
        populateSpotsLeftTextView();

        tvDestination = (TextView)findViewById(R.id.tvDestination);
        tvStartLoc = (TextView)findViewById(R.id.tvStartLoc);

        RelativeLayout rlMain = (RelativeLayout) findViewById(R.id.rlMain);

        setEnabled(!readOnly, rlMain);
    }

    private void setEnabled(boolean enabled, ViewGroup vg) {
        for (int i = 0; i < vg.getChildCount(); i++){
            View child = vg.getChildAt(i);
            child.setEnabled(enabled);
            if (child instanceof ViewGroup){
                setEnabled(enabled, (ViewGroup) child);
            }
        }    }

    private void populateSpotsLeftTextView() {
        populateSpotsLeftTextView(calculateSpotsLeft());
    }
    private void populateSpotsLeftTextView(int spots) {
        tvSpotsLeft.setText(String.format("(%s left)", spots));
    }

    private int calculateSpotsLeft() {
        int totalSpots = Integer.parseInt(spSpots.getSelectedItem().toString());
        int spotsTaken = resolveAcceptedRiders(riders).size();
        return totalSpots - spotsTaken;
    }




    private void setupPassengerList() {
        riders.clear();
        removePassengers.clear();

        ListView lvPassengers = (ListView) findViewById(R.id.lvPassengers);
        aPassengers = new PassengerListAdapter(DriverStatusActivity.this, riders, passengerListListener);
        lvPassengers.setAdapter(aPassengers);

        if (ride.getRiderIds() != null && !ride.getRiderIds().isEmpty()) {
            tvPassengers.setVisibility(VISIBLE);
            List<String> passengerIds = ride.getRiderIds();
            ParseQuery.getQuery(User.class).whereContainedIn(COLUMN_ID, passengerIds).findInBackground(new FindCallback<User>() {
                @Override
                public void done(List<User> objects, ParseException e) {
                    aPassengers.addAll(objects);
                    populateSpotsLeftTextView();
                }
            });

        }
    }

    private void setupSpinners() {
        spDestination = (Spinner) findViewById(R.id.spDestination);
        populateSpinner(ride.getDestination(), spDestination);
        spStartLoc = (Spinner) findViewById(R.id.spStartLoc);
        populateSpinner(ride.getStartLocation(), spStartLoc);
        spSpots = (Spinner) findViewById(R.id.spSpots);
        int spots = ride.getSpots();
        if(spots == 0) {
            spots = 1;
        }
        populateSpinner(spots, spSpots);

        spDestination.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position != spStartLoc.getSelectedItemPosition()) {
                    clearErrors();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        spStartLoc.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if(position != spDestination.getSelectedItemPosition()) {
                    clearErrors();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        spSpots.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                populateSpotsLeftTextView();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                populateSpotsLeftTextView();
            }
        });

    }

    private void setupSwitchWidget() {
        swAvailable = (Switch) findViewById(R.id.swAvailable);
        swAvailable.setChecked(ride.isAvailable());
        swAvailable.setTextColor(tvStartTime.getTextColors());
    }

    private void setupStartTimeWidget() {
        final int[] hourAndMinute = getLocalHourAndMinute(ride.getDate());
        etStartTime = (EditText) findViewById(R.id.etStartTime);
        etStartTime.setText(Utils.formatDuration(hourAndMinute[0], hourAndMinute[1]));
        etStartTime.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (MotionEvent.ACTION_DOWN == event.getAction()) {
                    TimePickerFragment.createInstance(hourAndMinute[0], hourAndMinute[1]).show(getSupportFragmentManager(), "timePicker");
                }

                return true;
            }
        });
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_driver_status, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.mi_driver_status_save) {
            onSave(new SaveCallback() {
                @Override
                public void done(ParseException e) {
                    if (e != null) {
                        Log.e(ParseUtil.class.getSimpleName(), "Failed to save ride info ", e);
                        alert(R.string.alert_network_error);
                    }
                    Intent data = new Intent();
                    ride.flush();
                    data.putExtra("ride", ride);
                    setResult(Activity.RESULT_OK, data);
                    finish();
                }
            });
            return true;
        } else if (id == android.R.id.home) {
//            NavUtils.navigateUpFromSameTask(this);
            Intent upIntent = NavUtils.getParentActivityIntent(this);
            upIntent.putExtra("user", driver.flush());
            NavUtils.navigateUpTo(this, upIntent);
            return true;
        }


        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
        etStartTime.setText(Utils.formatDuration(hourOfDay, minute));
    }

    private void populateSpinner(Object value, Spinner spinner) {
        if (value != null && !isNullOrEmpty(value.toString())) {
            int colorPos = ((ArrayAdapter<String>) spinner.getAdapter()).getPosition(value.toString());
            spinner.setSelection(colorPos);
        }
    }

    public void onSave(final SaveCallback callback) {
        populateRideInfo(ride, driver);
        if(!validate()) {
            return;
        }

        if (!isNullOrEmpty(ride.getObjectId())) {
            assignPassengersAndDriver(riders, removePassengers, ride, driver);
            //Combine riders, removePassengers and ride into one list
            List<ParseObject> models = Utils.joinModelLists(riders, removePassengers, Arrays.asList(ride), Arrays.asList(driver));
            ParseUtil.saveInBatch(models, callback);
        } else {
            Log.e(DriverStatusActivity.class.getSimpleName(), "IllegalState: ride doesn't exist", new IllegalStateException("Ride doesn't exist."));
            Toast.makeText(this, "Internal Error", Toast.LENGTH_LONG).show();
        }
    }

    private boolean validate() {
        clearErrors();

        if(ride.isAvailable() && ride.getStartLocation().equalsIgnoreCase(ride.getDestination())) {
            String error = "Start location and destination cannot be the same";
            Toast toast = Toast.makeText(DriverStatusActivity.this, error, Toast.LENGTH_LONG);
            toast.setGravity(Gravity.CENTER, 0, 20);
            toast.show();
//            TextView spinner = (TextView) spDestination.getSelectedView();
//            spinner.setError(error);
            tvDestination.setTextColor(Color.RED);
            tvStartLoc.setTextColor(Color.RED);
//            spinner.setText(error);
//            tvStartLoc.setError(error);
//            tvDestination.setError(error);
//            spDestination.requestFocus();
            return false;
        }

        return true;
    }

    private void populateRideInfo(Ride ride, User driver) {
        ride.setAvailable(swAvailable.isChecked());
        ride.setStartLocation(spStartLoc.getSelectedItem().toString());
        ride.setDestination(spDestination.getSelectedItem().toString());
        ride.setDate(parseHourAndMinute(etStartTime.getText().toString()));

        if(ride.isAvailable()) {
            driver.setStatus(DRIVER);
        } else if(driver.getStatus() == DRIVER) {
            driver.setStatus(NO_RIDE);
        }

        ride.setLastUpdatedBy();
        ride.setSpots(Integer.parseInt(spSpots.getSelectedItem().toString()));
        ride.setSpotsLeft(calculateSpotsLeft());

    }

    private void alert(int msgResource) {
        Toast.makeText(DriverStatusActivity.this, msgResource, Toast.LENGTH_LONG).show();
    }

    private void assignPassengersAndDriver(List<User> passengers, List<User> removePassengers, Ride ride, User driver) {

        //Cannot add passengers in the same operation due to Parse limitations
        List<String> passengerIds = Lists.transform(passengers, extractIdFunction);
        if(!passengerIds.isEmpty()) {
            ride.setRiderIds(passengerIds);
        } else {
            List<String> emptyArray = Collections.emptyList();
            ride.setRiderIds(emptyArray);
        }

        for(User u : passengers) {
            u.setRideId(ride.getObjectId());
        }

        for(User u : removePassengers) {
            u.remove(COLUMN_RIDE);
        }

        driver.setRideId(ride.getObjectId());
        ride.setDriverId(driver.getObjectId());
        ride.setLastUpdatedBy();
    }

    private List<User> resolveAcceptedRiders(List<User> riders) {
        return Utils.filterList(riders, new Predicate<User>() {
            @Override
            public boolean apply(User input) {
                return input.getStatus() == PASSENGER;
            }
        });
    }

    private void clearErrors() {
        // Reset errors.
        tvDestination.setTextColor(tvStartTime.getTextColors());
        tvStartLoc.setTextColor(tvStartTime.getTextColors());
    }


}
