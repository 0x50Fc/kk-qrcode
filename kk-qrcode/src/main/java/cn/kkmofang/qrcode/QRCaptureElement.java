package cn.kkmofang.qrcode;
import android.app.Activity;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

import cn.kkmofang.view.Element;
import cn.kkmofang.view.ViewElement;
import cn.kkmofang.view.value.V;

/**
 * Created by zhanghailong on 2018/4/24.
 */

public class QRCaptureElement extends ViewElement {

    public final static String TAG = "kk";

    public QRCaptureElement() {
        super();

    }

    private Camera _camera;
    private SurfaceView _surfaceView;
    private SurfaceHolder.Callback _callback;
    private Camera.PreviewCallback _previewCallback;

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

                if(_camera != null) {
                    _camera.startPreview();
                }
            }

        }
    }

    protected void closeCamera() {


        if(_camera != null) {
            _camera.setOneShotPreviewCallback(null);
            _camera.release();
            _camera = null;
        }

        if(_surfaceView != null && _callback != null) {
            _surfaceView.getHolder().removeCallback(_callback);
        }

    }

    protected void onPreviewFrame(byte[] data, Camera camera) {

        Camera.Parameters parameters = camera.getParameters();
        Camera.Size size = parameters.getPreviewSize();

        Result rawResult = null;
        int height = size.height;
        int width = size.width;

        byte[] rotatedData = new byte[data.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++)
                rotatedData[x * height + height - y - 1] = data[x + y * width];
        }
        int tmp = width;
        width = height;
        height = tmp;

        PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(rotatedData, width, height, 0, 0, width, height, false);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
        MultiFormatReader multiFormatReader = new MultiFormatReader();
        Map<DecodeHintType, Object> hints = new HashMap<>();
        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
        multiFormatReader.setHints(hints);
        try {
            rawResult = multiFormatReader.decodeWithState(bitmap);
        } catch (ReaderException re) {
            re.printStackTrace();
        } finally {
            multiFormatReader.reset();
        }

        if (rawResult != null){
            Element.Event event = new Element.Event(this);
            Map<String,Object> v = this.data();
            v.put("text",rawResult.getText());
            event.setData(v);
            emit("capture",event);
        }else {
            camera.setOneShotPreviewCallback(_previewCallback);
            camera.startPreview();
        }

    }

    protected void openCamera(SurfaceHolder holder) {

        if(_camera == null) {
            try {
                _camera = Camera.open();
                if (_camera == null){
                    throw new IOException();
                }
                Camera.Parameters _parameters = _camera.getParameters();
                _parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                _camera.setParameters(_parameters);

            } catch(Throwable e) {
                Log.d(TAG,Log.getStackTraceString(e));
            }
        }

        if(_camera != null) {

            if(_previewCallback == null) {

                final  WeakReference<QRCaptureElement> e = new WeakReference<>(this);
                _previewCallback = new Camera.PreviewCallback() {
                    @Override
                    public void onPreviewFrame(byte[] data, Camera camera) {


                        QRCaptureElement element = e.get();

                        if(element != null) {
                            element.onPreviewFrame(data,camera);
                        }

                    }
                };
            }

            _camera.setOneShotPreviewCallback(_previewCallback);

            _camera.setDisplayOrientation(90);

            try {
                _camera.setPreviewDisplay(holder);
            } catch (IOException ex) {
                Log.d(TAG,Log.getStackTraceString(ex));
            }

            _camera.startPreview();
            _camera.cancelAutoFocus();

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
