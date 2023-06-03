package com.example.pj4test

import android.Manifest.permission.CAMERA
import android.Manifest.permission.RECORD_AUDIO

//proj4
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE

import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import java.util.*

// fragment management
import  androidx.fragment.app.FragmentContainerView
import com.example.pj4test.fragment.CameraFragment
import com.example.pj4test.fragment.AudioFragment


class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"

    // permissions
//    private val permissions = arrayOf(RECORD_AUDIO, CAMERA)
    private val permissions = arrayOf(RECORD_AUDIO, CAMERA, WRITE_EXTERNAL_STORAGE)

    private val PERMISSIONS_REQUEST = 0x0000001;

    val fragmentManager = supportFragmentManager


    // camera fragment
    private var cameraFragment: CameraFragment? = null
    // audio fragment
    private var audioFragment: AudioFragment? = null


            @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.d(TAG, "onCreate")

        val caneraFragmentContainer = findViewById<FragmentContainerView>(R.id.cameraFragmentContainerView)
        cameraFragment = supportFragmentManager.findFragmentById(caneraFragmentContainer.id) as? CameraFragment
//        cameraFragment?.sayHello()
        val audioFragmentContainer = findViewById<FragmentContainerView>(R.id.audioFragmentContainerView)
        audioFragment = supportFragmentManager.findFragmentById(audioFragmentContainer.id) as? AudioFragment

        cameraFragment?.sayHello()
        audioFragment?.sayHello()
        checkPermissions() // check permissions
    }


    private fun checkPermissions() {
        if (permissions.all{ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED}){
            Log.d(TAG, "All Permission Granted")


        }
        else{
            requestPermissions(permissions, PERMISSIONS_REQUEST)
        }
    }


    fun sayHello(){
        Log.d(TAG, "hello! this is MainActivity instance")
    }

    fun setPersonDetectionOn(on:Boolean){
        cameraFragment?.setDetectionOn(on)
    }
}