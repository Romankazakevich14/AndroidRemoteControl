package com.example.remotectrl;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Path;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.accessibilityservice.GestureDescription;
import android.hardware.display.VirtualDisplay;
import android.media.ImageReader;
import android.media.Image;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import java.nio.ByteBuffer;
import java.io.ByteArrayOutputStream;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import android.os.Handler;
import android.os.Looper;

public class RemoteAccessibilityService extends AccessibilityService {
    private WebSocket webSocket;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Обработка событий доступности (клики, ввод текста и т. д.)
    }

    @Override
    public void onInterrupt() {
        // Обработчик прерываний
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED | AccessibilityEvent.TYPE_VIEW_CLICKED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        setServiceInfo(info);
        Toast.makeText(this, "Сервис запущен", Toast.LENGTH_SHORT).show();
        
        initWebSocket();
        startScreenStreaming();
    }
    
    private void initWebSocket() {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url("ws://192.168.1.100:8080").build(); // Локальный сервер
        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, okhttp3.Response response) {
                webSocket.send("Device connected");
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                handleCommand(text);
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                handleCommand(bytes.utf8());
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, okhttp3.Response response) {
                Toast.makeText(getApplicationContext(), "Ошибка WebSocket", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void handleCommand(String command) {
        if (command.startsWith("click:")) {
            performClick(command.substring(6));
        } else if (command.startsWith("text:")) {
            performTextInput(command.substring(5));
        } else if (command.startsWith("swipe:")) {
            performSwipe(command.substring(6));
        } else if (command.startsWith("key:")) {
            performKeyPress(command.substring(4));
        }
    }

    private void performClick(String nodeText) {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode != null) {
            for (AccessibilityNodeInfo node : rootNode.findAccessibilityNodeInfosByText(nodeText)) {
                if (node.isClickable()) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    break;
                }
            }
        }
    }

    private void performTextInput(String text) {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode != null) {
            for (AccessibilityNodeInfo node : rootNode.findAccessibilityNodeInfosByText("")) {
                if (node.isFocusable()) {
                    node.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                    node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, null);
                    break;
                }
            }
        }
    }

    private void performSwipe(String coordinates) {
        try {
            String[] parts = coordinates.split(",");
            float startX = Float.parseFloat(parts[0]);
            float startY = Float.parseFloat(parts[1]);
            float endX = Float.parseFloat(parts[2]);
            float endY = Float.parseFloat(parts[3]);
            
            Path path = new Path();
            path.moveTo(startX, startY);
            path.lineTo(endX, endY);
            
            GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, 500);
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(stroke);
            dispatchGesture(builder.build(), null, null);
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка выполнения свайпа", Toast.LENGTH_SHORT).show();
        }
    }

    private void performKeyPress(String key) {
        int keyCode;
        switch (key.toLowerCase()) {
            case "enter":
                keyCode = KeyEvent.KEYCODE_ENTER;
                break;
            case "back":
                keyCode = KeyEvent.KEYCODE_BACK;
                break;
            case "home":
                keyCode = KeyEvent.KEYCODE_HOME;
                break;
            case "volume_up":
                keyCode = KeyEvent.KEYCODE_VOLUME_UP;
                break;
            case "volume_down":
                keyCode = KeyEvent.KEYCODE_VOLUME_DOWN;
                break;
            case "power":
                keyCode = KeyEvent.KEYCODE_POWER;
                break;
            default:
                Toast.makeText(this, "Неизвестная клавиша", Toast.LENGTH_SHORT).show();
                return;
        }
        
        performGlobalAction(keyCode);
    }
    
    private void startScreenStreaming() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                sendScreenshot();
                handler.postDelayed(this, 500); // Обновление раз в 500 мс
            }
        }, 500);
    }

    private void sendScreenshot() {
        if (imageReader == null) return;
        Image image = imageReader.acquireLatestImage();
        if (image != null) {
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            Bitmap bitmap = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(buffer);
            image.close();
            
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 50, stream);
            byte[] byteArray = stream.toByteArray();
            
            webSocket.send(ByteString.of(byteArray));
        }
    }
}