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
import com.apbarrero.wheresmycar.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class ServiceRestartJob : JobService() {

    companion object {
        private const val JOB_ID = 1001

        fun scheduleJob(context: Context) {
            val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            jobScheduler.cancel(JOB_ID)
            val jobInfo = JobInfo.Builder(JOB_ID, ComponentName(context, ServiceRestartJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                .setPersisted(true)
                .setPeriodic(15 * 60 * 1000)
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
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = Repository(this@ServiceRestartJob).appSettings.first()
                val address = settings.selectedDeviceAddress
                val name = settings.selectedDeviceName
                if (settings.isTrackingEnabled &&
                    address != null &&
                    name != null &&
                    !ParkingTrackingService.isServiceRunning()
                ) {
                    val serviceIntent = Intent(this@ServiceRestartJob, ParkingTrackingService::class.java).apply {
                        action = ParkingTrackingService.ACTION_START_TRACKING
                        putExtra(ParkingTrackingService.EXTRA_DEVICE_ADDRESS, address)
                        putExtra(ParkingTrackingService.EXTRA_DEVICE_NAME, name)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent)
                    } else {
                        startService(serviceIntent)
                    }
                }
            } finally {
                jobFinished(params, false)
            }
        }
        return true // work is async
    }

    override fun onStopJob(params: JobParameters?) = false
}
