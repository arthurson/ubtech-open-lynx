package com.open.lynx;

import android.app.Activity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.ubtechinc.alpha.serverlibutil.aidl.ActionInfo;
import com.ubtechinc.alpha.serverlibutil.aidl.AlarmInfo;
import com.ubtechinc.alpha.serverlibutil.aidl.IActionListResultListener;
import com.ubtechinc.alpha.serverlibutil.aidl.IActionResultListener;
import com.ubtechinc.alpha.serverlibutil.aidl.IMotorListResultListener;
import com.ubtechinc.alpha.serverlibutil.aidl.IMotorMoveAngleResultListener;
import com.ubtechinc.alpha.serverlibutil.aidl.IMotorReadAngleListener;
import com.ubtechinc.alpha.serverlibutil.aidl.IPcmListener;
import com.ubtechinc.alpha.serverlibutil.aidl.IRemoteLedListResultListener;
import com.ubtechinc.alpha.serverlibutil.aidl.IRemoteLedOperationResultListener;
import com.ubtechinc.alpha.serverlibutil.aidl.IRemotePIRSensorOperationResultListener;
import com.ubtechinc.alpha.serverlibutil.aidl.ISpeechAsrListener;
import com.ubtechinc.alpha.serverlibutil.aidl.LedInfo;
import com.ubtechinc.alpha.serverlibutil.aidl.MotorInfo;
import com.ubtechinc.alpha.serverlibutil.aidl.SpeechVoice;
import com.ubtechinc.alpha2robot.Alpha2RobotApi;
import com.ubtechinc.alpha2robot.constant.UbxErrorCode;

import java.util.List;

/**
 * Minimal test panel for the rebuilt Alpha2 SDK
 * ({@code com.ubtechinc.alpha.serverlibutil.aidl}, confirmed against
 * com.ubtechinc.alpha2services_base.3.002.apk).
 *
 * <p>One button per AIDL call, grouped by subsystem, with a shared scrolling log at the
 * bottom showing every request/callback. This intentionally does not replicate the old
 * test panel's HTTP/WebSocket server, camera, or audio recording - it exists purely to
 * exercise every method on {@link Alpha2RobotApi} from the device screen.
 */
public class MainActivity extends Activity {

    private static final String TAG = "OpenLynx";

    private Alpha2RobotApi robot;
    private TextView logView;
    private EditText motorIdInput;
    private EditText motorAngleInput;
    private EditText actionNameInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        robot = new Alpha2RobotApi(getApplicationContext());

        ScrollView root = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(12);
        content.setPadding(pad, pad, pad, pad);
        root.addView(content);
        setContentView(root);

        content.addView(sectionLabel("Action ('action')"));
        actionNameInput = addLabeledInput(content, "Action name", "wave");
        addRow(content,
                button("Get action list", v -> onGetActionList()),
                button("Play action", v -> onPlayAction()),
                button("Stop action", v -> onStopAction()));

        content.addView(sectionLabel("Motor ('motor')"));
        motorIdInput = addLabeledInput(content, "Motor id", "1");
        motorAngleInput = addLabeledInput(content, "Target angle", "90");
        addRow(content,
                button("Get motor list", v -> onGetMotorList()),
                button("Move to angle", v -> onMoveToAbsoluteAngle()),
                button("Read angle", v -> onReadAbsoluteAngle()));

        content.addView(sectionLabel("LED ('led')"));
        addRow(content,
                button("Get LED list", v -> onGetLedList()),
                button("Eye on (red)", v -> onEyeOn()),
                button("Eye off", v -> onEyeOff()));

        content.addView(sectionLabel("Speech ('speech')"));
        addRow(content,
                button("Start ASR", v -> onStartSpeechAsr()),
                button("Get voices", v -> onGetSpeechVoices()));

        content.addView(sectionLabel("Sys ('sysinfo')"));
        addRow(content,
                button("Get SID", v -> onGetSid()),
                button("Battery version", v -> onGetBatteryVersion()),
                button("Power value", v -> onGetPowerValue()),
                button("PIR on", v -> onSetPirSensor(true)));

