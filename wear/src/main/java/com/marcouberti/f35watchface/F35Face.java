package com.marcouberti.f35watchface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.wearable.CapabilityApi;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableStatusCodes;
import com.marcouberti.f35watchface.utils.moonphase.MoonPhase;
import com.marcouberti.f35watchface.utils.ScreenUtils;
import com.marcouberti.f35watchface.utils.moonphase.StarDate;
import com.marcouberti.f35watchface.utils.stopwatch.StopWatch;

import java.lang.ref.WeakReference;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Set;
import java.util.TimeZone;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't shown. On
 * devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient mode.
 */
public class F35Face extends CanvasWatchFaceService {

    private static final String TAG = "NatureGradientsFace";

    private static final String F35_WEARABLE_CAPABILITY_NAME = "f35_wearable_capability";
    private static final String LAST_KNOW_GPS_POSITION = "/gps_position";
    private String phoneNodeId = null;

    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
    /**
     * Update rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static long INTERACTIVE_UPDATE_RATE_MS = 1000;
    private static final long INTERACTIVE_UPDATE_RATE_MS_NORMAL = 1000;
    private static final long INTERACTIVE_UPDATE_RATE_MS_STOPWATCH = 30;

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    private static final int CHRONO = 0;
    private static final int WEEK_DAYS_BATTERY = 1;
    private static final int COORDINATES = 2;
    private static final int MONTH_AND_DAY = 3;
    private static final int MONTH_AND_YEAR = 4;
    private static final int MOON = 5;
    private static final int WEAR_BATTERY = 6;

    private static final int NIGHT_MODE_ON = 0;
    private static final int NIGHT_MODE_OFF = 1;
    private static int NIGHT_MODE = NIGHT_MODE_OFF;

    //private int BOTTOM_COMPLICATION_MODE = BATTERY;
    private int LEFT_COMPLICATION_MODE = MOON;
    private int RIGHT_COMPLICATION_MODE = WEEK_DAYS_BATTERY;

    int selectedColorCode;
    String lastKnowCoordinates = "0.00 / 0.00";

    private StopWatch stopWatch = new StopWatch();

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine implements DataApi.DataListener,
            GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,  MessageApi.MessageListener{

        Bitmap bg;
        Paint mBackgroundPaint;
        Paint mSecondsCirclePaint,mDarkSecondsCirclePaint, smallTextPaint;
        Paint logoTextPaint;
        Paint blackFillPaint, whiteFillPaint, darkGrayFillPaint;
        Paint accentFillPaint, chronoPaint;
        Paint complicationArcAccentPaint,complicationArcBatteryPaint;
        Paint largeTextPaint, mediumTextPaint, normalTextPaint;
        boolean mAmbient;
        Calendar mCalendar;
        Time mTime;
        boolean mIsRound =false;
        float CR;
        Typeface logoTypeface = Typeface.createFromAsset(getApplicationContext().getAssets(), "fonts/square_sans_serif_7.ttf");
        Typeface monospacedTypeface = Typeface.createFromAsset(getApplicationContext().getAssets(), "fonts/larabiefont.ttf");

        final Handler mUpdateTimeHandler = new EngineHandler(this);

        GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(F35Face.this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };
        boolean mRegisteredTimeZoneReceiver = false;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);
            mIsRound = insets.isRound();
            if(mIsRound) {
                float smallSize = ScreenUtils.getScreenWidth(getApplicationContext())/28f;
                float normalSize = ScreenUtils.getScreenWidth(getApplicationContext())/17f;
                float mediumSize = ScreenUtils.getScreenWidth(getApplicationContext())/15f;
                float largeSize = ScreenUtils.getScreenWidth(getApplicationContext())/12f;
                //set round bg images
                smallTextPaint.setTextSize(smallSize);
                normalTextPaint.setTextSize(normalSize);
                mediumTextPaint.setTextSize(mediumSize);
                largeTextPaint.setTextSize(largeSize);
                chronoPaint.setTextSize(normalSize);
                logoTextPaint.setTextSize(normalSize);
            }else{
                float smallSize = ScreenUtils.getScreenWidth(getApplicationContext())/28f;
                float normalSize = ScreenUtils.getScreenWidth(getApplicationContext())/17f;
                float mediumSize = ScreenUtils.getScreenWidth(getApplicationContext())/15f;
                float largeSize = ScreenUtils.getScreenWidth(getApplicationContext())/12f;
                //set round bg images
                smallTextPaint.setTextSize(smallSize);
                normalTextPaint.setTextSize(normalSize);
                mediumTextPaint.setTextSize(mediumSize);
                largeTextPaint.setTextSize(largeSize);
                chronoPaint.setTextSize(normalSize);
                logoTextPaint.setTextSize(normalSize);
            }
        }

        @Override
        public void onTapCommand(@TapType int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case WatchFaceService.TAP_TYPE_TAP:
                    //detect screen area (CENTER_LEFT, CENTER_RIGHT, BOTTOM_CENTER)
                    handleTouch(x,y);
                    invalidate();
                    break;

                case WatchFaceService.TAP_TYPE_TOUCH:
                    break;
                case WatchFaceService.TAP_TYPE_TOUCH_CANCEL:
                    break;

                default:
                    super.onTapCommand(tapType, x, y, eventTime);
                    break;
            }
        }

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(F35Face.this)
                    .setAcceptsTapEvents(true)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                            //.setStatusBarGravity(Gravity.CENTER_VERTICAL)
                    .setShowSystemUiTime(false).
                            setViewProtectionMode(WatchFaceStyle.PROTECT_STATUS_BAR)
                    .build());

            bg = BitmapFactory.decodeResource(getResources(), R.drawable.background);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setAntiAlias(true);

            mSecondsCirclePaint= new Paint();
            mSecondsCirclePaint.setAntiAlias(true);
            mSecondsCirclePaint.setStyle(Paint.Style.FILL);
            mSecondsCirclePaint.setStrokeWidth(ScreenUtils.convertDpToPixels(getApplicationContext(), 3f));

            mDarkSecondsCirclePaint= new Paint();
            mDarkSecondsCirclePaint.setAntiAlias(true);
            mDarkSecondsCirclePaint.setStyle(Paint.Style.FILL);
            mDarkSecondsCirclePaint.setStrokeWidth(ScreenUtils.convertDpToPixels(getApplicationContext(), 3f));

            smallTextPaint = new Paint();
            smallTextPaint.setAntiAlias(true);
            smallTextPaint.setTextAlign(Paint.Align.CENTER);
            smallTextPaint.setTypeface(Typeface.createFromAsset(getApplicationContext().getAssets(), "fonts/Dolce Vita Heavy Bold.ttf"));

            largeTextPaint = new Paint();
            largeTextPaint.setAntiAlias(true);
            largeTextPaint.setTextAlign(Paint.Align.CENTER);
            largeTextPaint.setTypeface(Typeface.createFromAsset(getApplicationContext().getAssets(), "fonts/Dolce Vita.ttf"));

            mediumTextPaint = new Paint();
            mediumTextPaint.setAntiAlias(true);
            mediumTextPaint.setTextAlign(Paint.Align.CENTER);
            mediumTextPaint.setTypeface(Typeface.createFromAsset(getApplicationContext().getAssets(), "fonts/Dolce Vita.ttf"));

            normalTextPaint = new Paint();
            normalTextPaint.setAntiAlias(true);
            normalTextPaint.setTextAlign(Paint.Align.CENTER);
            normalTextPaint.setTypeface(Typeface.createFromAsset(getApplicationContext().getAssets(), "fonts/Dolce Vita.ttf"));

            accentFillPaint= new Paint();
            accentFillPaint.setAntiAlias(true);
            accentFillPaint.setTextAlign(Paint.Align.CENTER);
            accentFillPaint.setStrokeWidth(ScreenUtils.convertDpToPixels(getApplicationContext(), 1.5f));

            chronoPaint= new Paint();
            chronoPaint.setAntiAlias(true);
            chronoPaint.setTextAlign(Paint.Align.CENTER);
            chronoPaint.setTypeface(Typeface.createFromAsset(getApplicationContext().getAssets(), "fonts/Dolce Vita Heavy Bold.ttf"));

            complicationArcAccentPaint= new Paint();
            complicationArcAccentPaint.setStyle(Paint.Style.STROKE);
            complicationArcAccentPaint.setStrokeCap(Paint.Cap.BUTT);
            complicationArcAccentPaint.setStrokeWidth(ScreenUtils.getScreenWidth(getApplicationContext()) / 50);
            complicationArcAccentPaint.setAntiAlias(true);

            complicationArcBatteryPaint= new Paint();
            complicationArcBatteryPaint.setStyle(Paint.Style.STROKE);
            complicationArcBatteryPaint.setStrokeCap(Paint.Cap.BUTT);
            complicationArcBatteryPaint.setStrokeWidth(ScreenUtils.getScreenWidth(getApplicationContext()) / 20);
            complicationArcBatteryPaint.setAntiAlias(true);

            logoTextPaint= new Paint();
            logoTextPaint.setAntiAlias(true);
            logoTextPaint.setTextAlign(Paint.Align.CENTER);
            logoTextPaint.setTypeface(logoTypeface);

            blackFillPaint = new Paint();
            blackFillPaint.setColor(Color.BLACK);
            blackFillPaint.setStyle(Paint.Style.FILL);
            blackFillPaint.setAntiAlias(true);

            whiteFillPaint = new Paint();
            whiteFillPaint.setColor(Color.WHITE);
            whiteFillPaint.setStyle(Paint.Style.FILL);
            whiteFillPaint.setAntiAlias(true);
            whiteFillPaint.setFilterBitmap(true);

            darkGrayFillPaint = new Paint();
            darkGrayFillPaint.setColor(Color.DKGRAY);
            darkGrayFillPaint.setStyle(Paint.Style.FILL);
            darkGrayFillPaint.setAntiAlias(true);
            darkGrayFillPaint.setFilterBitmap(true);

            mTime = new Time();
            mCalendar = Calendar.getInstance();

            selectedColorCode = GradientsUtils.getGradients(getApplicationContext(), -1);

            updatePaintColors();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    //mHandPaint.setAntiAlias(!inAmbientMode);
                }
                updateBackground();
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            int width = bounds.width();
            int height = bounds.height();
            CR = width/8f;
            /*
             * These calculations reflect the rotation in degrees per unit of time, e.g.,
             * 360 / 60 = 6 and 360 / 12 = 30.
             */
            float seconds =
                    (mCalendar.get(Calendar.SECOND) + mCalendar.get(Calendar.MILLISECOND) / 1000f);

