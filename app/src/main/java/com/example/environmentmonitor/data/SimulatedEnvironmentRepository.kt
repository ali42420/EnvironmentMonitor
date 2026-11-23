package com.example.environmentmonitor.data

import android.util.Log
import com.example.environmentmonitor.model.Limits
import com.example.environmentmonitor.model.StationInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.atomic.AtomicBoolean

class SimulatedEnvironmentRepository : EnvironmentRepository {

    // در هر اجرای برنامه، اولین فراخوانی loadStation خطا پرتاب می‌کند
    private val shouldFailFirstStationLoad = AtomicBoolean(true)

    // پرچم ایمن در برابر هم‌زمانی برای خطای نمایشی در خوانش بعدی دما
    private val failNextTemp = AtomicBoolean(false)

    private val rawTempSequence = listOf(230, 250, 290, -999, 270)
    private val humiditySequence = listOf(40, 50, 65, 55)

    override suspend fun loadStation(): StationInfo {
        val startTime = System.currentTimeMillis()
        Log.d("EnvJob", "loadStation started on thread=${Thread.currentThread().name}")
        delay(800)

        if (shouldFailFirstStationLoad.compareAndSet(true, false)) {
            val elapsed = System.currentTimeMillis() - startTime
            Log.e("EnvJob", "loadStation simulated failure after ${elapsed}ms")
            throw IllegalStateException("خطا در دریافت اطلاعات ایستگاه (شبیه‌سازی‌شده)")
        }

        val elapsed = System.currentTimeMillis() - startTime
        Log.d("EnvJob", "loadStation completed in ${elapsed}ms")
        return StationInfo(id = "room-1", name = "اتاق مطالعه")
    }

    override suspend fun loadLimits(): Limits {
        val startTime = System.currentTimeMillis()
        Log.d("EnvJob", "loadLimits started on thread=${Thread.currentThread().name}")
        return try {
            delay(1200)
            val elapsed = System.currentTimeMillis() - startTime
            Log.d("EnvJob", "loadLimits completed in ${elapsed}ms")
            Limits(maxTempC = 28.0, maxHumidity = 60)
        } catch (e: CancellationException) {
            val elapsed = System.currentTimeMillis() - startTime
            Log.w("EnvJob", "loadLimits cancelled after ${elapsed}ms")
            throw e
        }
    }

    override fun temperatureRaw(): Flow<Int> = flow {
        var index = 0
        Log.d("EnvFlow", "temperatureRaw producer STARTED on thread=${Thread.currentThread().name}")
        try {
            while (true) {
                if (failNextTemp.compareAndSet(true, false)) {
                    Log.e("EnvFlow", "temperatureRaw throwing simulated error on thread=${Thread.currentThread().name}")
                    throw IllegalStateException("خطای شبیه‌سازی‌شده در حسگر دما")
                }

                val value = rawTempSequence[index]
                Log.d("EnvFlow", "temperatureRaw emit=$value on thread=${Thread.currentThread().name}")
                emit(value)

                index = (index + 1) % rawTempSequence.size
                delay(1000)
            }
        } catch (e: CancellationException) {
            Log.d("EnvFlow", "temperatureRaw producer CANCELLED on thread=${Thread.currentThread().name}")
            throw e
        } finally {
            Log.d("EnvFlow", "temperatureRaw producer ENDED on thread=${Thread.currentThread().name}")
        }
    }

    override fun humidity(): Flow<Int> = flow {
        var index = 0
        Log.d("EnvFlow", "humidity producer STARTED on thread=${Thread.currentThread().name}")
        try {
            while (true) {
                val value = humiditySequence[index]
                Log.d("EnvFlow", "humidity emit=$value on thread=${Thread.currentThread().name}")
                emit(value)

                index = (index + 1) % humiditySequence.size
                delay(1500)
            }
        } catch (e: CancellationException) {
            Log.d("EnvFlow", "humidity producer CANCELLED on thread=${Thread.currentThread().name}")
            throw e
        } finally {
            Log.d("EnvFlow", "humidity producer ENDED on thread=${Thread.currentThread().name}")
        }
    }

    override fun failNextTemperatureEmission() {
        failNextTemp.set(true)
        Log.w("EnvFlow", "Flag set: failNextTemperatureEmission = true")
    }
}