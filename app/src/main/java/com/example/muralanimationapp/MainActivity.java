package com.example.muralanimationapp;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.AugmentedImage;
import com.google.ar.core.AugmentedImageDatabase;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.Coordinates2d;
import com.google.ar.core.exceptions.UnavailableException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Collection;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MainActivity extends AppCompatActivity implements GLSurfaceView.Renderer {

    private static final String TAG = "MuralApp";
    private GLSurfaceView surfaceView;
    private Session session;
    private boolean videoLaunched = false;
    private String lastDetectedMural = "";

    // Camera background rendering
    private int cameraTextureId = -1;
    private int cameraProgram;
    private int positionHandle;
    private int texCoordHandle;
    private int textureUniformHandle;
    private FloatBuffer quadCoords;
    private FloatBuffer quadTexCoords;

    // Full-screen quad for camera preview
    private static final float[] QUAD_COORDS = {
            -1.0f, -1.0f,
            1.0f, -1.0f,
            -1.0f,  1.0f,
            1.0f,  1.0f,
    };

    private static final float[] QUAD_TEXCOORDS = {
            0.0f, 1.0f,
            1.0f, 1.0f,
            0.0f, 0.0f,
            1.0f, 0.0f,
    };

    // Shaders that draw camera feed to screen
    private static final String CAMERA_VERTEX_SHADER =
            "attribute vec4 a_Position;\n" +
                    "attribute vec2 a_TexCoord;\n" +
                    "varying vec2 v_TexCoord;\n" +
                    "void main() {\n" +
                    "  gl_Position = a_Position;\n" +
                    "  v_TexCoord = a_TexCoord;\n" +
                    "}";

    private static final String CAMERA_FRAGMENT_SHADER =
            "#extension GL_OES_EGL_image_external : require\n" +
                    "precision mediump float;\n" +
                    "varying vec2 v_TexCoord;\n" +
                    "uniform samplerExternalOES sTexture;\n" +
                    "void main() {\n" +
                    "  gl_FragColor = texture2D(sTexture, v_TexCoord);\n" +
                    "}";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        surfaceView = findViewById(R.id.surfaceView);
        surfaceView.setPreserveEGLContextOnPause(true);
        surfaceView.setEGLContextClientVersion(2);
        surfaceView.setRenderer(this);
        surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        requestCameraPermission();
    }

    private void requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.CAMERA}, 0);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            onResume();
        } else {
            Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Delay reset to prevent immediately re-triggering same mural
        surfaceView.postDelayed(() -> videoLaunched = false, 3000);

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission();
            return;
        }

        try {
            if (session == null) {
                ArCoreApk.Availability availability = ArCoreApk.getInstance()
                        .checkAvailability(this);
                if (availability.isTransient()) return;
                if (!availability.isSupported()) {
                    Toast.makeText(this, "ARCore not supported", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                session = new Session(this);
                setupAugmentedImageDatabase();
            }
            session.resume();
            surfaceView.onResume();
        } catch (UnavailableException |
                 com.google.ar.core.exceptions.CameraNotAvailableException e) {
            Log.e(TAG, "ARCore unavailable", e);
        }
    }

    private void setupAugmentedImageDatabase() {
        Config config = new Config(session);
        config.setFocusMode(Config.FocusMode.AUTO);

        try {
            AssetManager assetManager = getAssets();
            AugmentedImageDatabase imageDatabase = new AugmentedImageDatabase(session);

            for (int i = 1; i <= 9; i++) {
                try {
                    InputStream inputStream = assetManager.open("ar/" + i + ".jpg");
                    Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                    imageDatabase.addImage("mural_" + i, bitmap, 1.0f);
                    Log.d(TAG, "Successfully loaded mural_" + i);
                } catch (Exception e) {
                    Log.w(TAG, "Skipping mural_" + i + " - insufficient quality or missing: " + e.getMessage());
                }
            }

            config.setAugmentedImageDatabase(imageDatabase);
            Log.d(TAG, "Image database setup complete");
        } catch (Exception e) {
            Log.e(TAG, "Failed to set up image database", e);
        }

        session.configure(config);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (session != null) {
            surfaceView.onPause();
            session.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (session != null) {
            session.close();
            session = null;
        }
    }

    // ---- OpenGL setup ----

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0f, 0f, 0f, 1f);

        // Create camera texture
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        cameraTextureId = textures[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        // Compile shaders
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, CAMERA_VERTEX_SHADER);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, CAMERA_FRAGMENT_SHADER);
        cameraProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(cameraProgram, vertexShader);
        GLES20.glAttachShader(cameraProgram, fragmentShader);
        GLES20.glLinkProgram(cameraProgram);

        positionHandle = GLES20.glGetAttribLocation(cameraProgram, "a_Position");
        texCoordHandle = GLES20.glGetAttribLocation(cameraProgram, "a_TexCoord");
        textureUniformHandle = GLES20.glGetUniformLocation(cameraProgram, "sTexture");

        // Set up quad buffers
        quadCoords = ByteBuffer.allocateDirect(QUAD_COORDS.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        quadCoords.put(QUAD_COORDS).position(0);

        quadTexCoords = ByteBuffer.allocateDirect(QUAD_TEXCOORDS.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        quadTexCoords.put(QUAD_TEXCOORDS).position(0);
    }

    private int loadShader(int type, String shaderCode) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);
        return shader;
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        if (session != null) {
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            session.setDisplayGeometry(rotation, width, height);
        }
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        if (session == null || videoLaunched) return;

        try {
            session.setCameraTextureName(cameraTextureId);
            Frame frame = session.update();

            // Correct texture coordinates for display rotation
            frame.transformCoordinates2d(
                    Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
                    quadCoords,
                    Coordinates2d.TEXTURE_NORMALIZED,
                    quadTexCoords
            );

            // Draw camera feed to screen
            drawCameraBackground();

            Camera camera = frame.getCamera();
            if (camera.getTrackingState() != TrackingState.TRACKING) return;

            // Check for detected mural
            Collection<AugmentedImage> images =
                    frame.getUpdatedTrackables(AugmentedImage.class);

            for (AugmentedImage image : images) {
                if (image.getTrackingState() == TrackingState.TRACKING
                        && image.getName().startsWith("mural_")
                        && !videoLaunched
                        && !image.getName().equals(lastDetectedMural)) {

                    String detectedName = image.getName();
                    String videoFile = detectedName.replace("mural_", "") + ".mp4";

                    Log.d(TAG, "Detected: " + detectedName + " launching: " + videoFile);

                    videoLaunched = true;
                    lastDetectedMural = detectedName;

                    runOnUiThread(() -> {
                        Intent intent = new Intent(MainActivity.this, VideoActivity.class);
                        intent.putExtra("VIDEO_FILE", videoFile);
                        startActivity(intent);
                    });
                    return;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onDrawFrame", e);
        }
    }

    private void drawCameraBackground() {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glUseProgram(cameraProgram);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId);
        GLES20.glUniform1i(textureUniformHandle, 0);
        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(positionHandle, 2,
                GLES20.GL_FLOAT, false, 0, quadCoords);
        GLES20.glEnableVertexAttribArray(texCoordHandle);
        GLES20.glVertexAttribPointer(texCoordHandle, 2,
                GLES20.GL_FLOAT, false, 0, quadTexCoords);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glDisableVertexAttribArray(texCoordHandle);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
    }
}