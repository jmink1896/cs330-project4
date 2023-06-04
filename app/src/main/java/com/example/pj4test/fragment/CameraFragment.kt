/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.pj4test.fragment

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview

// proj4: start import
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.VideoRecordEvent
import java.text.SimpleDateFormat
import java.util.Locale
import android.content.ContentValues
import android.provider.MediaStore
import android.os.Build
import android.hardware.camera2.CameraCharacteristics
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.camera.camera2.interop.Camera2CameraInfo
// proj4: finish import


import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.pj4test.MainActivity
import com.example.pj4test.ProjectConfiguration
import com.example.pj4test.audioInference.SnapClassifier
import java.util.LinkedList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import com.example.pj4test.cameraInference.PersonClassifier
import com.example.pj4test.databinding.FragmentCameraBinding
import org.tensorflow.lite.task.vision.detector.Detection

class CameraFragment : Fragment(), PersonClassifier.DetectorListener {
    private val TAG = "CameraFragment"
   
    private var _fragmentCameraBinding: FragmentCameraBinding? = null

    private val fragmentCameraBinding
        get() = _fragmentCameraBinding!!

    private var toneGen1: ToneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100);

   
    private lateinit var personView: TextView

    // classifiers
    private lateinit var personClassifier: PersonClassifier
    private lateinit var bitmapBuffer: Bitmap
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null

    /** Blocking camera operations are performed using this executor */
    private lateinit var cameraExecutor: ExecutorService

    private var personTime = 0
    private var noPersonTime = 0


    fun sayHello(){
        Log.d(TAG, "hello! this is CameraFragment instance")
    }

    // main activitiy instance
    private var mainActivity: MainActivity? = null

    // camera provider
    private lateinit var cameraProvider: ProcessCameraProvider


    // proj4
     private var recording: Recording? = null
     private var videoCapture: VideoCapture<Recorder>? = null
    // end of proj4

    private val idleDetectionPeriod = 200L
    private val busyDetectionPeriod = 10L

    private var isIdle = true


     companion object {
 //        private const val TAG = "CameraXApp"
         private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"

         }

    fun setDetectionOn(on:Boolean){
        if (on){
            try {
                // A variable number of use-cases can be passed here -
                // camera provides access to CameraControl & CameraInfo
                camera = cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    imageAnalyzer
                )
                Log.d(TAG, "Use case imageAnalyzer bound to cameraProvider")
            } catch (exc: Exception) {
                Log.e(TAG, "Use case imageAnalyzer failed", exc)
            }
        }
        else {
            cameraProvider.unbind(imageAnalyzer)
        }
    }


    fun setRecordingOn(on:Boolean){
        if (on){
            try {
                // A variable number of use-cases can be passed here -
                // camera provides access to CameraControl & CameraInfo
                camera = cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    videoCapture
                )
                Log.d(TAG, "Use case imageAnalyzer bound to cameraProvider")
            } catch (exc: Exception) {
                Log.e(TAG, "Use case imageAnalyzer failed", exc)
            }
        }
        else {
            cameraProvider.unbind(videoCapture)
        }
    }

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()

        // Shut down our background executor
        cameraExecutor.shutdown()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentCameraBinding = FragmentCameraBinding.inflate(inflater, container, false)

        return fragmentCameraBinding.root
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        personClassifier = PersonClassifier()
        personClassifier.initialize(requireContext())
        personClassifier.setDetectorListener(this)

        // Initialize our background executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Wait for the views to be properly laid out
        fragmentCameraBinding.viewFinder.post {
            // Set up the camera and its use cases
            setUpCamera()
        }

        personView = fragmentCameraBinding.PersonView

        personView.text = "NOT RECORDING"
        personView.setBackgroundColor(ProjectConfiguration.idleBackgroundColor)
        personView.setTextColor(ProjectConfiguration.idleTextColor)

        mainActivity =  requireActivity() as MainActivity
        mainActivity?.sayHello()
    }

    // Initialize CameraX, and prepare to bind the camera use cases
    private fun setUpCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener(
            {
                // CameraProvider
                cameraProvider = cameraProviderFuture.get()

                // Build and bind the camera use cases
                bindCameraUseCases(cameraProvider)
            },
            ContextCompat.getMainExecutor(requireContext())
        )
    }

    // Declare and bind preview, capture and analysis use cases
    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases(cameraProvider: ProcessCameraProvider) {

        // CameraSelector - makes assumption that we're only using the back camera

        val cameraSelector =
            CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()


        // Preview. Only using the 4:3 ratio because this is the closest to our models
        preview =
            Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
                .build()
        // Attach the viewfinder's surface provider to preview use case
        preview?.setSurfaceProvider(fragmentCameraBinding.viewFinder.surfaceProvider)


        // ImageAnalysis. Using RGBA 8888 to match how our models work
        imageAnalyzer =
            ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
        // The analyzer can then be assigned to the instance
        imageAnalyzer!!.setAnalyzer(cameraExecutor) { image ->
            detectObjects(image)
            Thread.sleep(idleDetectionPeriod)
        }

        // proj4
        // Create the VideoCapture UseCase and make it available to use
        // in the other part of the application.
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HD))
            .build()
        videoCapture = VideoCapture.withOutput(recorder)

        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll()

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview
            )
            Log.d(TAG, "Use case imageAnalyzer bound to cameraProvider")
        } catch (exc: Exception) {
            Log.e(TAG, "Use case imageAnalyzer failed", exc)
        }
        setDetectionOn(true)

        mainActivity?.setAudioInference(false)

    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        imageAnalyzer?.targetRotation = fragmentCameraBinding.viewFinder.display.rotation
    }

    private fun detectObjects(image: ImageProxy) {

        if (!::bitmapBuffer.isInitialized) {
            // The image rotation and RGB image buffer are initialized only once
            // the analyzer has started running
            bitmapBuffer = Bitmap.createBitmap(
                image.width,
                image.height,
                Bitmap.Config.ARGB_8888
            )
        }
        // Copy out RGB bits to the shared bitmap buffer
        image.use { bitmapBuffer.copyPixelsFromBuffer(image.planes[0].buffer) }
        val imageRotation = image.imageInfo.rotationDegrees

        // Pass Bitmap and rotation to the object detector helper for processing and detection
        personClassifier.detect(bitmapBuffer, imageRotation)
    }


    // proj4
     fun captureVideo() {
         // check if videoCapture UseCase has been created successfully
         val videoCapture = this.videoCapture ?: return

         // if some recording is ongoing
         val curRecording = recording
         if (curRecording != null) {
             // stop current recording
             curRecording.stop()
             recording = null
             return
         }

         // create and start new recording session
         val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
             .format(System.currentTimeMillis())
         val contentValues = ContentValues().apply() {
             put(MediaStore.MediaColumns.DISPLAY_NAME, name)
             put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
             if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                 put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraX-Video")
             }
         }

         // create a camera selector to bind image analysis to life cycle
         val cameraSelector =
             CameraSelector.Builder()
                 .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                 .build()

         // create storing option in extra storage
         val mediaStoreOutputOptions = MediaStoreOutputOptions
             .Builder(requireActivity().contentResolver,
                 MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
             .setContentValues(contentValues)
             .build()

         // set the output option of VideoCapture to Recorder
         recording = videoCapture.output
             .prepareRecording(this.requireContext(), mediaStoreOutputOptions)
             .start(ContextCompat.getMainExecutor(this.requireContext())) {
                     recordEvent -> when(recordEvent) {
                 is VideoRecordEvent.Start -> {
                     // call the change of the text in audio fragment snapview
                     personView.text = "RECORDING SET"
                     personView.setBackgroundColor(ProjectConfiguration.activeBackgroundColor)
                     personView.setTextColor(ProjectConfiguration.activeTextColor)
                     toneGen1.startTone(ToneGenerator.TONE_CDMA_PIP,300);

                     mainActivity?.setAudioInference(true)
                 }
                 is VideoRecordEvent.Finalize -> {
                     if (!recordEvent.hasError()) {
                         val msg = "Video capture succeeded: " +
                                 "${recordEvent.outputResults.outputUri}"
                         Toast.makeText(this.context, msg, Toast.LENGTH_SHORT)
                             .show()
                         Log.d(TAG, msg)
                     } else {
                         recording?.close()
                         recording = null
                         Log.e(TAG, "Video capture ends with error: " +
                                 "${recordEvent.error}")
                     }
                    setRecordingOn(false)
                    setDetectionOn(true)
                     toneGen1.startTone(ToneGenerator.TONE_CDMA_PIP,300);

                     personView.text = "NOT RECORDING"
                     personView.setBackgroundColor(ProjectConfiguration.idleBackgroundColor)
                     personView.setTextColor(ProjectConfiguration.idleTextColor)
                 }
             }
             }

     }
    // end proj4


    // Update UI after objects have been detected. Extracts original image height/width
    // to scale and place bounding boxes properly through OverlayView
    override fun onObjectDetectionResults(
        results: MutableList<Detection>?,
        inferenceTime: Long,
        imageHeight: Int,
        imageWidth: Int
    ) {
        activity?.runOnUiThread {
            // Pass necessary information to OverlayView for drawing on the canvas
            fragmentCameraBinding.overlay.setResults(
                results ?: LinkedList<Detection>(),
                imageHeight,
                imageWidth
            )

            // find at least one bounding box of the person
            val isPersonDetected: Boolean = results!!.find { it.categories[0].label == "person" } != null


            // change UI according to the result
            if (isPersonDetected) {
                if (isIdle){
                    imageAnalyzer!!.setAnalyzer(cameraExecutor) { image ->
                        detectObjects(image)
                        Thread.sleep(busyDetectionPeriod)
                    }
                    isIdle = false
                }

                personTime += 1

                if (personTime > 20){
                    Log.d(TAG, "person entered")

                    setDetectionOn(false)
                    setRecordingOn(true)
                    // once the person enters, clear the screen
                    fragmentCameraBinding.overlay.setResults(
                        LinkedList<Detection>() ?: LinkedList<Detection>(),
                        imageHeight,
                        imageWidth
                    )
                    captureVideo()
                }
            }
            else {
                if (personTime > 0){
                    personTime -= 1
                }
                if (personTime == 0 && !isIdle){
                        imageAnalyzer!!.setAnalyzer(cameraExecutor) { image ->
                            detectObjects(image)
                            Thread.sleep(idleDetectionPeriod)
                        }
                        isIdle = true
                }


            }
//            else if (AudioFragment.state == 2 && !isPersonDetected) {
//                personTime = 0
//                noPersonTime += 1
//                if (noPersonTime > 50){
//                    Log.d(TAG, "person left")
//                    setDetectionOn(false)
//
//                    AudioFragment.state = 0
//                    personView.text = "NO PERSON"
//                    personView.setBackgroundColor(ProjectConfiguration.idleBackgroundColor)
//                    personView.setTextColor(ProjectConfiguration.idleTextColor)
//                    cameraProvider.unbind(imageAnalyzer)
//                }
//            }


            // Force a redraw
            fragmentCameraBinding.overlay.invalidate()
        }
    }

    override fun onObjectDetectionError(error: String) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
        }
    }
}