            //if chrono change seconds value
            if(LEFT_COMPLICATION_MODE == CHRONO || RIGHT_COMPLICATION_MODE == CHRONO) {
                if (stopWatch.running) {
                    seconds = (float)stopWatch.getElapsedTimeSecs()+ (float)stopWatch.getElapsedTimeMillis() / 1000f;
                }else if(stopWatch.paused){
                    seconds = lastStopWatchSecondsValue+lastMillisValue/1000f;
                }else seconds = 0;
            }

            final float secondsRotation = seconds * 6f;

            final float minutesRotation = mCalendar.get(Calendar.MINUTE) * 6f;

            final float hourHandOffset = mCalendar.get(Calendar.MINUTE) / 2f;
            final float hoursRotation = (mCalendar.get(Calendar.HOUR) * 30) + hourHandOffset;

            //BACKGROUND
            Rect src = new Rect(0,0, bg.getWidth(), bg.getHeight());
            canvas.drawBitmap(bg, src, bounds, whiteFillPaint);

            //Accent triangle
            if(!mAmbient) {
                drawTopTriangle(canvas, width, height);
            }

            //LOGO TEXT
            if(!mAmbient) {
                float smallSize = ScreenUtils.getScreenWidth(getApplicationContext())/20f;
                float normalSize = ScreenUtils.getScreenWidth(getApplicationContext())/17f;
                logoTextPaint.setTypeface(monospacedTypeface);
                logoTextPaint.setTextSize(normalSize);
                if (stopWatch.running) {
                    drawTextLogo(canvas, stopWatch.toString() + "." + String.format("%03d", stopWatch.getElapsedTimeMillis()), width, height);
                } else if (stopWatch.paused) {
                    drawTextLogo(canvas, lastStopWatchValue + "." + lastMillisValue, width, height);
                } else {
                    logoTextPaint.setTypeface(logoTypeface);
                    logoTextPaint.setTextSize(smallSize);
                    drawTextLogo(canvas, "AIRHERO", width, height);
                }
            }
            //END LOGO TEXT

