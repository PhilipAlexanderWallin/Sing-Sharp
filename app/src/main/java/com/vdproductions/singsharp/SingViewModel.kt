package com.vdproductions.singsharp

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SingViewModel : ViewModel() {
    val note = MutableLiveData<Triple<Int, Int, Float>>()
}