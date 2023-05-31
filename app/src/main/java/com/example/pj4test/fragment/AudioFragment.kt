package com.example.pj4test.fragment

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



class AudioFragment: Fragment(), SnapClassifier.DetectorListener {
    private val TAG = "AudioFragment"
    private var _fragmentAudioBinding: FragmentAudioBinding? = null


    companion object {
        // classifiers
        lateinit var snapClassifier: SnapClassifier

        var squatting = 0
        fun startInferencing() {
            // Code for the static method
            snapClassifier.startInferencing()
        }
    }
    private val fragmentAudioBinding
        get() = _fragmentAudioBinding!!



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
        Log.d(TAG, "onViewCreated")

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
//        activity?.runOnUiThread {
//            if (score > SnapClassifier.THRESHOLD) {
//                snapView.text = "SNAP"
//                snapView.setBackgroundColor(ProjectConfiguration.activeBackgroundColor)
//                snapView.setTextColor(ProjectConfiguration.activeTextColor)
//            } else {
//                snapView.text = "NO SNAP"
//                snapView.setBackgroundColor(ProjectConfiguration.idleBackgroundColor)
//                snapView.setTextColor(ProjectConfiguration.idleTextColor)
//            }
//        }
        activity?.runOnUiThread {
            if (squatting == 0 && score > SnapClassifier.THRESHOLD) {
                snapView.text = "RECORDING"
                snapView.setBackgroundColor(ProjectConfiguration.activeBackgroundColor)
                snapView.setTextColor(ProjectConfiguration.activeTextColor)
                squatting = 1
                Log.d(TAG, "recording started")

                snapClassifier.stopInferencing()
            }



        }
    }
}