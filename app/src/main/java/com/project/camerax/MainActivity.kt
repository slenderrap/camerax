package com.project.camerax

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import com.project.camerax.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var gravant: Recording? = null
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (permisosAcceptats()) {
            iniciarCamera()
        } else {
            demanarPermisos()
        }

        binding.imageCaptureButton.setOnClickListener { ferFoto() }
        binding.videoCaptureButton.setOnClickListener { capturarVideo() }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun iniciarCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.FHD))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            imageCapture = ImageCapture.Builder().build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, videoCapture
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Error en la vinculació d'ús de la càmera", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun ferFoto() {
        val imageCapture = imageCapture ?: return
        val name = SimpleDateFormat(FORMAT_NOM, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }
        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Error en la captura de foto: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val msg = "Foto guardada: ${output.savedUri}"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                }
            }
        )
    }

    private fun capturarVideo() {
        val videoCapture = videoCapture ?: return

        if (gravant != null) {
            gravant?.stop()
            gravant = null
            Toast.makeText(this, "Gravació aturada", Toast.LENGTH_SHORT).show()
            return
        }

        val name = SimpleDateFormat(FORMAT_NOM, Locale.US).format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraX-Video")
        }

        val mediaStoreOutput = MediaStoreOutputOptions.Builder(
            contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        gravant = videoCapture.output.prepareRecording(this, mediaStoreOutput)
            .apply {
                if(permisosAcceptats()){
                    try{
                    withAudioEnabled()
                    }catch (e: SecurityException){
                        Log.i("ERROR","No has acceptat els permisos de audio")
                    }
                }
            }
            .start(ContextCompat.getMainExecutor(this)){ event ->
                when (event){
                    is VideoRecordEvent.Start -> {
                        Toast.makeText(this, "Gravació iniciada",Toast.LENGTH_SHORT).show()
                        binding.videoCaptureButton.text = "Atura"
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (event.hasError()){
                            Toast.makeText(this, "S'ha produit un error",Toast.LENGTH_SHORT).show()
                        }else {
                            Toast.makeText(this, "Gravació finalitzada", Toast.LENGTH_SHORT).show()
                            binding.videoCaptureButton.text = "Grava"
                            Toast.makeText(
                                this, "Video guardat a: ${event.outputResults.outputUri}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        gravant=null
                    }
                }
            }


    }
    private fun demanarPermisos() {
        activityResultLauncher.launch(PERMISOS_REQUERITS)
    }

    private fun permisosAcceptats() = PERMISOS_REQUERITS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraX"
        private const val FORMAT_NOM = "yyyy-MM-dd-HH-mm-ss-SSS"
        private val PERMISOS_REQUERITS = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        ).apply {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
    }

    private val activityResultLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permisos ->
            if (permisos.all { it.value }) {
                iniciarCamera()
            } else {
                Toast.makeText(baseContext, "Permís denegat", Toast.LENGTH_SHORT).show()
            }
        }
}
