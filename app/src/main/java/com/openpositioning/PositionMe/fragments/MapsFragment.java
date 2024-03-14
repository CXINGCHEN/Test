package com.openpositioning.PositionMe.fragments;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;
import androidx.preference.PreferenceManager;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.GroundOverlay;
import com.google.android.gms.maps.model.GroundOverlayOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.android.SphericalUtil;
import com.openpositioning.PositionMe.R;
import com.openpositioning.PositionMe.sensors.SensorFusion;
import com.openpositioning.PositionMe.sensors.SensorTypes;

import java.util.ArrayList;
import java.util.List;

public class MapsFragment extends Fragment {

    public static final String TAG = "MapsFragment";

    //Zoom of google maps
    private float zoom = 19f;

    //App settings
    private SharedPreferences settings;
    //Singleton class to collect all sensor data
    private SensorFusion sensorFusion;
    //Timer to end recording
    private CountDownTimer autoStop;
    //Fresh the data
    private Handler refreshDataHandler;
    //Position and Orientation
    private Marker marker;
    //Trajectory
    private Polyline polyline;
    //Indoor Map
    private GroundOverlay groundOverlay;
    //Use Google Service
    private GoogleMap googleMap;
    //GNSS/GPS position
    private List<LatLng> polyLinsPointList = new ArrayList<>();
    //Record Time
    private ProgressBar timeRemaining;

    //Library Location
    private final LatLng libraryNorthEast = new LatLng(55.923067, -3.174772);
    private final LatLng librarySouthWest = new LatLng(55.922778, -3.175189);
//    private final LatLng libraryNorthEast = new LatLng(55.943124, -3.213640);
//    private final LatLng librarySouthWest = new LatLng(55.942483, -3.214865);

    //Nucleus Location
    private final LatLng nucleusNorthEast = new LatLng(55.923321, -3.173802);
    private final LatLng nucleusSouthWest = new LatLng(55.922772, -3.174596);
//    private final LatLng nucleusNorthEast = new LatLng(55.943124, -3.213640);
//    private final LatLng nucleusSouthWest = new LatLng(55.942483, -3.214865);

    //Fleming Jeking Location
    private final LatLng fjkNorthEast = new LatLng(55.922865419430416, -3.171911036113354);
    private final LatLng fjkSouthWest = new LatLng(55.92208993351801, -3.172865902615692);

    //If-in-building status
    private boolean isInLibrary = false;
    private boolean isInNucleus = false;
    private boolean isInFjk = false;

    //Initial floor: Ground floor
    private int currentInLibraryFloor = 0;
    private int currentInNucleusFloor = 0;
    private int currentInFjkFloor = 0;

    private boolean userChangedFloor = false;

    private float[] lastLatitude = null;


