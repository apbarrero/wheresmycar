package com.apbarrero.wheresmycar.service

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class ServiceRestartJob : JobService() {
    
    companion object {
        private const val JOB_ID = 1001
        
        fun scheduleJob(context: Context) {
            val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            
            // Cancel any existing job
            jobScheduler.cancel(JOB_ID)
            
            val jobInfo = JobInfo.Builder(JOB_ID, ComponentName(context, ServiceRestartJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                .setPersisted(true)
                .setPeriodic(15 * 60 * 1000) // Check every 15 minutes
                .setRequiresCharging(false)
                .setRequiresDeviceIdle(false)
                .build()
                
            jobScheduler.schedule(jobInfo)
        }
        
        fun cancelJob(context: Context) {
            val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            jobScheduler.cancel(JOB_ID)
        }
    }
    
    override fun onStartJob(params: JobParameters?): Boolean {
        // Check if our service is running, if not restart it
        val preferences = getSharedPreferences("parking_prefs", Context.MODE_PRIVATE)
        val wasTracking = preferences.getBoolean("was_tracking", false)
        val deviceAddress = preferences.getString("tracked_device_address", null)
        val deviceName = preferences.getString("tracked_device_name", null)
        
        if (wasTracking && deviceAddress != null && deviceName != null) {
            // Check if service is running
            if (!ParkingTrackingService.isServiceRunning()) {
                // Service is not running, restart it
                val serviceIntent = Intent(this, ParkingTrackingService::class.java).apply {
                    action = ParkingTrackingService.ACTION_START_TRACKING
                    putExtra(ParkingTrackingService.EXTRA_DEVICE_ADDRESS, deviceAddress)
                    putExtra(ParkingTrackingService.EXTRA_DEVICE_NAME, deviceName)
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            }
        }
        
        jobFinished(params, false)
        return false
    }
    
    override fun onStopJob(params: JobParameters?): Boolean {
        return false
    }
}