package com.example.medpal

import android.content.Context
import android.media.MediaPlayer


// soundResID -> sound resource id
fun playSound(context: Context,soundResId:Int){
    MediaPlayer.create(context,soundResId)?.apply {
        setOnCompletionListener { release() }
        start()
    }
}