            //COMPLICATIONS
            if(!mAmbient) {
                //left bottom
                drawLeftComplication(canvas, width, height);
                //right bottom
                drawRightComplication(canvas, width, height);
            }
            //END COMPLICATIONS

            //Hands sizes and round rect readius
            int RR = ScreenUtils.convertDpToPixels(getApplicationContext(), 10);
            int RRradius = (int)((width/2f)/30);

            //Minutes hand
            canvas.save();
            canvas.rotate(minutesRotation, width / 2, width / 2);
            canvas.drawRoundRect(width / 2 - RRradius, (height / 2F) * 0.20F, width / 2 + RRradius, (height / 2f) * 0.85F, RR, RR, mSecondsCirclePaint);
            canvas.drawLine(width / 2, height / 2, width / 2, (height / 2F) * 0.20F, mDarkSecondsCirclePaint);
            canvas.restore();
            //END Minutes hands

            //Hours hand
            canvas.save();
            canvas.rotate(hoursRotation, width / 2, width / 2);
            canvas.drawRoundRect(width / 2 - RRradius, (height / 2F) * 0.35F, width / 2 + RRradius, (height / 2f) * 0.85F, RR, RR, mSecondsCirclePaint);
            canvas.drawLine(width / 2, height / 2, width / 2, (height / 2F) * 0.35F, mDarkSecondsCirclePaint);
            if (!mAmbient) {
                canvas.drawCircle(width / 2, (height / 2F) * 0.38F, ScreenUtils.convertDpToPixels(getApplicationContext(), 2.5F), accentFillPaint);
            }
            canvas.restore();
            //END Hours hand

            //Center circle
            canvas.drawCircle(width / 2, height / 2, ScreenUtils.convertDpToPixels(getApplicationContext(), 6), mDarkSecondsCirclePaint);

            //Seconds hand
            if(!mAmbient) {
                canvas.save();
                canvas.rotate(secondsRotation, width / 2, width / 2);
                accentFillPaint.setStyle(Paint.Style.STROKE);
                canvas.drawLine(width / 2, height / 2 + (height / 15) * 2f, width / 2, (height / 25), accentFillPaint);
                accentFillPaint.setStyle(Paint.Style.FILL);
                canvas.restore();
            }
            //END seconds hand

