package com.dekel.babysitter;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.LinkedList;

/**
 * Created with IntelliJ IDEA.
 * User: dekelna
 * Date: 7/16/13
 * Time: 9:26 PM
 */
public class RideStateMachine {
    public static final int BOOT_DELAY = 5 * 1000;
    public static final int SPEED_MEASURES_COUNT = 10;
    public static final int MOVING_MIN_TIME = 15 * 1000;
    public static final int STOPPING_MIN_TIME = 5 * 60 * 100; // TODO DEBUG
    public static final int FALSE_POSITIVE_STOP = 5 * 60 * 1000;
    public static float SPEED_MOVING_THRESHOLD = 8; // ~25Km/h / 3.6m/s
    public static float SPEED_STOPPED_THRESHOLD = 3; // ~10Km/h / 3.6m/s

    long serviceLoadTime = System.currentTimeMillis();
    BabyRepo babyRepo = null;

    private long stoppedSince = 0;
    private long movingSince = 0;
    private long user_havent_stopped_override = 0;

    private Context context = null;
    public RideStateMachine(Context context) {
        this.context = context;
        babyRepo = new BabyRepo(context);
    }

    LinkedList<Float> lastSpeeds = new LinkedList<Float>();
    float sumLastSpeeds = 0;

    public void notifySpeedChange(float speed) {

        if (System.currentTimeMillis() - serviceLoadTime < BOOT_DELAY) {
            Log.i(Config.MODULE_NAME, "ignoring, boot");
            return;
        }

        lastSpeeds.add(speed);
        sumLastSpeeds += speed;

        if (lastSpeeds.size() < (SPEED_MEASURES_COUNT + 1)) {
            Log.i(Config.MODULE_NAME, "Booting speed measure, already has: " + lastSpeeds.size());
            return;
        } else {
            sumLastSpeeds -= lastSpeeds.poll();
            assert lastSpeeds.size() == SPEED_MEASURES_COUNT;
        }

        float averageSpeed = sumLastSpeeds / SPEED_MEASURES_COUNT;
        Log.d(Config.MODULE_NAME ,"Current average speed is " + averageSpeed);

        if (averageSpeed > SPEED_MOVING_THRESHOLD) {

            stoppedSince = 0;
            if (movingSince == 0) {
                movingSince = System.currentTimeMillis();
                Log.d(Config.MODULE_NAME, "ON THE MOVE? " +movingSince);

            } else {
                if (System.currentTimeMillis() - movingSince > MOVING_MIN_TIME) {
                    if (!babyRepo.isRideInProgress()) {
                        handleRideStarted();
                    }
                }
            }

        } else if (averageSpeed < SPEED_STOPPED_THRESHOLD) {
            movingSince = 0;
            Log.d(Config.MODULE_NAME, "STOPEED! " +movingSince);
            if (stoppedSince == 0) {
                stoppedSince = System.currentTimeMillis();
            } else if (user_havent_stopped_override != 0) {
                if (System.currentTimeMillis() - user_havent_stopped_override > FALSE_POSITIVE_STOP) {
                    user_havent_stopped_override = 0;
                }

            } else {
                if (System.currentTimeMillis() - stoppedSince > STOPPING_MIN_TIME) {
                    if (babyRepo.isRideInProgress()) {
                        handleRideStopped();
                    }
                }
            }
        }
    }

    public void handleRideStarted() {
        Log.d(Config.MODULE_NAME, "handleRideStarted");
        assert !babyRepo.isRideInProgress();
        babyRepo.setRideInProgress(true);

        if (babyRepo.isDialogPendingUser()) {
            return;
            // TODO what's the expected behaviour?
        }

        Log.d(Config.MODULE_NAME, Config.SHOW_RIDE_STARTED_ALERT_INTENT_EXTRA);
        startAlertActivityWithIntent(Config.SHOW_RIDE_STARTED_ALERT_INTENT_EXTRA);
    }

    private void handleRideStopped() {
        Log.d(Config.MODULE_NAME, "handleRideStopped");
        assert babyRepo.isRideInProgress();
        babyRepo.setRideInProgress(false);

        if (babyRepo.isBabyInCar()) {
            babyRepo.setBabyInCar(false);

            if (babyRepo.isDialogPendingUser()) {
                // TODO what's the expected behaviour?
                // TODO maybe alert without sound? medium alert? diffrent text?
                return;
            }

            Log.d(Config.MODULE_NAME , Config.SHOW_RIDE_FINISHED_ALERT_INTENT_EXTRA);
            startAlertActivityWithIntent(Config.SHOW_RIDE_FINISHED_ALERT_INTENT_EXTRA);
        }
    }

    public void userChoiceHasntFinishedRide() {
        babyRepo.setDialogPendingUser(false);
        babyRepo.setRideInProgress(true);
        babyRepo.setBabyInCar(true);
        user_havent_stopped_override = System.currentTimeMillis();
    }

    public void userChoiseFinishedRide() {
        babyRepo.setDialogPendingUser(false);
        babyRepo.setRideInProgress(false);
        babyRepo.setBabyInCar(false);
    }

    public void UserChoiceRidingWithBaby() {
        babyRepo.setDialogPendingUser(false);
        babyRepo.setRideInProgress(true);
        babyRepo.setBabyInCar(true);
    }

    public void userChoiceRidingAlone() {
        babyRepo.setDialogPendingUser(false);
        babyRepo.setRideInProgress(true);
        babyRepo.setBabyInCar(false);
    }

    private void startAlertActivityWithIntent(String s) {
        babyRepo.setDialogPendingUser(true);
        Intent i = new Intent(context, AlertActivity.class);
        i.putExtra(s, true);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP); // TODO
        context.startActivity(i);
    }
}
