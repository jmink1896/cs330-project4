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
//     private var recording: Recording? = null
//     private var videoCapture: VideoCapture<Recorder>? = null
//     private val activity = getActivity()
//     private var msg = ""
//     companion object {
// //        private const val TAG = "CameraXApp"
//         private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"

//         var detectionOn: Boolean = false


//     }
    // end of proj4

    private var detectionOn: Boolean = false
    private var clearScreen: Boolean = false

     companion object {
 //        private const val TAG = "CameraXApp"
         private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"

         }
    private fun isDetectionOn(): Boolean{
        return detectionOn
    }

    fun setDetectionOn(on:Boolean){
        detectionOn = on

        if (on){
            try {
                // A variable number of use-cases can be passed here -
                // camera provides access to CameraControl & CameraInfo
                camera = cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
//                    preview,
                imageAnalyzer
//                ,videoCapture
//            TODO: add recording feature here
//            https://developer.android.com/training/camerax/video-capture
                )
                Log.d(TAG, "Use case imageAnalyzer bound to cameraProvider")
            } catch (exc: Exception) {
                Log.e(TAG, "Use case imageAnalyzer failed", exc)
            }
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
                // proj 4
//                .addCameraFilter {
//                    it.filter { camInfo ->
//                        val level = Camera2CameraInfo.from(camInfo)
//                            .getCameraCharacteristic(
//                                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL
//                            )
//                        level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3
//                    }
//                }
                // end proj 4


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
        imageAnalyzer!!.setAnalyzer(cameraExecutor) { image -> detectObjects(image) }

        // proj4
        // Create the VideoCapture UseCase and make it available to use
        // in the other part of the application.
//        val recorder = Recorder.Builder()
//            .setQualitySelector(QualitySelector.from(Quality.HD))
//            .build()
//        videoCapture = VideoCapture.withOutput(recorder)

        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll()

//        try {
//            // A variable number of use-cases can be passed here -
//            // camera provides access to CameraControl & CameraInfo
//            camera = cameraProvider.bindToLifecycle(
//                this,
//                cameraSelector,
//                preview,
////                imageAnalyzer,
////                videoCapture
////            TODO: add recording feature here
////            https://developer.android.com/training/camerax/video-capture
//
//            )
//            Log.d(TAG, "Use case preview bound to cameraProvider")
//        } catch (exc: Exception) {
//            Log.e(TAG, "Use case preview failed", exc)
//        }

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
//                imageAnalyzer
//                ,videoCapture
//            TODO: add recording feature here
//            https://developer.android.com/training/camerax/video-capture
            )
            Log.d(TAG, "Use case imageAnalyzer bound to cameraProvider")
        } catch (exc: Exception) {
            Log.e(TAG, "Use case imageAnalyzer failed", exc)
        }

//        try {
//            // A variable number of use-cases can be passed here -
//            // camera provides access to CameraControl & CameraInfo
//            camera = cameraProvider.bindToLifecycle(
//                this,
//                cameraSelector,
////                preview,
////                imageAnalyzer,
//                videoCapture
////            TODO: add recording feature here
////            https://developer.android.com/training/camerax/video-capture
//            )
//            Log.d(TAG, "Use case videoCapture bound to cameraProvider")