            //Red center circle
            if(!mAmbient && NIGHT_MODE == NIGHT_MODE_OFF) {
                canvas.drawCircle(width / 2, height / 2, ScreenUtils.convertDpToPixels(getApplicationContext(), 3.5f), accentFillPaint);
            }else if(!mAmbient && NIGHT_MODE == NIGHT_MODE_ON) {
                canvas.drawCircle(width / 2, height / 2, ScreenUtils.convertDpToPixels(getApplicationContext(), 3.5f), accentFillPaint);
            }
            else if(mAmbient && NIGHT_MODE == NIGHT_MODE_OFF){
                canvas.drawCircle(width / 2, height / 2, ScreenUtils.convertDpToPixels(getApplicationContext(), 3.5f), blackFillPaint);
            }else if(mAmbient && NIGHT_MODE == NIGHT_MODE_ON){
                canvas.drawCircle(width / 2, height / 2, ScreenUtils.convertDpToPixels(getApplicationContext(), 3.5f), blackFillPaint);
            }

        }

        private void drawLeftComplication(Canvas canvas, int width, int height) {
            float LX = width*0.3530f;
            float LY = height*0.6220f;

            if(LEFT_COMPLICATION_MODE == CHRONO
                    || RIGHT_COMPLICATION_MODE == CHRONO) {
                String text = "START";
                if(stopWatch.running) text = "PAUSE";
                else if(stopWatch.paused) text = "RESUME";
                drawStopWatch(canvas, text, width, height, LX, LY);
            }else if(LEFT_COMPLICATION_MODE == MOON) {
                drawMoonPhase(canvas, width, height, LX, LY);
            }else if(LEFT_COMPLICATION_MODE == WEEK_DAYS_BATTERY) {
                drawWeekDays(canvas, width, height, LX, LY);
            }else if(LEFT_COMPLICATION_MODE == COORDINATES) {
                drawCoordinates(canvas, width, height, LX, LY);
            }else if(LEFT_COMPLICATION_MODE == MONTH_AND_DAY) {
                drawMonthAndDay(canvas, width, height, LX, LY);
            }else if(LEFT_COMPLICATION_MODE == MONTH_AND_YEAR) {
                drawMonthAndYear(canvas, width, height, LX, LY);
            }else if(LEFT_COMPLICATION_MODE == WEAR_BATTERY) {
                drawBatteryWear(canvas, width, height, LX, LY);
            }
        }

        private void drawRightComplication(Canvas canvas, int width, int height) {
            float RX = height*0.6420f;
            float RY = height*0.6220f;

            //draw bg circle
            //canvas.drawCircle(RX,RY,CR,blackFillPaint);
            if(LEFT_COMPLICATION_MODE == CHRONO
                    || RIGHT_COMPLICATION_MODE == CHRONO) {
                String text = "STOP";
                drawStopWatch(canvas, text, width, height, RX, RY);
            }
            else if(RIGHT_COMPLICATION_MODE == MOON) {
                drawMoonPhase(canvas, width, height, RX, RY);
            }else if(RIGHT_COMPLICATION_MODE == WEEK_DAYS_BATTERY) {
                drawWeekDays(canvas, width, height, RX, RY);
            }else if(RIGHT_COMPLICATION_MODE == COORDINATES) {
                drawCoordinates(canvas, width, height, RX, RY);
            }else if(RIGHT_COMPLICATION_MODE == MONTH_AND_DAY) {
                drawMonthAndDay(canvas, width, height, RX, RY);
            }else if(RIGHT_COMPLICATION_MODE == MONTH_AND_YEAR) {
                drawMonthAndYear(canvas, width, height, RX, RY);
            }else if(RIGHT_COMPLICATION_MODE == WEAR_BATTERY) {
                drawBatteryWear(canvas, width, height, RX, RY);
            }
        }

        private void drawBatteryWear(Canvas canvas, int width, int height,  float CX, float CY) {
            //Battery level
            int batteryPercentage = getBatteryLevel();
            int deg = batteryPercentage * 360 /100;
            canvas.save();
            canvas.rotate(-90, CX, CY);
            complicationArcBatteryPaint.setAlpha(40);
            float RP = 0.7f;
            canvas.drawArc(new RectF(CX - CR * RP, CY - CR * RP, CX + CR * RP, CY + CR * RP), 0, 360, false, complicationArcBatteryPaint);
            complicationArcBatteryPaint.setAlpha(255);
            canvas.drawArc(new RectF(CX - CR * RP, CY - CR * RP, CX + CR * RP, CY + CR * RP), 0, deg, false, complicationArcBatteryPaint);
            canvas.restore();

            //Day number
            String perc = batteryPercentage+"%";
            Rect bounds = new Rect();
            int previousColor = normalTextPaint.getColor();
            normalTextPaint.setColor(whiteFillPaint.getColor());
            normalTextPaint.getTextBounds(perc, 0, perc.length(), bounds);
            canvas.drawText(perc, CX, CY + bounds.height() / 2, normalTextPaint);
            normalTextPaint.setColor(previousColor);
        }

        private void drawWeekDays(Canvas canvas, int width, int height,  float CX, float CY) {

            String[] days =getWeekDaysSymbols();
            Path rPath = new Path();
            rPath.addCircle(CX, CY, CR * 0.7f, Path.Direction.CW);
            for(int i=0; i<days.length; i++) {

                if(days[i] == null || days[i].equalsIgnoreCase("")) continue;

                canvas.save();
                canvas.rotate(i * 51.4f, CX, CY);
                int previousColor = smallTextPaint.getColor();
                if(days[i].toUpperCase().equalsIgnoreCase(getWeekDay())) {
                    smallTextPaint.setColor(accentFillPaint.getColor());
                }else {
                    smallTextPaint.setColor(previousColor);
                }
                canvas.drawTextOnPath(days[i].toUpperCase(), rPath, 0, 0, smallTextPaint);
                smallTextPaint.setColor(previousColor);
                canvas.restore();
            }

            //Battery level
            int batteryPercentage = getBatteryLevel();
            int deg = batteryPercentage * 360 /100;
            canvas.save();
            canvas.rotate(-90, CX, CY);
            canvas.drawArc(new RectF(CX - CR * 0.5f, CY - CR * 0.5f, CX + CR * 0.5f, CY + CR * 0.5f), 0, deg, false, complicationArcAccentPaint);
            canvas.restore();

            //Day number
            String dayNumber = getDayNumber();
            Rect bounds = new Rect();
            int previousColor = normalTextPaint.getColor();
            normalTextPaint.setColor(whiteFillPaint.getColor());
            normalTextPaint.getTextBounds(dayNumber, 0, dayNumber.length(), bounds);
            canvas.drawText(dayNumber, CX, CY + bounds.height() / 2, normalTextPaint);
            normalTextPaint.setColor(previousColor);
        }

        long lastLocationTs = -1;
        private void drawCoordinates(Canvas canvas, int width, int height,  float CX, float CY) {
            long now = System.currentTimeMillis();
            //phone message only every 5min at least
            if(lastLocationTs == -1 || now - lastLocationTs > 60000*5) {
                fireMessage(LAST_KNOW_GPS_POSITION);
            }

            //draw
            canvas.save();
            canvas.rotate(90, CX, CY);
            Path path = new Path();
            path.addCircle(CX, CY, CR * 0.7f, Path.Direction.CW);
            canvas.drawTextOnPath("LATITUDE AND LONGITUDE", path, 0, 0, smallTextPaint);
            canvas.restore();

            String coord = lastKnowCoordinates;
            String[] parts = coord.split("/");
            String lat = parts[0].trim();
            String lon = parts[1].trim();
            Rect bounds = new Rect();
            int previousColor = normalTextPaint.getColor();
            normalTextPaint.getTextBounds(lat, 0, lat.length(), bounds);
            normalTextPaint.setColor(whiteFillPaint.getColor());
            canvas.drawText(lat, CX, CY, normalTextPaint);
            canvas.drawText(lon, CX, CY+bounds.height()+4, normalTextPaint);
            normalTextPaint.setColor(previousColor);
        }

        private String lastStopWatchValue = "";
        private int lastMillisValue = 0;
        private int lastStopWatchSecondsValue = 0;
        private void drawStopWatch(Canvas canvas, String text, int width, int height,  float CX, float CY) {
            //draw
            canvas.save();
            canvas.rotate(90, CX, CY);
            Path path = new Path();
            path.addCircle(CX, CY, CR * 0.7f, Path.Direction.CW);
            canvas.drawTextOnPath("CHRONO", path, 0, 0, smallTextPaint);
            canvas.restore();

            Rect bounds = new Rect();
            normalTextPaint.getTextBounds(text, 0, text.length(), bounds);
            canvas.drawText(text, CX, CY + bounds.height() / 2, chronoPaint);
        }

        private void drawMonthAndDay(Canvas canvas, int width, int height, float CX, float CY) {

            //left bottom
            //canvas.drawCircle(LX, LY, CR, mSecondsCirclePaint);
            canvas.save();
            canvas.rotate(90, CX, CY);
            Path path = new Path();
            path.addCircle(CX, CY, CR * 0.7f, Path.Direction.CW);
            canvas.drawTextOnPath(getMonthExtended(), path, 0, 0, smallTextPaint);
            canvas.restore();

            String dayNumber = getDayNumber();
            Rect bounds = new Rect();
            int previousColor = largeTextPaint.getColor();
            largeTextPaint.getTextBounds(dayNumber, 0, dayNumber.length(), bounds);
            largeTextPaint.setColor(whiteFillPaint.getColor());
            canvas.drawText(dayNumber, CX, CY + bounds.height() / 2, largeTextPaint);
            largeTextPaint.setColor(previousColor);
        }

        private void drawMoonPhase(Canvas canvas, int W, int H, float CX, float CY) {

            //left bottom
            //canvas.drawCircle(LX, LY, CR, mSecondsCirclePaint);
            canvas.save();
            canvas.rotate(90, CX, CY);
            Path path = new Path();
            path.addCircle(CX, CY, CR * 0.7f, Path.Direction.CW);
            canvas.drawTextOnPath("MOON PHASE", path, 0, 0, smallTextPaint);
            canvas.restore();

            //DRAW MOON
            // Create new each time so it is update on every redraw.
            StarDate date = new StarDate();

            int width = (int)CR;
            int height = (int)CR;
            double phaseAngle = new MoonPhase().getPhaseAngle(date);

            int xcenter = (int)CX;
            int ycenter = (int)CY;

            int moonradius= (int) (Math.min(width, height) * .4);
            // draw the whole moon disk, in moonColor:
            RectF oval = new RectF();
            oval.set(xcenter - moonradius, ycenter - moonradius, xcenter
                    + moonradius, ycenter + moonradius);
            canvas.drawOval(oval, whiteFillPaint);


            /* The phase angle is the angle sun-moon-earth,
             so 0 = full phase, 180 = new.
             What we're actually interested in for drawing purposes
             is the position angle of the sunrise terminator,
             which runs the opposite direction from the phase angle,
             so we have to convert. */
            double positionAngle = Math.PI - phaseAngle;
            if (positionAngle < 0.)
                positionAngle += 2. * Math.PI;

            // Okay, now fill in the dark part.

            double cosTerm = Math.cos(positionAngle);
            //if (cosTerm < 0) cosTerm = -cosTerm;
            moonradius+=1;//FIX WHITE BEHIND EDGES
            double rsquared = moonradius * moonradius;
            int whichQuarter = ((int) (positionAngle * 2. / Math.PI) + 4) % 4;
            int j;

            for (j = 0; j <= moonradius; ++j) {
                double rrf = Math.sqrt(rsquared - j * j);
                int rr = (int) (rrf + .5);
                int xx = (int) (rrf * cosTerm);
                int x1 = xcenter - (whichQuarter < 2 ? rr : xx);
                int w = rr + xx + 1;
                canvas.drawRect(x1, ycenter - j, w + x1, ycenter - j + 1, darkGrayFillPaint);
                canvas.drawRect(x1, ycenter + j, w + x1, ycenter + j + 1, darkGrayFillPaint);
            }
            //END DRAW MOON

            //Draw moon age
            com.marcouberti.f35watchface.utils.moonage.MoonPhase moonAge = new com.marcouberti.f35watchface.utils.moonage.MoonPhase(Calendar.getInstance());
            moonAge.getPhase();
            String age = moonAge.getMoonAgeAsDays();

            canvas.save();
            canvas.rotate(-90, CX, CY);
            Path pathAge = new Path();
            pathAge.addCircle(CX, CY, CR * 0.9f, Path.Direction.CCW);
            canvas.drawTextOnPath(age, pathAge, 0, 0, smallTextPaint);
            canvas.restore();
        }

        private void drawMonthAndYear(Canvas canvas, int width, int height, float CX, float CY) {

            //left bottom
            //canvas.drawCircle(LX, LY, CR, mSecondsCirclePaint);
            canvas.save();
            canvas.rotate(90, CX, CY);
            Path path = new Path();
            path.addCircle(CX, CY, CR * 0.7f, Path.Direction.CW);
            canvas.drawTextOnPath(getMonthExtended(), path, 0, 0, smallTextPaint);
            canvas.restore();

            String year = getYear();
            Rect bounds = new Rect();
            int previousColor = mediumTextPaint.getColor();
            mediumTextPaint.setColor(whiteFillPaint.getColor());
            mediumTextPaint.getTextBounds(year, 0, year.length(), bounds);
            canvas.drawText(year, CX, CY + bounds.height() / 2, mediumTextPaint);
            mediumTextPaint.setColor(previousColor);
        }

        private void drawTopTriangle(Canvas canvas, int width, int height) {
            int TS = ScreenUtils.convertDpToPixels(getApplicationContext(), 5);
            if(!mIsRound) {
                TS = ScreenUtils.convertDpToPixels(getApplicationContext(), 3.5f);
            }
            Path minutesPath = new Path();
            minutesPath.moveTo((width / 2), (height / 2) * 0.31f);
            minutesPath.lineTo((width / 2) - TS, (height / 2) * 0.3f - TS * 1.9F);
            minutesPath.lineTo((width / 2) + TS, (height / 2) * 0.3f - TS * 1.9F);
            minutesPath.lineTo((width / 2), (height / 2) * 0.31f);
            minutesPath.close();
            int previousColor = accentFillPaint.getColor();
            if(NIGHT_MODE == NIGHT_MODE_ON) accentFillPaint.setColor(getResources().getColor(R.color.night_mode));
            canvas.drawPath(minutesPath, accentFillPaint);
            accentFillPaint.setColor(previousColor);
        }

        private void drawTextLogo(Canvas canvas, String text, int width, int height) {
            canvas.drawText(text, width / 2, (height / 2) * 0.80f, logoTextPaint);
        }

        private String[] getWeekDaysSymbols(){
            DateFormatSymbols symbols = new DateFormatSymbols();
            String[] dayNames = symbols.getShortWeekdays();
            return dayNames;
        }

        private String getWeekDay() {
            return new SimpleDateFormat("EEE").format(Calendar.getInstance().getTime()).toUpperCase();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                mGoogleApiClient.connect();
                Wearable.MessageApi.addListener(mGoogleApiClient, this);
                registerReceiver();
                new Thread() {
                    @Override
                    public void run() {
                        setupF35Wearable();
                    }
                }.start();
                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    Wearable.DataApi.removeListener(mGoogleApiClient, this);
                    Wearable.MessageApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                }
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
            updateBackground();
        }

        private void setupF35Wearable() {
            CapabilityApi.GetCapabilityResult result =
                    Wearable.CapabilityApi.getCapability(
                            mGoogleApiClient, F35_WEARABLE_CAPABILITY_NAME,
                            CapabilityApi.FILTER_REACHABLE).await();

            updateTranscriptionCapability(result.getCapability());

            //setupComplete(); we can fire messages

            CapabilityApi.CapabilityListener capabilityListener =
                    new CapabilityApi.CapabilityListener() {
                        @Override
                        public void onCapabilityChanged(CapabilityInfo capabilityInfo) {
                            updateTranscriptionCapability(capabilityInfo);
                        }
                    };

            Wearable.CapabilityApi.addCapabilityListener(
                    mGoogleApiClient,
                    capabilityListener,
                    F35_WEARABLE_CAPABILITY_NAME);
        }

        private void updateTranscriptionCapability(CapabilityInfo capabilityInfo) {
            Set<Node> connectedNodes = capabilityInfo.getNodes();
            phoneNodeId = pickBestNodeId(connectedNodes);
        }

        private String pickBestNodeId(Set<Node> nodes) {
            String bestNodeId = null;
            // Find a nearby node or pick one arbitrarily
            for (Node node : nodes) {
                if (node.isNearby()) {
                    return node.getId();
                }
                bestNodeId = node.getId();
            }
            return bestNodeId;
        }

        protected void fireMessage(final String command) {
            if (phoneNodeId == null) {
                //bad, we cannot send message
            } else {
                //fire the message to the first connected node in list
                Log.d(TAG, "Sending message to Node with ID: " + phoneNodeId);

                PendingResult<MessageApi.SendMessageResult> messageResult = Wearable.MessageApi.sendMessage(mGoogleApiClient, phoneNodeId, command, null);
                messageResult.setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
                    @Override
                    public void onResult(MessageApi.SendMessageResult sendMessageResult) {
                        Status status = sendMessageResult.getStatus();
                        Log.d(TAG, "Status: " + status.toString());
                        if (status.getStatusCode() != WearableStatusCodes.SUCCESS) {
                            Log.e(TAG,"Something go wrong during sending command... "+command);
                        } else {
                            Log.d(TAG,"Message sent successfully to node. "+command);
                        }
                    }
                });
            }
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            F35Face.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            F35Face.this.unregisterReceiver(mTimeZoneReceiver);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }


        private void updateConfigDataItemAndUiOnStartup() {
            WatchFaceUtil.fetchConfigDataMap(mGoogleApiClient,
                    new WatchFaceUtil.FetchConfigDataMapCallback() {
                        @Override
                        public void onConfigDataMapFetched(DataMap startupConfig) {
                            // If the DataItem hasn't been created yet or some keys are missing,
                            // use the default values.
                            setDefaultValuesForMissingConfigKeys(startupConfig);
                            WatchFaceUtil.putConfigDataItem(mGoogleApiClient, startupConfig);

                            updateUiForConfigDataMap(startupConfig);
                        }
                    }
            );
        }

        private void setDefaultValuesForMissingConfigKeys(DataMap config) {
            addIntKeyIfMissing(config, WatchFaceUtil.KEY_BACKGROUND_COLOR,
                    WatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_BACKGROUND);
        }

        private void addIntKeyIfMissing(DataMap config, String key, int color) {
            if (!config.containsKey(key)) {
                config.putInt(key, color);
            }
        }

        @Override // DataApi.DataListener
        public void onDataChanged(DataEventBuffer dataEvents) {
            for (DataEvent dataEvent : dataEvents) {
                if (dataEvent.getType() != DataEvent.TYPE_CHANGED) {
                    continue;
                }

                DataItem dataItem = dataEvent.getDataItem();
                if (!dataItem.getUri().getPath().equals(
                        WatchFaceUtil.PATH_WITH_FEATURE)) {
                    continue;
                }

                DataMapItem dataMapItem = DataMapItem.fromDataItem(dataItem);
                DataMap config = dataMapItem.getDataMap();
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Config DataItem updated:" + config);
                }
                updateUiForConfigDataMap(config);
            }
        }

        private void updateUiForConfigDataMap(final DataMap config) {
            boolean uiUpdated = false;
            for (String configKey : config.keySet()) {
                if (!config.containsKey(configKey)) {
                    continue;
                }

                if(configKey.equalsIgnoreCase(WatchFaceUtil.KEY_BACKGROUND_COLOR)) {
                    int color = config.getInt(configKey);
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "Found watch face config key: " + configKey + " -> "
                                + color);
                    }
                    if (updateUiForKey(configKey, color)) {
                        uiUpdated = true;
                    }
                }
            }
            if (uiUpdated) {
                invalidate();
            }
        }

        /**
         * Updates the color of a UI item according to the given {@code configKey}. Does nothing if
         * {@code configKey} isn't recognized.
         *
         * @return whether UI has been updated
         */
        private boolean updateUiForKey(String configKey, int color) {
            if (configKey.equals(WatchFaceUtil.KEY_BACKGROUND_COLOR)) {
                setGradient(color);
            } else {
                Log.w(TAG, "Ignoring unknown config key: " + configKey);
                return false;
            }
            return true;
        }

        private void setGradient(int color) {
            Log.d("color=", color + "");
            selectedColorCode = color;

            updatePaintColors();
        }

        @Override  // GoogleApiClient.ConnectionCallbacks
        public void onConnected(Bundle connectionHint) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onConnected: " + connectionHint);
            }
            Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);
            updateConfigDataItemAndUiOnStartup();
        }

        @Override  // GoogleApiClient.ConnectionCallbacks
        public void onConnectionSuspended(int cause) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onConnectionSuspended: " + cause);
            }
        }

        @Override  // GoogleApiClient.OnConnectionFailedListener
        public void onConnectionFailed(ConnectionResult result) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onConnectionFailed: " + result);
            }
        }

        @Override
        public void onMessageReceived(MessageEvent messageEvent) {
            Log.v("WEAR", "In onMessageReceived()");

            if (messageEvent.getPath().contains(LAST_KNOW_GPS_POSITION)) {
                try {
                    Log.d(TAG, "Received message " + LAST_KNOW_GPS_POSITION + " " + new String(messageEvent.getData()));
                    String rawData = new String(messageEvent.getData());
                    String[] parts = rawData.split("_");
                    lastKnowCoordinates = String.format("%.2f", Double.parseDouble(parts[0])) + " / " + String.format("%.2f", Double.parseDouble(parts[1]));
                    lastLocationTs = System.currentTimeMillis();
                }catch (Exception e) {Log.e(TAG,"Exception",e);}
            }else {
            }
        }


        private void handleTouch(int x, int y) {
            int W = ScreenUtils.getScreenWidth(getApplicationContext());
            int H = ScreenUtils.getScreenHeight(getApplicationContext());
            int DELTA_X = (int)CR;
            int DELTA_Y = (int)CR;

            float LX = W*0.3530f;
            float LY = H*0.6220f;

            float RX = W*0.6420f;
            float RY = H*0.6220f;
            //LEFT CENTER
            /*
            if(x <(W/4 + DELTA_X) && x >(W/4 - DELTA_X)) {
                if(y <(H/2 + DELTA_Y) && y >(H/2 - DELTA_Y)) {
                    handleTouchLeftCenter();
                    return;
                }
            }
            */
            //RIGHT CENTER
            /*
            if(x <(W*3/4 + DELTA_X) && x >(W*3/4 - DELTA_X)) {
                if(y <(H/2 + DELTA_Y) && y >(H/2 - DELTA_Y)) {
                    handleTouchRightCenter();
                    return;
                }
            }*/
            //BOTTOM CENTER
            /*
            if(x <(W/2 + DELTA_X) && x >(W/2 - DELTA_X)) {
                if(y <(H*3/4 + DELTA_Y) && y >(H*3/4 - DELTA_Y)) {
                    handleTouchBottomCenter();
                    return;
                }
            }
            */
            //LEFT BOTTOM
            if(x <(LX + DELTA_X) && x >(LX - DELTA_X)) {
                if(y >(LY -DELTA_Y) && y <(LY +DELTA_Y)) {
                    handleTouchLeftBottom();
                    return;
                }
            }
            //RIGHT BOTTOM
            if(x <(RX + DELTA_X) && x >(RX- DELTA_X)) {
                if(y >(RY -DELTA_Y) && y <(RY +DELTA_Y)) {
                    handleTouchRightBottom();
                    return;
                }
            }

            handleTouchOther();

        }

        private void handleTouchOther() {
            if(NIGHT_MODE == NIGHT_MODE_ON) {
                NIGHT_MODE = NIGHT_MODE_OFF;

            }else {
                NIGHT_MODE = NIGHT_MODE_ON;
            }
            updateBackground();
        }

        private void updateBackground() {
            bg.recycle();
            if(!mAmbient) {
                if(NIGHT_MODE == NIGHT_MODE_ON) {
                    if(mIsRound) bg = BitmapFactory.decodeResource(getResources(), R.drawable.background_night);
                    else bg = BitmapFactory.decodeResource(getResources(), R.drawable.background_square_night);
                }else {
                    if(mIsRound) bg = BitmapFactory.decodeResource(getResources(), R.drawable.background);
                    else bg = BitmapFactory.decodeResource(getResources(), R.drawable.background_square);
                }
            }else {
                if(NIGHT_MODE == NIGHT_MODE_ON) {
                    if(mIsRound) bg = BitmapFactory.decodeResource(getResources(), R.drawable.background_ambient_night);
                    else bg = BitmapFactory.decodeResource(getResources(), R.drawable.background_square_night_ambient);
                }else {
                    if(mIsRound) bg = BitmapFactory.decodeResource(getResources(), R.drawable.background_ambient);
                    else bg = BitmapFactory.decodeResource(getResources(), R.drawable.background_square_ambient);
                }
            }
            updatePaintColors();
        }

        private void updatePaintColors() {
            if(NIGHT_MODE == NIGHT_MODE_OFF) {
                //accent colors
                if(mAmbient) {
                    accentFillPaint.setColor(Color.WHITE);
                }else {
                    accentFillPaint.setColor(GradientsUtils.getGradients(getApplicationContext(), selectedColorCode));
                }
                chronoPaint.setColor(GradientsUtils.getGradients(getApplicationContext(), selectedColorCode));
                complicationArcAccentPaint.setColor(GradientsUtils.getGradients(getApplicationContext(), selectedColorCode));
                complicationArcBatteryPaint.setColor(GradientsUtils.getGradients(getApplicationContext(), selectedColorCode));
                //white and gray colors
                normalTextPaint.setColor(getResources().getColor(R.color.complications_gray));
                smallTextPaint.setColor(getResources().getColor(R.color.complications_gray));
                mediumTextPaint.setColor(getResources().getColor(R.color.complications_gray));
                largeTextPaint.setColor(getResources().getColor(R.color.complications_gray));
                logoTextPaint.setColor(Color.WHITE);
                mSecondsCirclePaint.setColor(Color.WHITE);
                mDarkSecondsCirclePaint.setColor(Color.WHITE);
                whiteFillPaint.setColor(Color.WHITE);
                darkGrayFillPaint.setColor(Color.DKGRAY);
            }else {
                //accent
                int nightColor = getResources().getColor(R.color.night_mode);
                accentFillPaint.setColor(Color.DKGRAY);
                chronoPaint.setColor(nightColor);
                complicationArcAccentPaint.setColor(Color.DKGRAY);
                complicationArcBatteryPaint.setColor(Color.DKGRAY);
                //white and gray colors
                normalTextPaint.setColor(nightColor);
                smallTextPaint.setColor(nightColor);
                mediumTextPaint.setColor(nightColor);
                largeTextPaint.setColor(nightColor);
                logoTextPaint.setColor(nightColor);
                mSecondsCirclePaint.setColor(nightColor);
                mDarkSecondsCirclePaint.setColor(nightColor);
                whiteFillPaint.setColor(nightColor);
                //darkGrayFillPaint.setColor(nightColor);
            }

            //Ambient mode
            if(mAmbient) {
                mSecondsCirclePaint.setShadowLayer(0, 0, 0, Color.BLACK);
            }else {
                mSecondsCirclePaint.setShadowLayer(2, 1, 1, Color.BLACK);
            }
        }

        private void handleTouchBottomCenter() {

        }

        private void handleTouchRightBottom() {
            if(RIGHT_COMPLICATION_MODE == CHRONO || LEFT_COMPLICATION_MODE == CHRONO) {
                    stopWatch.stop();
                    INTERACTIVE_UPDATE_RATE_MS = INTERACTIVE_UPDATE_RATE_MS_NORMAL;
                    LEFT_COMPLICATION_MODE =MOON;
                    updateTimer();
            }
            else if(RIGHT_COMPLICATION_MODE == MOON) RIGHT_COMPLICATION_MODE =WEEK_DAYS_BATTERY;
            else if(RIGHT_COMPLICATION_MODE == WEEK_DAYS_BATTERY) RIGHT_COMPLICATION_MODE =COORDINATES;
            else if(RIGHT_COMPLICATION_MODE == COORDINATES) RIGHT_COMPLICATION_MODE =MONTH_AND_DAY;
            else if(RIGHT_COMPLICATION_MODE == MONTH_AND_DAY) RIGHT_COMPLICATION_MODE =MONTH_AND_YEAR;
            else if(RIGHT_COMPLICATION_MODE == MONTH_AND_YEAR) RIGHT_COMPLICATION_MODE =WEAR_BATTERY;
            else if(RIGHT_COMPLICATION_MODE == WEAR_BATTERY) RIGHT_COMPLICATION_MODE =MOON;
        }

        private void handleTouchLeftBottom() {
            if(LEFT_COMPLICATION_MODE == MOON) LEFT_COMPLICATION_MODE =WEEK_DAYS_BATTERY;
            else if(LEFT_COMPLICATION_MODE == WEEK_DAYS_BATTERY) LEFT_COMPLICATION_MODE =COORDINATES;
            else if(LEFT_COMPLICATION_MODE == COORDINATES) LEFT_COMPLICATION_MODE =MONTH_AND_DAY;
            else if(LEFT_COMPLICATION_MODE == MONTH_AND_DAY) LEFT_COMPLICATION_MODE =MONTH_AND_YEAR;
            else if(LEFT_COMPLICATION_MODE == MONTH_AND_YEAR) LEFT_COMPLICATION_MODE =WEAR_BATTERY;
            else if(LEFT_COMPLICATION_MODE == WEAR_BATTERY) LEFT_COMPLICATION_MODE =CHRONO;
            else if(LEFT_COMPLICATION_MODE == CHRONO) {

                String chrono;
                int millis;
                if(stopWatch.paused) {
                    chrono = lastStopWatchValue;
                    millis = lastMillisValue;
                }else {
                    chrono = stopWatch.toString();
                    millis = (int)stopWatch.getElapsedTimeMillis();
                    lastStopWatchValue = chrono;
                    lastMillisValue = millis;
                    lastStopWatchSecondsValue = (int)stopWatch.getElapsedTimeSecs();
                }

                if(stopWatch.running) {
                    stopWatch.pause();
                    INTERACTIVE_UPDATE_RATE_MS = INTERACTIVE_UPDATE_RATE_MS_NORMAL;
                    updateTimer();
                }else if(stopWatch.paused) {
                    stopWatch.resume();
                    INTERACTIVE_UPDATE_RATE_MS = INTERACTIVE_UPDATE_RATE_MS_STOPWATCH;
                    updateTimer();
                }
                else {
                    stopWatch.start();
                    INTERACTIVE_UPDATE_RATE_MS = INTERACTIVE_UPDATE_RATE_MS_STOPWATCH;
                    updateTimer();
                }
            }
        }

    }

    private static class EngineHandler extends Handler {
        private final WeakReference<F35Face.Engine> mWeakReference;

        public EngineHandler(F35Face.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            F35Face.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private String getYear() {
        return new SimpleDateFormat("yyyy").format(Calendar.getInstance().getTime()).toUpperCase();
    }

    private String getMonth() {
        return new SimpleDateFormat("MMM").format(Calendar.getInstance().getTime()).toUpperCase();
    }

    private String getMonthExtended() {
        return new SimpleDateFormat("MMMM").format(Calendar.getInstance().getTime()).toUpperCase();
    }

    private String getDayNumber() {
        return new SimpleDateFormat("d").format(Calendar.getInstance().getTime()).toUpperCase();
    }

    private String getWeekDay() {
        return new SimpleDateFormat("EEE").format(Calendar.getInstance().getTime()).toUpperCase();
    }

    public int getBatteryLevel() {
        Intent batteryIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        // Error checking that probably isn't needed but I added just in case.
        if(level == -1 || scale == -1) {
            return 50;
        }

        return (int)(((float)level / (float)scale) * 100.0f);
    }
}
