package com.example.pj4test.fragment

import android.graphics.Camera
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.pj4test.ProjectConfiguration
import com.example.pj4test.audioInference.SnapClassifier
import com.example.pj4test.databinding.FragmentAudioBinding

import com.example.pj4test.fragment.CameraFragment

class AudioFragment: Fragment(), SnapClassifier.DetectorListener {
    private val TAG = "AudioFragment"

    private var _fragmentAudioBinding: FragmentAudioBinding? = null

    private val fragmentAudioBinding
        get() = _fragmentAudioBinding!!

//    // classifiers
//    lateinit var snapClassifier: SnapClassifier

    companion object {
        var state: Int = 0
        // classifiers
        lateinit var snapClassifier: SnapClassifier
        fun startInferencing() {
            // Code for staticMethod2
        }

    }
    // views
    lateinit var snapView: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentAudioBinding = FragmentAudioBinding.inflate(inflater, container, false)

        return fragmentAudioBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        snapView = fragmentAudioBinding.SnapView

        snapClassifier = SnapClassifier()
        snapClassifier.initialize(requireContext())
        snapClassifier.setDetectorListener(this)
    }

    override fun onPause() {
        super.onPause()
        snapClassifier.stopInferencing()
    }

    override fun onResume() {
        super.onResume()
        snapClassifier.startInferencing()
    }

    override fun onResults(score: Float) {
//        TODO: start filming here when clap detected
        activity?.runOnUiThread {
//            if (score > SnapClassifier.THRESHOLD) {
//                snapView.text = "SNAP"
//                snapView.setBackgroundColor(ProjectConfiguration.activeBackgroundColor)
//                snapView.setTextColor(ProjectConfiguration.activeTextColor)
//            } else {
//                snapView.text = "NO SNAP"
//                snapView.setBackgroundColor(ProjectConfiguration.idleBackgroundColor)
//                snapView.setTextColor(ProjectConfiguration.idleTextColor)
//            }
            if (state == 0 && score > SnapClassifier.THRESHOLD) {
                Log.d(TAG, "clap detected")
                state = 1
                snapView.text = "RECORDING"
                CameraFragment.detectionOn = true
//                requireContext()

                snapView.setBackgroundColor(ProjectConfiguration.activeBackgroundColor)
                snapView.setTextColor(ProjectConfiguration.activeTextColor)
//                snapClassifier.stopInferencing()
            }
            else if (state == 0 && score < SnapClassifier.THRESHOLD) {
//                Log.d(TAG, "snap detected")
                snapView.text = "NOT RECORDING"
                CameraFragment.detectionOn = false
                snapView.setBackgroundColor(ProjectConfiguration.idleBackgroundColor)
                snapView.setTextColor(ProjectConfiguration.idleTextColor)
            }
        }
    }
}