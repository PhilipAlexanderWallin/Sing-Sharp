package com.example.easyzinger

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class EZViewModel : ViewModel() {
    val note = MutableLiveData<String>()
}