//        } catch (exc: Exception) {
//            Log.e(TAG, "Use case videoCapture failed", exc)
//        }
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
//         if (isDetectionOn()) {
////             Log.d(TAG, "detectObjects: detecting")
//             personClassifier.detect(bitmapBuffer, imageRotation)
//         }
        personClassifier.detect(bitmapBuffer, imageRotation)
    }


    // proj4
    // private fun captureVideo(cameraProvider: ProcessCameraProvider) {
    //     // check if videoCapture UseCase has been created successfully
    //     val videoCapture = this.videoCapture ?: return

    //     // if some recording is ongoing
    //     val curRecording = recording
    //     if (curRecording != null) {
    //         // stop current recording
    //         curRecording.stop()
    //         recording = null
    //         return
    //     }

    //     // create and start new recording session
    //     val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
    //         .format(System.currentTimeMillis())
    //     val contentValues = ContentValues().apply() {
    //         put(MediaStore.MediaColumns.DISPLAY_NAME, name)
    //         put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
    //         if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
    //             put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraX-Video")
    //         }
    //     }

    //     // create a camera selector to bind image analysis to life cycle
    //     val cameraSelector =
    //         CameraSelector.Builder()
    //             .requireLensFacing(CameraSelector.LENS_FACING_BACK)
    //             .build()

    //     // create storing option in extra storage
    //     val mediaStoreOutputOptions = MediaStoreOutputOptions
    //         .Builder(requireActivity().contentResolver,
    //             MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
    //         .setContentValues(contentValues)
    //         .build()

    //     // set the output option of VideoCapture to Recorder
    //     recording = videoCapture.output
    //         .prepareRecording(this.requireContext(), mediaStoreOutputOptions)
    //         .start(ContextCompat.getMainExecutor(this.requireContext())) {
    //                 recordEvent -> when(recordEvent) {
    //             is VideoRecordEvent.Start -> {
    //                 // call the change of the text in audio fragment snapview
    //                 try {
    //                     // A variable number of use-cases can be passed here -
    //                     // camera provides access to CameraControl & CameraInfo
    //                     camera = cameraProvider.bindToLifecycle(
    //                         this,
    //                         cameraSelector,
    //                         imageAnalyzer,
    //                     )
    //                 } catch (exc: Exception) {
    //                     Log.e(TAG, "Use case binding failed", exc)
    //                 }
    //             }
    //             is VideoRecordEvent.Finalize -> {
    //                 if (!recordEvent.hasError()) {
    //                     val msg = "Video capture succeeded: " +
    //                             "${recordEvent.outputResults.outputUri}"
    //                     Toast.makeText(this.context, msg, Toast.LENGTH_SHORT)
    //                         .show()
    //                     Log.d(TAG, msg)
    //                 } else {
    //                     recording?.close()
    //                     recording = null
    //                     Log.e(TAG, "Video capture ends with error: " +
    //                             "${recordEvent.error}")
    //                 }
    //                 cameraProvider.unbind(imageAnalyzer)

    //             }
    //         }
    //         }

    // }
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
//            // Pass necessary information to OverlayView for drawing on the canvas
//            fragmentCameraBinding.overlay.setResults(
//                results ?: LinkedList<Detection>(),
//                imageHeight,
//                imageWidth
//            )

            // find at least one bounding box of the person
            val isPersonDetected: Boolean = results!!.find { it.categories[0].label == "person" } != null

//            if (isPersonDetected){
//                Log.d(TAG, "person detected")
//            }


            // change UI according to the result
            if (AudioFragment.state == 1 && isPersonDetected) {
                noPersonTime = 0
                personTime += 1
                if (personTime > 50){
                    Log.d(TAG, "person entered")
                    AudioFragment.state = 2
                    personView.text = "PERSON"
                    personView.setBackgroundColor(ProjectConfiguration.activeBackgroundColor)
                    personView.setTextColor(ProjectConfiguration.activeTextColor)
                }
            } else if (AudioFragment.state == 2 && !isPersonDetected) {
                personTime = 0
                noPersonTime += 1
                if (noPersonTime > 50){
                    Log.d(TAG, "person left")
                    setDetectionOn(false)

                    // once the person leaves, clear the screen on the next round
                    clearScreen = true
                    AudioFragment.state = 0
                    personView.text = "NO PERSON"
                    personView.setBackgroundColor(ProjectConfiguration.idleBackgroundColor)
                    personView.setTextColor(ProjectConfiguration.idleTextColor)

                    cameraProvider.unbind(imageAnalyzer)
                }
            }

            if (isDetectionOn()){
                // Pass necessary information to OverlayView for drawing on the canvas
                fragmentCameraBinding.overlay.setResults(
                    results ?: LinkedList<Detection>(),
                    imageHeight,
                    imageWidth
                )
            }
            else {
                fragmentCameraBinding.overlay.setResults(
                    LinkedList<Detection>() ?: LinkedList<Detection>(),
                    imageHeight,
                    imageWidth
                )
            }

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
