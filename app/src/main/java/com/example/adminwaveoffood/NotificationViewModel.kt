package com.example.adminwaveoffood

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData

class NotificationViewModel(application: Application) : AndroidViewModel(application) {
    val newNotification: MutableLiveData<Boolean> = MutableLiveData(false)

    fun notifyNewOrder() {
        newNotification.value = true
    }
}