    private OnMapReadyCallback callback = new OnMapReadyCallback() {

        @Override
        public void onMapReady(GoogleMap googleMap) {

            MapsFragment.this.googleMap = googleMap;
            googleMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
            //Default Map Type: satellite; Change map type using Button
            if (getView() != null) {
                RadioButton radioButtonNormal = getView().findViewById(R.id.rb_check_normal);
                RadioButton radioButtonSatellite = getView().findViewById(R.id.rb_check_satellite);
                radioButtonNormal.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (radioButtonSatellite.isChecked()) {
                        googleMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
                    } else {
                        googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                    }
                });
                radioButtonSatellite.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (radioButtonSatellite.isChecked()) {
                        googleMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
                    } else {
                        googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                    }
                });
                radioButtonSatellite.setChecked(true);
            }

            //Obtain the start position from the GPS data from the SensorFusion class
            float[] latitude = sensorFusion.getGNSSLatitude(false);
            //If not location found zoom the map out
            if (latitude[0] == 0 && latitude[1] == 0) {
                zoom = 1f;
            } else {
                zoom = 19f;
            }

            //Obtain GNSS position
            LatLng position = new LatLng(latitude[0], latitude[1]);
            //Prepare trajectory
            polyLinsPointList.add(position);
            //Orientation
            float rotation = (float) Math.toDegrees(sensorFusion.passOrientation());
            //Set Marker
            marker = googleMap.addMarker(new MarkerOptions().position(position).icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_action_navigation)).rotation(rotation));
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(position, zoom));
            //Draw trajectory
            PolylineOptions polylineOptions = new PolylineOptions().add(position, new LatLng(latitude[0], latitude[1]));
            polylineOptions.zIndex(999f);
            polyline = googleMap.addPolyline(polylineOptions);

            // start refreshing
            MapsFragment.this.initTimer();
            blinkingRecording();
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.sensorFusion = SensorFusion.getInstance();
        Context context = getActivity();
        this.settings = PreferenceManager.getDefaultSharedPreferences(context);
        this.refreshDataHandler = new Handler();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_maps, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(callback);
        }

        view.findViewById(R.id.image_up).setOnClickListener(v -> {
            up();  // Go up one floor button
        });
        view.findViewById(R.id.image_down).setOnClickListener(v -> {
            down();// Go down one floor button
        });

        view.findViewById(R.id.cancelButton).setOnClickListener(v -> {
            cancelRecord();
        });
        view.findViewById(R.id.stopButton).setOnClickListener(v -> {
            stopRecord();
        });


        view.findViewById(R.id.btn_lg).setOnClickListener(v -> {//LG only works in Nucleus
            userChangedFloor = true;
            if (isInLibrary) {
            } else if (isInNucleus) {
                addGroundOverlay(R.drawable.nucleuslg, nucleusSouthWest, nucleusNorthEast);
                checkFloorButtonStatus("LG");
            } else if (isInFjk) {
            }
        });
        view.findViewById(R.id.btn_g).setOnClickListener(v -> {
            userChangedFloor = true;
            if (isInLibrary) {
                addGroundOverlay(R.drawable.libraryg, librarySouthWest, libraryNorthEast);
            } else if (isInNucleus) {
                addGroundOverlay(R.drawable.nucleusg, nucleusSouthWest, nucleusNorthEast);
            } else if (isInFjk) {
                addGroundOverlay(R.drawable.fjkg, fjkSouthWest, fjkNorthEast);
            }
            checkFloorButtonStatus("G");
        });
        view.findViewById(R.id.btn_f1).setOnClickListener(v -> {
            userChangedFloor = true;
            if (isInLibrary) {
                addGroundOverlay(R.drawable.library1, librarySouthWest, libraryNorthEast);
            } else if (isInNucleus) {
                addGroundOverlay(R.drawable.nucleus1, nucleusSouthWest, nucleusNorthEast);
            } else if (isInFjk) {
                addGroundOverlay(R.drawable.fjk1, fjkSouthWest, fjkNorthEast);
            }
            checkFloorButtonStatus("F1");
        });
        view.findViewById(R.id.btn_f2).setOnClickListener(v -> {
            userChangedFloor = true;
            if (isInLibrary) {
                addGroundOverlay(R.drawable.library2, librarySouthWest, libraryNorthEast);
            } else if (isInNucleus) {
                addGroundOverlay(R.drawable.nucleus2, nucleusSouthWest, nucleusNorthEast);
            } else if (isInFjk) {
            }
            checkFloorButtonStatus("F2");
        });
        view.findViewById(R.id.btn_f3).setOnClickListener(v -> {
            userChangedFloor = true;
            if (isInLibrary) {
                addGroundOverlay(R.drawable.library3, librarySouthWest, libraryNorthEast);
            } else if (isInNucleus) {
                addGroundOverlay(R.drawable.nucleus3, nucleusSouthWest, nucleusNorthEast);
            } else if (isInFjk) {
            }
            checkFloorButtonStatus("F3");
        });

        this.timeRemaining = view.findViewById(R.id.timeRemainingBar);
    }

    private void stopRecord() {
        Log.d(TAG, "stopRecord() called");
        if (autoStop != null) autoStop.cancel();
        sensorFusion.stopRecording();
        NavDirections action = MapsFragmentDirections.actionMapsFragmentToCorrectionFragment();
        if (getView() != null) {
            Navigation.findNavController(getView()).navigate(action);
        }
    }

    private void cancelRecord() {
        Log.d(TAG, "cancelRecord() called");
        sensorFusion.stopRecording();
        NavDirections action = MapsFragmentDirections.actionMapsFragmentToHomeFragment();
        if (getView() != null) {
            Navigation.findNavController(getView()).navigate(action);
        }
        if (autoStop != null) autoStop.cancel();
    }

    private void up() {// Go up one floor for all 3 buildings
        Log.d(TAG, "up() called");
        if (isInLibrary) {
            userChangedFloor = true;
            switch (currentInLibraryFloor) {
                case 0:
                    currentInLibraryFloor++;
                    addGroundOverlay(R.drawable.library1, librarySouthWest, libraryNorthEast);
                    checkFloorButtonStatus("F1");
                    break;
                case 1:
                    currentInLibraryFloor++;
                    addGroundOverlay(R.drawable.library2, librarySouthWest, libraryNorthEast);
                    checkFloorButtonStatus("F2");
                    break;
                case 2:
                    currentInLibraryFloor++;
                    addGroundOverlay(R.drawable.library3, librarySouthWest, libraryNorthEast);
                    checkFloorButtonStatus("F3");
                    break;
                case 3:
                    break;
            }
        } else if (isInNucleus) {
            userChangedFloor = true;
            switch (currentInNucleusFloor) {
                case -1:
                    currentInNucleusFloor++;
                    addGroundOverlay(R.drawable.nucleusg, nucleusSouthWest, nucleusNorthEast);
                    checkFloorButtonStatus("G");
                    break;
                case 0:
                    currentInNucleusFloor++;
                    addGroundOverlay(R.drawable.nucleus1, nucleusSouthWest, nucleusNorthEast);
                    checkFloorButtonStatus("F1");
                    break;
                case 1:
                    currentInNucleusFloor++;
                    addGroundOverlay(R.drawable.nucleus2, nucleusSouthWest, nucleusNorthEast);
                    checkFloorButtonStatus("F2");
                    break;
                case 2:
                    currentInNucleusFloor++;
                    addGroundOverlay(R.drawable.nucleus3, nucleusSouthWest, nucleusNorthEast);
                    checkFloorButtonStatus("F3");
                    break;
                case 3:
                    break;
            }
        } else if (isInFjk) {
            userChangedFloor = true;
            switch (currentInFjkFloor) {
                case 0:
                    currentInFjkFloor++;
                    addGroundOverlay(R.drawable.fjk1, fjkSouthWest, fjkNorthEast);
                    checkFloorButtonStatus("F1");
                    break;
                case 1:
                    break;
            }
        }
    }

    private void down() {// Go down one floor for all 3 buildings
        Log.d(TAG, "down() called");
        if (isInLibrary) {
            userChangedFloor = true;
            switch (currentInLibraryFloor) {
                case 0:
                    break;
                case 1:
                    currentInLibraryFloor--;
                    addGroundOverlay(R.drawable.libraryg, librarySouthWest, libraryNorthEast);
                    checkFloorButtonStatus("G");
                    break;
                case 2:
                    currentInLibraryFloor--;
                    addGroundOverlay(R.drawable.library1, librarySouthWest, libraryNorthEast);
                    checkFloorButtonStatus("F1");
                    break;
                case 3:
                    currentInLibraryFloor--;
                    addGroundOverlay(R.drawable.library2, librarySouthWest, libraryNorthEast);
                    checkFloorButtonStatus("F2");
                    break;
            }
        } else if (isInNucleus) {
            userChangedFloor = true;
            switch (currentInNucleusFloor) {
                case -1:
                    break;
                case 0:
                    currentInNucleusFloor--;
                    addGroundOverlay(R.drawable.nucleuslg, nucleusSouthWest, nucleusNorthEast);
                    checkFloorButtonStatus("LG");
                    break;
                case 1:
                    currentInNucleusFloor--;
                    addGroundOverlay(R.drawable.nucleusg, nucleusSouthWest, nucleusNorthEast);
                    checkFloorButtonStatus("G");
                    break;
                case 2:
                    currentInNucleusFloor--;
                    addGroundOverlay(R.drawable.nucleus1, nucleusSouthWest, nucleusNorthEast);
                    checkFloorButtonStatus("F1");
                    break;
                case 3:
                    currentInNucleusFloor--;
                    addGroundOverlay(R.drawable.nucleus2, nucleusSouthWest, nucleusNorthEast);
                    checkFloorButtonStatus("F2");
                    break;
            }
        } else if (isInFjk) {
            userChangedFloor = true;
            switch (currentInFjkFloor) {
                case 0:
                    break;
                case 1:
                    currentInFjkFloor--;
                    addGroundOverlay(R.drawable.fjkg, fjkSouthWest, fjkNorthEast);
                    checkFloorButtonStatus("G");
                    break;
            }
        }
    }

    private void initTimer() {
        if (this.settings.getBoolean("split_trajectory", false)) {
            // If that time limit has been reached:
            long limit = this.settings.getInt("split_duration", 30) * 60000L;
            // Set progress bar
            this.timeRemaining.setMax((int) (limit / 1000));
            this.timeRemaining.setScaleY(3f);
            this.autoStop = new CountDownTimer(limit, 1000) {
                @Override
                public void onTick(long l) {
                    timeRemaining.incrementProgressBy(1);
                    updateSensorData();
                }
                @Override
                public void onFinish() {
                    sensorFusion.stopRecording();
                    NavDirections action = MapsFragmentDirections.actionMapsFragmentToCorrectionFragment();
                    if (getView() != null) {
                        Navigation.findNavController(getView()).navigate(action);
                    }
                }
            }.start();
        } else {
            // No time limit - use a repeating task to refresh UI.
            this.refreshDataHandler.post(refreshDataTask);
        }
    }

    private void updateSensorData() {

        //Initiate the first location with GNSS location
        if (lastLatitude == null) {
            lastLatitude = sensorFusion.getGNSSLatitude(false);
        } else {
            float elevation = sensorFusion.getElevation(); // barometer
            float accuracy = sensorFusion.getAccuracy();   // GNSS accuracy

            // Get new position
            float[] pdrValues = sensorFusion.getSensorValueMap().get(SensorTypes.PDR);

            //Use PDR value to update trajectory
            if (pdrValues != null && pdrValues[0] != 0 && pdrValues[1] != 0) {
                Log.i(TAG, "updateSensorData: pdrValues[0] = " + pdrValues[0]);
                Log.i(TAG, "updateSensorData: pdrValues[1] = " + pdrValues[1]);
                float[] newLatitude = offsetToGpsLatLng(lastLatitude[0], lastLatitude[1], pdrValues[0], pdrValues[1]);
                float[] gnssLatitude = sensorFusion.getGNSSLatitude(false);

                float distanceBetween = (float) SphericalUtil.computeDistanceBetween(new LatLng(newLatitude[0], newLatitude[1]), new LatLng(gnssLatitude[0], gnssLatitude[1]));

                // All sensor data
                if (getView() != null) {
                    TextView accuracyTextView = getView().findViewById(R.id.tvAccuracy);
                    accuracyTextView.setText("Accuracy: " + accuracy);

                    TextView elevationTextView = getView().findViewById(R.id.tvElevation);
                    elevationTextView.setText("Elevation: " + elevation);

                    TextView latTextView = getView().findViewById(R.id.tvLat);
                    latTextView.setText("Latitude: " + newLatitude[0]);

                    TextView longTextView = getView().findViewById(R.id.tvLong);
                    longTextView.setText("Longitude: " + newLatitude[1]);

                    TextView pdrTextView = getView().findViewById(R.id.tvPdr);
                    pdrTextView.setText("PdrPosition:" + getString(R.string.x, String.format("%.1f", pdrValues[0])) + "," + getString(R.string.y, String.format("%.1f", pdrValues[1])));

                    TextView offsetMeterTextView = getView().findViewById(R.id.tvOffsetMeter);
                    offsetMeterTextView.setText("Offset Meter: " + String.format("%.2f", distanceBetween));

                }

                if (newLatitude[0] == 0 && newLatitude[1] == 0) {
                    zoom = 1f;
                } else {
                    zoom = 19f;
                }

                Log.i(TAG, "updateSensorData: latitude[0] = " + newLatitude[0]);
                Log.i(TAG, "updateSensorData: latitude[1] = " + newLatitude[1]);
                Log.i(TAG, "updateSensorData: elevation = " + elevation);
                Log.i(TAG, "updateSensorData: passOrientation = " + sensorFusion.passOrientation());
                // new angle
                float rotation = (float) Math.toDegrees(sensorFusion.passOrientation());
                Log.i(TAG, "updateSensorData: rotation = " + rotation);
                // New location of marker
                LatLng position = new LatLng(newLatitude[0], newLatitude[1]);

                if (!polyLinsPointList.contains(position)) {
                    polyLinsPointList.add(position);
                }
                Log.i(TAG, "updateSensorData: Number of track points = " + polyLinsPointList.size());

                if (marker != null) {
                    marker.setPosition(position);
                    marker.setRotation(rotation);
                }
                // googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(position, zoom));
                // Update trajectory
                polyline.setPoints(polyLinsPointList);
                checkLocationIsInBuilding(newLatitude, elevation);


            }
        }
    }


    private void checkLocationIsInBuilding(float[] latitude, float elevation) {
        // in library or not
        if (latitude[0] >= librarySouthWest.latitude && latitude[0] <= libraryNorthEast.latitude && latitude[1] >= librarySouthWest.longitude && latitude[1] <= libraryNorthEast.longitude) {
            isInLibrary = true;
            isInNucleus = false;
            isInFjk = false;

            if (getView() != null) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    getView().findViewById(R.id.ll_up_down).setVisibility(View.VISIBLE);
                    getView().findViewById(R.id.btn_lg).setVisibility(View.GONE);
                    getView().findViewById(R.id.btn_g).setVisibility(View.VISIBLE);
                    getView().findViewById(R.id.btn_f1).setVisibility(View.VISIBLE);
                    getView().findViewById(R.id.btn_f2).setVisibility(View.VISIBLE);
                    getView().findViewById(R.id.btn_f3).setVisibility(View.VISIBLE);
                });
            }

            //If the user has not manually changed the floor, use the height of the sensor to calculate the floor
            if (!userChangedFloor) {
                if (elevation >= -1 && elevation <= 2.5) {
                    currentInLibraryFloor = 0;
                    addGroundOverlay(R.drawable.libraryg, librarySouthWest, libraryNorthEast);
                    checkFloorButtonStatus("G");
                } else if (elevation > 2.5 && elevation <= 6) {
                    currentInLibraryFloor = 1;
                    addGroundOverlay(R.drawable.library1, librarySouthWest, libraryNorthEast);
                    checkFloorButtonStatus("F1");
                } else if (elevation > 6 && elevation <= 10) {
                    currentInLibraryFloor = 2;
                    addGroundOverlay(R.drawable.library2, librarySouthWest, libraryNorthEast);
                    checkFloorButtonStatus("F2");
                } else {
                    currentInLibraryFloor = 3;
                    addGroundOverlay(R.drawable.library3, librarySouthWest, libraryNorthEast);
                    checkFloorButtonStatus("F3");
                }
            }


        } else if (latitude[0] >= nucleusSouthWest.latitude && latitude[0] <= nucleusNorthEast.latitude && latitude[1] >= nucleusSouthWest.longitude && latitude[1] <= nucleusNorthEast.longitude) {
            isInLibrary = false;
            isInNucleus = true;
            isInFjk = false;

            if (getView() != null) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    getView().findViewById(R.id.ll_up_down).setVisibility(View.VISIBLE);
                    getView().findViewById(R.id.btn_lg).setVisibility(View.VISIBLE);
                    getView().findViewById(R.id.btn_g).setVisibility(View.VISIBLE);
                    getView().findViewById(R.id.btn_f1).setVisibility(View.VISIBLE);
                    getView().findViewById(R.id.btn_f2).setVisibility(View.VISIBLE);
                    getView().findViewById(R.id.btn_f3).setVisibility(View.VISIBLE);
                });
            }

            //Auto change floor based on height/barometer
            if (!userChangedFloor) {
                if (elevation < -1) {
                    currentInNucleusFloor = -1;
                    addGroundOverlay(R.drawable.nucleuslg, nucleusSouthWest, nucleusNorthEast);
                    checkFloorButtonStatus("LG");
                } else if (elevation >= -1 && elevation <= 3) {
                    currentInNucleusFloor = 0;
                    addGroundOverlay(R.drawable.nucleusg, nucleusSouthWest, nucleusNorthEast);
                    checkFloorButtonStatus("G");
                } else if (elevation > 3 && elevation <= 8.5) {
                    currentInNucleusFloor = 1;
                    addGroundOverlay(R.drawable.nucleus1, nucleusSouthWest, nucleusNorthEast);
                    checkFloorButtonStatus("F1");
                } else if (elevation > 8.5 && elevation <= 14) {
                    currentInNucleusFloor = 2;
                    addGroundOverlay(R.drawable.nucleus2, nucleusSouthWest, nucleusNorthEast);
                    checkFloorButtonStatus("F2");
                } else if (elevation > 14) {
                    currentInNucleusFloor = 3;
                    addGroundOverlay(R.drawable.nucleus3, nucleusSouthWest, nucleusNorthEast);
                    checkFloorButtonStatus("F3");
                }
            }
        } else if (latitude[0] >= fjkSouthWest.latitude && latitude[0] <= fjkNorthEast.latitude && latitude[1] >= fjkSouthWest.longitude && latitude[1] <= fjkNorthEast.longitude) {
            isInLibrary = false;
            isInNucleus = false;
            isInFjk = true;

            if (getView() != null) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    getView().findViewById(R.id.ll_up_down).setVisibility(View.VISIBLE);
                    getView().findViewById(R.id.btn_lg).setVisibility(View.GONE);
                    getView().findViewById(R.id.btn_g).setVisibility(View.VISIBLE);
                    getView().findViewById(R.id.btn_f1).setVisibility(View.VISIBLE);
                    getView().findViewById(R.id.btn_f2).setVisibility(View.GONE);
                    getView().findViewById(R.id.btn_f3).setVisibility(View.GONE);
                });
            }

            if (!userChangedFloor) {
                if (elevation >= -1 && elevation <= 2.5) {
                    currentInFjkFloor = 0;
                    addGroundOverlay(R.drawable.fjkg, fjkSouthWest, fjkNorthEast);
                    checkFloorButtonStatus("G");
                } else if (elevation > 2.5 && elevation <= 6) {
                    currentInFjkFloor = 1;
                    addGroundOverlay(R.drawable.fjk1, fjkSouthWest, fjkNorthEast);
                    checkFloorButtonStatus("F1");
                }
            }


        } else {
            isInLibrary = false;
            isInNucleus = false;
            isInFjk = false;
            userChangedFloor = false;

            if (getView() != null) {
                new Handler(Looper.getMainLooper()).post(() -> getView().findViewById(R.id.ll_up_down).setVisibility(View.GONE));
            }

            if (groundOverlay != null) {
                groundOverlay.remove();
                groundOverlay = null;
            }
        }
    }

    private void addGroundOverlay(int drawableId, LatLng southWest, LatLng northEast) {
        if (groundOverlay == null) {
            // add library image
            GroundOverlayOptions groundOverlayOptions = new GroundOverlayOptions().image(BitmapDescriptorFactory.fromResource(drawableId)).positionFromBounds(new LatLngBounds(southWest, northEast));
            groundOverlay = googleMap.addGroundOverlay(groundOverlayOptions);
        } else {
            groundOverlay.setImage(BitmapDescriptorFactory.fromResource(drawableId));
        }
    }


    private final Runnable refreshDataTask = new Runnable() {
        @Override
        public void run() {
            updateSensorData();
            // Loop the task again to keep refreshing the data
            refreshDataHandler.postDelayed(refreshDataTask, 500);
        }
    };


    /**
     * Displays a blinking red dot to signify an ongoing recording.
     *
     * @see Animation for makin the red dot blink.
     */
    private void blinkingRecording() {
        //Initialise Image View
        if (getView() != null) {
            ImageView recIcon = getView().findViewById(R.id.redDot);
            //Configure blinking animation
            Animation blinking_rec = new AlphaAnimation(1, 0);
            blinking_rec.setDuration(800);
            blinking_rec.setInterpolator(new LinearInterpolator());
            blinking_rec.setRepeatCount(Animation.INFINITE);
            blinking_rec.setRepeatMode(Animation.REVERSE);
            recIcon.startAnimation(blinking_rec);
        }

    }


    private void checkFloorButtonStatus(String newFloor) {// set button colour

        if (getView() != null) {
            TextView textLG = getView().findViewById(R.id.btn_lg);
            TextView textG = getView().findViewById(R.id.btn_g);
            TextView textF1 = getView().findViewById(R.id.btn_f1);
            TextView textF2 = getView().findViewById(R.id.btn_f2);
            TextView textF3 = getView().findViewById(R.id.btn_f3);

            switch (newFloor) {
                case "LG":
                    textLG.setBackgroundColor(getResources().getColor(R.color.primaryBlue));
                    textLG.setTextColor(getResources().getColor(R.color.selectFloorText));

                    textG.setBackgroundResource(R.drawable.solid_color);
                    textG.setTextColor(getResources().getColor(R.color.unselectFloorText));

                    textF1.setBackgroundResource(R.drawable.solid_color);
                    textF1.setTextColor(getResources().getColor(R.color.unselectFloorText));

                    textF2.setBackgroundResource(R.drawable.solid_color);
                    textF2.setTextColor(getResources().getColor(R.color.unselectFloorText));

                    textF3.setBackgroundResource(R.drawable.solid_color);
                    textF3.setTextColor(getResources().getColor(R.color.unselectFloorText));
                    break;
                case "G":
                    textLG.setBackgroundResource(R.drawable.solid_color);
                    textLG.setTextColor(getResources().getColor(R.color.unselectFloorText));

                    textG.setBackgroundColor(getResources().getColor(R.color.primaryBlue));
                    textG.setTextColor(getResources().getColor(R.color.selectFloorText));

                    textF1.setBackgroundResource(R.drawable.solid_color);
                    textF1.setTextColor(getResources().getColor(R.color.unselectFloorText));

                    textF2.setBackgroundResource(R.drawable.solid_color);
                    textF2.setTextColor(getResources().getColor(R.color.unselectFloorText));

                    textF3.setBackgroundResource(R.drawable.solid_color);
                    textF3.setTextColor(getResources().getColor(R.color.unselectFloorText));
                    break;
                case "F1":
                    textLG.setBackgroundResource(R.drawable.solid_color);
                    textLG.setTextColor(getResources().getColor(R.color.unselectFloorText));

                    textG.setBackgroundResource(R.drawable.solid_color);
                    textG.setTextColor(getResources().getColor(R.color.unselectFloorText));

                    textF1.setBackgroundColor(getResources().getColor(R.color.primaryBlue));
                    textF1.setTextColor(getResources().getColor(R.color.selectFloorText));

                    textF2.setBackgroundResource(R.drawable.solid_color);
                    textF2.setTextColor(getResources().getColor(R.color.unselectFloorText));

                    textF3.setBackgroundResource(R.drawable.solid_color);
                    textF3.setTextColor(getResources().getColor(R.color.unselectFloorText));
                    break;
                case "F2":
                    textLG.setBackgroundResource(R.drawable.solid_color);
                    textLG.setTextColor(getResources().getColor(R.color.unselectFloorText));

                    textG.setBackgroundResource(R.drawable.solid_color);
                    textG.setTextColor(getResources().getColor(R.color.unselectFloorText));

                    textF1.setBackgroundResource(R.drawable.solid_color);
                    textF1.setTextColor(getResources().getColor(R.color.unselectFloorText));

                    textF2.setBackgroundColor(getResources().getColor(R.color.primaryBlue));
                    textF2.setTextColor(getResources().getColor(R.color.selectFloorText));

                    textF3.setBackgroundResource(R.drawable.solid_color);
                    textF3.setTextColor(getResources().getColor(R.color.unselectFloorText));
                    break;
                case "F3":
                    textLG.setBackgroundResource(R.drawable.solid_color);
                    textLG.setTextColor(getResources().getColor(R.color.unselectFloorText));

                    textG.setBackgroundResource(R.drawable.solid_color);
                    textG.setTextColor(getResources().getColor(R.color.unselectFloorText));

                    textF1.setBackgroundResource(R.drawable.solid_color);
                    textF1.setTextColor(getResources().getColor(R.color.unselectFloorText));

                    textF2.setBackgroundResource(R.drawable.solid_color);
                    textF2.setTextColor(getResources().getColor(R.color.unselectFloorText));

                    textF3.setBackgroundColor(getResources().getColor(R.color.primaryBlue));
                    textF3.setTextColor(getResources().getColor(R.color.selectFloorText));
                    break;
            }
        }
    }

    /**
     * {@inheritDoc}
     * Stops ongoing refresh task, but not the countdown timer which stops automatically
     */
    @Override
    public void onPause() {
        refreshDataHandler.removeCallbacks(refreshDataTask);
        super.onPause();
    }

    /**
     * {@inheritDoc}
     * Restarts UI refreshing task when no countdown task is in progress
     */
    @Override
    public void onResume() {
        if (!this.settings.getBoolean("split_trajectory", false)) {
            refreshDataHandler.postDelayed(refreshDataTask, 500);
        }
        super.onResume();
    }

    private float[] offsetToGpsLatLng(float latitude, float longitude, float px, float py) {
        double R = 6378137.0;

        // Convert latitude and longitude from degrees to radians
        double latRad = Math.toRadians(latitude);
        double lonRad = Math.toRadians(longitude);

        // Convert meters to radians
        double dLat = py / R;
        double dLon = px / (R * Math.cos(latRad));

        // New latitude
        double newLat = Math.toDegrees(latRad + dLat);

        // New longitude
        double newLon = Math.toDegrees(lonRad + dLon);

        float[] newLatLng = new float[2];

        newLatLng[0] = (float) newLat;
        newLatLng[1] = (float) newLon;

        return newLatLng;
    }
}