        content.addView(sectionLabel("Log"));
        logView = new TextView(this);
        logView.setTextIsSelectable(true);
        logView.setMovementMethod(new ScrollingMovementMethod());
        logView.setMinHeight(dp(200));
        content.addView(logView);
    }

    // ------------------------------------------------------------------
    // Action
    // ------------------------------------------------------------------

    private void onGetActionList() {
        UbxErrorCode.API_ERROR_CODE result = robot.action_getActionList(new IActionListResultListener.Stub() {
            @Override
            public void onGetActionList(int code, int total, ActionInfo[] actions) {
                StringBuilder sb = new StringBuilder("getActionList code=" + code + " total=" + total);
                if (actions != null) {
                    for (ActionInfo a : actions) {
                        sb.append("\n  - ").append(a.getId()).append(" / ").append(a.getName());
                    }
                }
                log(sb.toString());
            }
        });
        log("action_getActionList -> " + result);
    }

    private void onPlayAction() {
        String name = actionNameInput.getText().toString();
        UbxErrorCode.API_ERROR_CODE result = robot.action_playAction(name, new IActionResultListener.Stub() {
            @Override
            public void onPlayActionResult(int code, int progress) {
                log("onPlayActionResult code=" + code + " progress=" + progress);
            }

            @Override
            public void onStopActionResult(int code) {
                log("onStopActionResult (via play listener) code=" + code);
            }
        });
        log("action_playAction(" + name + ") -> " + result);
    }

    private void onStopAction() {
        UbxErrorCode.API_ERROR_CODE result = robot.action_stopAction(new IActionResultListener.Stub() {
            @Override
            public void onPlayActionResult(int code, int progress) {
                log("onPlayActionResult (via stop listener) code=" + code + " progress=" + progress);
            }

            @Override
            public void onStopActionResult(int code) {
                log("onStopActionResult code=" + code);
            }
        });
        log("action_stopAction -> " + result);
    }

    // ------------------------------------------------------------------
    // Motor
    // ------------------------------------------------------------------

    private void onGetMotorList() {
        UbxErrorCode.API_ERROR_CODE result = robot.motor_getMotorList(new IMotorListResultListener.Stub() {
            @Override
            public void onGetMotorList(int code, int total, MotorInfo[] motors) {
                StringBuilder sb = new StringBuilder("getMotorList code=" + code + " total=" + total);
                if (motors != null) {
                    for (MotorInfo m : motors) {
                        sb.append("\n  - id=").append(m.getId())
                                .append(" range=[").append(m.getLowerLimitAngle())
                                .append(",").append(m.getUpperLimitAngle()).append("]");
                    }
                }
                log(sb.toString());
            }
        });
        log("motor_getMotorList -> " + result);
    }

    private void onMoveToAbsoluteAngle() {
        int id = parseIntOr(motorIdInput, 1);
        int angle = parseIntOr(motorAngleInput, 90);
        UbxErrorCode.API_ERROR_CODE result = robot.motor_moveToAbsoluteAngle(id, angle, 1000L, new IMotorMoveAngleResultListener.Stub() {
            @Override
            public void onMoveAngle(int motorId, int finalAngle, int code) {
                log("onMoveAngle id=" + motorId + " angle=" + finalAngle + " code=" + code);
            }
        });
        log("motor_moveToAbsoluteAngle(id=" + id + ", angle=" + angle + ") -> " + result);
    }

    private void onReadAbsoluteAngle() {
        int id = parseIntOr(motorIdInput, 1);
        UbxErrorCode.API_ERROR_CODE result = robot.motor_readAbsoluteAngle(id, true, new IMotorReadAngleListener.Stub() {
            @Override
            public void onReadMotorAngle(int motorId, int angle, int code) {
                log("onReadMotorAngle id=" + motorId + " angle=" + angle + " code=" + code);
            }
        });
        log("motor_readAbsoluteAngle(id=" + id + ") -> " + result);
    }

    // ------------------------------------------------------------------
    // LED
    // ------------------------------------------------------------------

    private void onGetLedList() {
        UbxErrorCode.API_ERROR_CODE result = robot.led_getLedList(new IRemoteLedListResultListener.Stub() {
            @Override
            public void onGetLedList(int code, int total, List<LedInfo> leds) {
                StringBuilder sb = new StringBuilder("getLedList code=" + code + " total=" + total);
                if (leds != null) {
                    for (LedInfo l : leds) {
                        sb.append("\n  - ").append(l.toString());
                    }
                }
                log(sb.toString());
            }
        });
        log("led_getLedList -> " + result);
    }

    private void onEyeOn() {
        UbxErrorCode.API_ERROR_CODE result = robot.led_turnOnEye(1, new IRemoteLedOperationResultListener.Stub() {
            @Override
            public void onLedOpResult(int code, int extra) {
                log("onLedOpResult (eye on) code=" + code + " extra=" + extra);
            }
        });
        log("led_turnOnEye(RED) -> " + result);
    }

    private void onEyeOff() {
        UbxErrorCode.API_ERROR_CODE result = robot.led_turnOffEye(new IRemoteLedOperationResultListener.Stub() {
            @Override
            public void onLedOpResult(int code, int extra) {
                log("onLedOpResult (eye off) code=" + code + " extra=" + extra);
            }
        });
        log("led_turnOffEye -> " + result);
    }

    // ------------------------------------------------------------------
    // Speech
    // ------------------------------------------------------------------

    private void onStartSpeechAsr() {
        UbxErrorCode.API_ERROR_CODE result = robot.speech_startSpeechAsr("testpanel", 0, new ISpeechAsrListener.Stub() {
            @Override
            public void onBegin() {
                log("speech asr onBegin");
            }

            @Override
            public void onEnd() {
                log("speech asr onEnd");
            }

            @Override
            public void onResult(String text) {
                log("speech asr onResult: " + text);
            }

            @Override
            public void onError(int code) {
                log("speech asr onError code=" + code);
            }
        });
        log("speech_startSpeechAsr -> " + result);
    }

    private void onGetSpeechVoices() {
        List<SpeechVoice> voices = robot.speech_getSpeechVoices();
        if (voices == null) {
            log("speech_getSpeechVoices -> null (service not ready)");
            return;
        }
        StringBuilder sb = new StringBuilder("speech_getSpeechVoices -> " + voices.size() + " voices");
        for (SpeechVoice v : voices) {
            sb.append("\n  - ").append(v.toString());
        }
        log(sb.toString());
    }

    // ------------------------------------------------------------------
    // Sys
    // ------------------------------------------------------------------

    private void onGetSid() {
        log("sys_getSid -> " + robot.sys_getSid());
    }

    private void onGetBatteryVersion() {
        log("sys_getBatteryVersion -> " + robot.sys_getBatteryVersion());
    }

    private void onGetPowerValue() {
        log("sys_getPowerValue -> " + robot.sys_getPowerValue());
    }

    private void onSetPirSensor(boolean enabled) {
        UbxErrorCode.API_ERROR_CODE result = robot.sys_setPIRSensor(enabled, new IRemotePIRSensorOperationResultListener.Stub() {
            @Override
            public void onPIRSensorOpResult(int code) {
                log("onPIRSensorOpResult code=" + code);
            }
        });
        log("sys_setPIRSensor(" + enabled + ") -> " + result);
    }

    // ------------------------------------------------------------------
    // UI helpers
    // ------------------------------------------------------------------

    private void log(final String message) {
        Log.i(TAG, message);
        runOnUiThread(() -> {
            if (logView != null) {
                logView.append(message + "\n\n");
            }
        });
    }

    private int parseIntOr(EditText input, int fallback) {
        try {
            return Integer.parseInt(input.getText().toString().trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    private TextView sectionLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(16);
        label.setPadding(0, dp(16), 0, dp(4));
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        return label;
    }

    private EditText addLabeledInput(LinearLayout parent, String hint, String defaultValue) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setText(defaultValue);
        parent.addView(input);
        return input;
    }

    private Button button(String text, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setOnClickListener(listener);
        return b;
    }

    private void addRow(LinearLayout parent, Button... buttons) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        for (Button b : buttons) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            row.addView(b, lp);
        }
        parent.addView(row);
    }
}
