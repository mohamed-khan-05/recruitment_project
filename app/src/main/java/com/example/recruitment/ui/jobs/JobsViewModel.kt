package com.example.recruitment.ui.jobs

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class JobsViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "This is Jobs Fragment"
    }
    val text: LiveData<String> = _text
}