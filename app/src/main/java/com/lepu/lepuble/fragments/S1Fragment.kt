package com.lepu.lepuble.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.jeremyliao.liveeventbus.LiveEventBus
import com.lepu.lepuble.R
import com.lepu.lepuble.ble.S1BleInterface
import com.lepu.lepuble.ble.obj.Er1DataController
import com.lepu.lepuble.objs.Bluetooth
import com.lepu.lepuble.vals.EventMsgConst
import com.lepu.lepuble.viewmodel.MainViewModel
import com.lepu.lepuble.viewmodel.S1ViewModel
import com.lepu.lepuble.views.EcgBkg
import com.lepu.lepuble.views.EcgView
import kotlinx.android.synthetic.main.fragment_s1.*
import java.text.SimpleDateFormat
import kotlin.math.floor


class S1Fragment : Fragment() {

    private lateinit var bleInterface: S1BleInterface

    private val s1Model: S1ViewModel by viewModels()
    private val activityModel: MainViewModel by activityViewModels()

    private lateinit var ecgBkg: EcgBkg
    private lateinit var ecgView: EcgView

    private lateinit var viewEcgBkg: RelativeLayout
    private lateinit var viewEcgView: RelativeLayout

    private var device: Bluetooth? = null


    /**
     * rt wave
     */
    private val waveHandler = Handler()

    inner class WaveTask : Runnable {
        override fun run() {
            if (!runWave) {
                return
            }

            val interval: Int = when {
                Er1DataController.dataRec.size > 250 -> {
                    30
                }
                Er1DataController.dataRec.size > 150 -> {
                    35
                }
                Er1DataController.dataRec.size > 75 -> {
                    40
                }
                else -> {
                    45
                }
            }

            waveHandler.postDelayed(this, interval.toLong())
//            LogUtils.d("DataRec: ${Er1DataController.dataRec.size}, delayed $interval")

            val temp = Er1DataController.draw(5)
            s1Model.dataSrc.value = Er1DataController.feed(s1Model.dataSrc.value, temp)
        }
    }

    private var runWave = false
    private fun startWave() {
        if (runWave) {
            return
        }
        runWave = true
        waveHandler.post(WaveTask())
    }

    private fun stopWave() {
        runWave = false
        Er1DataController.clear()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        arguments?.let {
//            device = it.getParcelable(ARG_ER1_DEVICE)
//            LogUtils.d("instance: ${device?.name}")
//            connect()
//        }
        bleInterface = S1BleInterface()
        bleInterface.setViewModel(s1Model)
        addLiveDataObserver()
        addLiveEventObserver()
    }

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(R.layout.fragment_s1, container, false)

        // add view
        viewEcgBkg = v.findViewById<RelativeLayout>(R.id.ecg_bkg)
        viewEcgView = v.findViewById<RelativeLayout>(R.id.ecg_view)

        viewEcgBkg.post {
            initEcgView()
        }

        return v
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initView()
    }

    @ExperimentalUnsignedTypes
    private fun initView() {
//        get_file_list.setOnClickListener {
//            bleInterface.getFileList()
//        }
//
//        /**
//         * ???????????????????????????
//         */
//        download_file.setOnClickListener {
//            if (bleInterface.fileList == null || bleInterface.fileList!!.size == 0) {
//                Toast.makeText(activity, "please get file list at first or file list is null", Toast.LENGTH_SHORT).show()
//            } else {
//                val name = bleInterface.fileList!!.fileList[0]
//                bleInterface.downloadFile(name)
//            }
//
//        }
//
//        get_rt_data.setOnClickListener {
//            bleInterface.runRtTask()
//            startWave()
//        }
    }

    private fun initEcgView() {
        // cal screen
        val dm =resources.displayMetrics
        val index = floor(viewEcgBkg.width / dm.xdpi * 25.4 / 25 * 125).toInt()
        Er1DataController.maxIndex = index

        val mm2px = 25.4f / dm.xdpi
        Er1DataController.mm2px = mm2px

//        LogUtils.d("max index: $index", "mm2px: $mm2px")

        viewEcgBkg.measure(0, 0)
        ecgBkg = EcgBkg(context)
        viewEcgBkg.addView(ecgBkg)

        viewEcgView.measure(0, 0)
        ecgView = EcgView(context)
        viewEcgView.addView(ecgView)

        ecg_view.visibility = View.GONE
    }

    // Er1ViewModel
    private fun addLiveDataObserver(){

        activityModel.er1DeviceName.observe(this, {
            if (it == null) {
                device_sn.text = "no bind device"
            } else {
                device_sn.text = it
            }
        })

        s1Model.dataSrc.observe(this, {
            if (this::ecgView.isInitialized) {
                ecgView.setDataSrc(it)
                ecgView.invalidate()
            }
        })

        s1Model.er1.observe(this, {
            device_sn.text = "SN???${it.sn}"
        })

        s1Model.connect.observe(this, {
            if (it) {
                ble_state.setImageResource(R.mipmap.bluetooth_ok)
                ecg_view.visibility = View.VISIBLE
                battery.visibility = View.VISIBLE
                battery_left_duration.visibility = View.VISIBLE
                startWave()
            } else {
                ble_state.setImageResource(R.mipmap.bluetooth_error)
                ecg_view.visibility = View.INVISIBLE
                battery.visibility = View.INVISIBLE
                battery_left_duration.visibility = View.INVISIBLE
                stopWave()
            }
        })

        s1Model.duration.observe(this, {
            if (it == 0) {
                measure_duration.text = "?"
                start_at.text = "?"
            } else {
                val day = it/60/60/24
                val hour = it/60/60 % 24
                val minute = it/60 % 60

                val start = System.currentTimeMillis() - it*1000
                start_at.text = SimpleDateFormat("yyyy/MM/dd HH:mm").format(start)
                if (day != 0) {
                    measure_duration.text = "$day ??? $hour ?????? $minute ??????"
                } else {
                    measure_duration.text = "$hour ?????? $minute ??????"
                }
            }
        })

        s1Model.battery.observe(this, {
            battery.setImageLevel(it)
        })

        s1Model.hr.observe(this, {
            if (it == 0) {
                hr.text = "?"
            } else {
                hr.text = it.toString()
            }
        })

        s1Model.weight.observe(this, {
            weight.text = it.toString()
        })
        s1Model.unit.observe(this,{
            unit.text = it.toString()
        })
        s1Model.resistance.observe(this, {
            resistance.text = it.toString()
        })
    }


    /**
     * observe LiveDataBus
     * receive from KcaBleInterface
     * ???????????????interface???????????????????????????????????????
     */
    private fun addLiveEventObserver() {
        LiveEventBus.get(EventMsgConst.EventDeviceChoosen)
                .observe(this, {
                    connect(it as Bluetooth)
                })
    }

    private fun connect(b: Bluetooth) {
        this@S1Fragment.context?.let { bleInterface.connect(it, b.device) }
    }

    companion object {

        @JvmStatic
        fun newInstance() = S1Fragment()
    }
}