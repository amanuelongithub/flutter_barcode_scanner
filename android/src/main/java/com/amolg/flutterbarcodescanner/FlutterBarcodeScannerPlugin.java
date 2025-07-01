package com.amolg.flutterbarcodescanner;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.vision.barcode.Barcode;

import java.util.Map;

import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.EventChannel.StreamHandler;
import io.flutter.embedding.engine.plugins.lifecycle.FlutterLifecycleAdapter;

public class FlutterBarcodeScannerPlugin implements MethodCallHandler, PluginRegistry.ActivityResultListener, StreamHandler, FlutterPlugin, ActivityAware {
    private static final String CHANNEL = "flutter_barcode_scanner";

    private FlutterActivity activity;
    private Result pendingResult;
    private Map<String, Object> arguments;

    private static final String TAG = FlutterBarcodeScannerPlugin.class.getSimpleName();
    private static final int RC_BARCODE_CAPTURE = 9001;
    public static String lineColor = "";
    public static boolean isShowFlashIcon = false;
    public static boolean isContinuousScan = false;
    private EventChannel.EventSink barcodeStream;
    private EventChannel eventChannel;

    private MethodChannel channel;
    private FlutterPluginBinding pluginBinding;
    private ActivityPluginBinding activityBinding;
    private Application applicationContext;
    private Lifecycle lifecycle;
    private LifeCycleObserver observer;

    private static FlutterBarcodeScannerPlugin instance;

    public static FlutterBarcodeScannerPlugin getInstance() {
        return instance;
    }

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        pluginBinding = binding;
        instance = this;
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        pluginBinding = null;
        instance = null;
    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        activityBinding = binding;
        this.activity = (FlutterActivity) binding.getActivity();
        setupPlugin(
                pluginBinding.getBinaryMessenger(),
                (Application) pluginBinding.getApplicationContext(),
                activityBinding
        );
    }

    @Override
    public void onDetachedFromActivity() {
        tearDownPlugin();
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity();
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        onAttachedToActivity(binding);
    }

    private void setupPlugin(
            BinaryMessenger messenger,
            Application applicationContext,
            ActivityPluginBinding activityBinding
    ) {
        this.activity = (FlutterActivity) activityBinding.getActivity();
        this.applicationContext = applicationContext;
        
        // Setup channels
        channel = new MethodChannel(messenger, CHANNEL);
        channel.setMethodCallHandler(this);
        
        eventChannel = new EventChannel(messenger, "flutter_barcode_scanner_receiver");
        eventChannel.setStreamHandler(this);

        // Register activity result listener
        activityBinding.addActivityResultListener(this);
        
        // Setup lifecycle observer
        lifecycle = FlutterLifecycleAdapter.getActivityLifecycle(activityBinding);
        observer = new LifeCycleObserver(activityBinding.getActivity());
        lifecycle.addObserver(observer);
    }

    private void tearDownPlugin() {
        if (activityBinding != null) {
            activityBinding.removeActivityResultListener(this);
            activityBinding = null;
        }
        
        if (lifecycle != null && observer != null) {
            lifecycle.removeObserver(observer);
            lifecycle = null;
        }
        
        if (channel != null) {
            channel.setMethodCallHandler(null);
            channel = null;
        }
        
        if (eventChannel != null) {
            eventChannel.setStreamHandler(null);
            eventChannel = null;
        }
        
        activity = null;
        applicationContext = null;
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        try {
            pendingResult = result;

            if (call.method.equals("scanBarcode")) {
                if (!(call.arguments instanceof Map)) {
                    throw new IllegalArgumentException("Plugin not passing a map as parameter: " + call.arguments);
                }
                arguments = (Map<String, Object>) call.arguments;
                lineColor = (String) arguments.get("lineColor");
                isShowFlashIcon = (boolean) arguments.get("isShowFlashIcon");
                if (null == lineColor || lineColor.equalsIgnoreCase("")) {
                    lineColor = "#DC143C";
                }
                if (null != arguments.get("scanMode")) {
                    if ((int) arguments.get("scanMode") == BarcodeCaptureActivity.SCAN_MODE_ENUM.DEFAULT.ordinal()) {
                        BarcodeCaptureActivity.SCAN_MODE = BarcodeCaptureActivity.SCAN_MODE_ENUM.QR.ordinal();
                    } else {
                        BarcodeCaptureActivity.SCAN_MODE = (int) arguments.get("scanMode");
                    }
                } else {
                    BarcodeCaptureActivity.SCAN_MODE = BarcodeCaptureActivity.SCAN_MODE_ENUM.QR.ordinal();
                }

                isContinuousScan = (boolean) arguments.get("isContinuousScan");

                startBarcodeScannerActivityView((String) arguments.get("cancelButtonText"), isContinuousScan);
            }
        } catch (Exception e) {
            Log.e(TAG, "onMethodCall: " + e.getLocalizedMessage());
        }
    }

    private void startBarcodeScannerActivityView(String buttonText, boolean isContinuousScan) {
        try {
            Intent intent = new Intent(activity, BarcodeCaptureActivity.class).putExtra("cancelButtonText", buttonText);
            if (isContinuousScan) {
                activity.startActivity(intent);
            } else {
                activity.startActivityForResult(intent, RC_BARCODE_CAPTURE);
            }
        } catch (Exception e) {
            Log.e(TAG, "startView: " + e.getLocalizedMessage());
        }
    }

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RC_BARCODE_CAPTURE) {
            if (resultCode == CommonStatusCodes.SUCCESS) {
                if (data != null) {
                    try {
                        Barcode barcode = data.getParcelableExtra(BarcodeCaptureActivity.BarcodeObject);
                        String barcodeResult = barcode.rawValue;
                        pendingResult.success(barcodeResult);
                    } catch (Exception e) {
                        pendingResult.success("-1");
                    }
                } else {
                    pendingResult.success("-1");
                }
                pendingResult = null;
                arguments = null;
                return true;
            } else {
                pendingResult.success("-1");
            }
        }
        return false;
    }

    @Override
    public void onListen(Object o, EventChannel.EventSink eventSink) {
        barcodeStream = eventSink;
    }

    @Override
    public void onCancel(Object o) {
        barcodeStream = null;
    }

    public void onBarcodeScanReceived(final Barcode barcode) {
        try {
            if (barcode != null && !barcode.displayValue.isEmpty() && 
                activity != null && barcodeStream != null) {
                activity.runOnUiThread(() -> barcodeStream.success(barcode.rawValue));
            }
        } catch (Exception e) {
            Log.e(TAG, "onBarcodeScanReceived: " + e.getLocalizedMessage());
        }
    }

    private static class LifeCycleObserver implements DefaultLifecycleObserver {
        private final Activity activity;

        LifeCycleObserver(Activity activity) {
            this.activity = activity;
        }

        @Override
        public void onDestroy(@NonNull LifecycleOwner owner) {
            // Clean up if needed
        }
    }
}