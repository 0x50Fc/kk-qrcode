package cn.kkmofang.qrcode;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.client.android.camera.CameraManager;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Map;

import cn.kkmofang.view.Element;
import cn.kkmofang.view.ViewElement;
import cn.kkmofang.view.value.V;

/**
 * Created by zhanghailong on 2018/4/24.
 */

public class QRCaptureElement extends ViewElement {

    protected final static int MSG_FRAME = 1;

    public final static String TAG = "kk";

    public QRCaptureElement() {
        super();
        _mainHandler = new Handler();
    }

    private SurfaceView _surfaceView;
    private SurfaceHolder.Callback _callback;
    private CameraManager _cameraManager;
    private HandlerThread _thread;
    private Handler _handler;
    private final Handler _mainHandler;

    protected CameraManager cameraManager() {
        if(_cameraManager == null) {
            _cameraManager = new CameraManager(viewContext.getContext());
        }
        return _cameraManager;
    }

    public void setView(View view) {

        closeCamera();

        if(_surfaceView != null) {
            if(_callback != null ){
                _surfaceView.getHolder().removeCallback(_callback);
            }
            ViewGroup p = (ViewGroup) _surfaceView.getParent();
            if(p != null) {
                p.removeView(_surfaceView);
            }
            _surfaceView = null;
        }

        super.setView(view);

        if(view  != null && view instanceof ViewGroup) {

            _surfaceView = new SurfaceView(viewContext.getContext());

            ((ViewGroup) view).addView(_surfaceView);

            if(_callback == null) {

                final WeakReference<QRCaptureElement> e = new WeakReference<>(this);

                _callback = new SurfaceHolder.Callback() {

                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {

                        QRCaptureElement element = e.get();

                        if(element != null) {
                            element.openCamera(holder);
                        }
                    }

                    @Override
                    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

                    }

                    @Override
                    public void surfaceDestroyed(SurfaceHolder holder) {


                    }
                };
            }

            _surfaceView.getHolder().addCallback(_callback);
        }

    }

    protected void onSetProperty(View view, String key, String value) {
        super.onSetProperty(view,key,value);

        if("capture".equals(key)) {

            if(V.booleanValue(value,false)) {
                if(_cameraManager != null && _cameraManager.isOpen() && _handler != null) {
                    _cameraManager.requestPreviewFrame(_handler,MSG_FRAME);
                }
            }

        }
    }

    protected void closeCamera() {

        if(_thread != null) {
            _thread.quit();
            _thread = null;
        }

        if(_handler != null) {
            _handler = null;
        }

        if(_cameraManager!=null && _cameraManager.isOpen()) {
            _cameraManager.stopPreview();
            _cameraManager.closeDriver();
        }

        if(_surfaceView != null && _callback != null) {
            _surfaceView.getHolder().removeCallback(_callback);
        }

    }

    protected void openCamera(SurfaceHolder holder) {

        if(holder == null) {
            return;
        }

        CameraManager camera = cameraManager();

        if(! camera.isOpen()) {

            try {
                camera.openDriver(holder);
                camera.startPreview();
            } catch (IOException e) {
                Log.d(TAG,Log.getStackTraceString(e));
            } catch (RuntimeException e) {
                Toast.makeText(viewContext.getContext(),e.getMessage(),Toast.LENGTH_SHORT);
            }

        }

        if(_thread == null) {
            _thread = new HandlerThread("QRCaptureElement");
            _thread.start();
        }

        if(_handler == null) {

            final WeakReference<QRCaptureElement> e = new WeakReference<>(this);

            _handler = new Handler(_thread.getLooper(),new Handler.Callback(){
                @Override
                public boolean handleMessage(Message msg) {

                    if(msg.what == MSG_FRAME) {
                        QRCaptureElement element = e.get();
                        if(element != null) {
                            element.decode((byte[])msg.obj, msg.arg1, msg.arg2);
                        }
                        return true;
                    }
                    return false;
                }
            });
        }

        if(V.booleanValue(get("capture"),false)) {
            camera.requestPreviewFrame(_handler,MSG_FRAME);
        }

    }

    private final QRCodeReader _qrReader = new QRCodeReader();

    private void decode(byte[] data, int width, int height) {

        Result rawResult = null;
        PlanarYUVLuminanceSource source =  cameraManager().buildLuminanceSource(data, width, height);
        if(source != null) {
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            try {
                rawResult = _qrReader.decode(bitmap);
            } catch (ReaderException e) {
            } finally {
                _qrReader.reset();
            }
        }

        if(rawResult != null) {
            final String text = rawResult.getText();
            _mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    set("capture","false");
                    Element.Event event = new Element.Event(QRCaptureElement.this);
                    Map<String,Object> data = data();
                    data.put("text",text);
                    event.setData(data);
                    emit("capture",event);
                }
            });
        } else {
            _mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    if(_cameraManager != null && _handler != null) {
                        _cameraManager.requestPreviewFrame(_handler,MSG_FRAME);
                    }
                }
            });
        }
    }

    public void onPause(Activity activity) {

        closeCamera();

        super.onPause(activity);
    }

    public void onResume(Activity activity) {
        super.onResume(activity);

        if(_surfaceView != null && _callback != null) {
            _surfaceView.getHolder().addCallback(_callback);
        }

    }

}
