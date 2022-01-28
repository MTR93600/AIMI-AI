package info.nightscout.androidaps.diaconn.activities

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import info.nightscout.androidaps.activities.NoSplashAppCompatActivity
import info.nightscout.androidaps.diaconn.R
import info.nightscout.androidaps.diaconn.common.RecordTypes
import info.nightscout.androidaps.diaconn.database.DiaconnHistoryRecord
import info.nightscout.androidaps.diaconn.database.DiaconnHistoryRecordDao
import info.nightscout.androidaps.diaconn.databinding.DiaconnG8HistoryActivityBinding
import info.nightscout.androidaps.events.EventPumpStatusChanged
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.CommandQueue
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import java.util.*
import javax.inject.Inject

class DiaconnG8HistoryActivity : NoSplashAppCompatActivity() {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var diaconnHistoryRecordDao: DiaconnHistoryRecordDao
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var aapsSchedulers: AapsSchedulers

    private val disposable = CompositeDisposable()

    private var showingType = RecordTypes.RECORD_TYPE_ALARM
    private var historyList: List<DiaconnHistoryRecord> = ArrayList()

    class TypeList internal constructor(var type: Byte, var name: String) {

        override fun toString(): String = name
    }

    private lateinit var binding: DiaconnG8HistoryActivityBinding

    override fun onResume() {
        super.onResume()
        disposable += rxBus
            .toObservable(EventPumpStatusChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ binding.status.text = it.getStatus(rh) }) { fabricPrivacy.logException(it) }
        swapAdapter(showingType)
    }

    override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DiaconnG8HistoryActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.recyclerview.setHasFixedSize(true)
        binding.recyclerview.layoutManager = LinearLayoutManager(this)
        binding.recyclerview.adapter = RecyclerViewAdapter(historyList)
        binding.status.visibility = View.GONE

        // Types
        val typeList = ArrayList<TypeList>()
        typeList.add(TypeList(RecordTypes.RECORD_TYPE_ALARM, rh.gs(R.string.diaconn_g8_history_alarm)))
        typeList.add(TypeList(RecordTypes.RECORD_TYPE_BASALHOUR, rh.gs(R.string.diaconn_g8_history_basalhours)))
        typeList.add(TypeList(RecordTypes.RECORD_TYPE_BOLUS, rh.gs(R.string.diaconn_g8_history_bolus)))
        typeList.add(TypeList(RecordTypes.RECORD_TYPE_TB, rh.gs(R.string.diaconn_g8_history_tempbasal)))
        typeList.add(TypeList(RecordTypes.RECORD_TYPE_DAILY, rh.gs(R.string.diaconn_g8_history_dailyinsulin)))
        typeList.add(TypeList(RecordTypes.RECORD_TYPE_REFILL, rh.gs(R.string.diaconn_g8_history_refill)))
        typeList.add(TypeList(RecordTypes.RECORD_TYPE_SUSPEND, rh.gs(R.string.diaconn_g8_history_suspend)))
        binding.spinner.adapter = ArrayAdapter(this, R.layout.spinner_centered, typeList)

        binding.reload.setOnClickListener {
            val selected = binding.spinner.selectedItem as TypeList?
                ?: return@setOnClickListener
            runOnUiThread {
                binding.reload.visibility = View.GONE
                binding.status.visibility = View.VISIBLE
            }
            clearCardView()
            commandQueue.loadHistory(selected.type, object : Callback() {
                override fun run() {
                    swapAdapter(selected.type)
                    runOnUiThread {
                        binding.reload.visibility = View.VISIBLE
                        binding.status.visibility = View.GONE
                    }
                }
            })
        }
        binding.spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = typeList[position]
                swapAdapter(selected.type)
                showingType = selected.type
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                clearCardView()
            }
        }
    }

    inner class RecyclerViewAdapter internal constructor(private var historyList: List<DiaconnHistoryRecord>) : RecyclerView.Adapter<RecyclerViewAdapter.HistoryViewHolder>() {

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): HistoryViewHolder =
            HistoryViewHolder(LayoutInflater.from(viewGroup.context).inflate(R.layout.diaconn_g8_history_item, viewGroup, false))

        override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
            val record = historyList[position]
            holder.time.text = dateUtil.dateAndTimeString(record.timestamp)
            holder.value.text = DecimalFormatter.to2Decimal(record.value)
            holder.stringValue.text = record.stringValue
            holder.bolusType.text = record.bolusType
            holder.duration.text = DecimalFormatter.to0Decimal(record.duration.toDouble())
            holder.alarm.text = record.alarm
            when (showingType) {
                RecordTypes.RECORD_TYPE_ALARM     -> {
                    holder.time.visibility = View.VISIBLE
                    holder.value.visibility = View.VISIBLE
                    holder.stringValue.visibility = View.VISIBLE
                    holder.bolusType.visibility = View.GONE
                    holder.duration.visibility = View.GONE
                    holder.dailyBasal.visibility = View.GONE
                    holder.dailyBolus.visibility = View.GONE
                    holder.dailyTotal.visibility = View.GONE
                    holder.alarm.visibility = View.VISIBLE
                }

                RecordTypes.RECORD_TYPE_BOLUS     -> {
                    holder.time.visibility = View.VISIBLE
                    holder.value.visibility = View.VISIBLE
                    holder.stringValue.visibility = View.VISIBLE
                    holder.bolusType.visibility = View.VISIBLE
                    holder.duration.visibility = View.VISIBLE
                    holder.dailyBasal.visibility = View.GONE
                    holder.dailyBolus.visibility = View.GONE
                    holder.dailyTotal.visibility = View.GONE
                    holder.alarm.visibility = View.GONE
                }

                RecordTypes.RECORD_TYPE_DAILY     -> {
                    holder.dailyBasal.text = rh.gs(R.string.formatinsulinunits, record.dailyBasal)
                    holder.dailyBolus.text = rh.gs(R.string.formatinsulinunits, record.dailyBolus)
                    holder.dailyTotal.text = rh.gs(R.string.formatinsulinunits, record.dailyBolus + record.dailyBasal)
                    holder.time.text = dateUtil.dateString(record.timestamp)
                    holder.time.visibility = View.VISIBLE
                    holder.value.visibility = View.GONE
                    holder.stringValue.visibility = View.GONE
                    holder.bolusType.visibility = View.GONE
                    holder.duration.visibility = View.GONE
                    holder.dailyBasal.visibility = View.VISIBLE
                    holder.dailyBolus.visibility = View.VISIBLE
                    holder.dailyTotal.visibility = View.VISIBLE
                    holder.alarm.visibility = View.GONE
                }

                RecordTypes.RECORD_TYPE_BASALHOUR -> {
                    holder.time.visibility = View.VISIBLE
                    holder.value.visibility = View.VISIBLE
                    holder.stringValue.visibility = View.VISIBLE
                    holder.bolusType.visibility = View.GONE
                    holder.duration.visibility = View.GONE
                    holder.dailyBasal.visibility = View.GONE
                    holder.dailyBolus.visibility = View.GONE
                    holder.dailyTotal.visibility = View.GONE
                    holder.alarm.visibility = View.GONE
                }

                RecordTypes.RECORD_TYPE_REFILL    -> {
                    holder.time.visibility = View.VISIBLE
                    holder.value.visibility = View.VISIBLE
                    holder.stringValue.visibility = View.VISIBLE
                    holder.bolusType.visibility = View.GONE
                    holder.duration.visibility = View.GONE
                    holder.dailyBasal.visibility = View.GONE
                    holder.dailyBolus.visibility = View.GONE
                    holder.dailyTotal.visibility = View.GONE
                    holder.alarm.visibility = View.GONE
                }

                RecordTypes.RECORD_TYPE_TB        -> {
                    holder.time.visibility = View.VISIBLE
                    holder.value.visibility = View.VISIBLE
                    holder.stringValue.visibility = View.VISIBLE
                    holder.bolusType.visibility = View.GONE
                    holder.duration.visibility = View.VISIBLE
                    holder.dailyBasal.visibility = View.GONE
                    holder.dailyBolus.visibility = View.GONE
                    holder.dailyTotal.visibility = View.GONE
                    holder.alarm.visibility = View.GONE
                }

                RecordTypes.RECORD_TYPE_SUSPEND   -> {
                    holder.time.visibility = View.VISIBLE
                    holder.value.visibility = View.GONE
                    holder.stringValue.visibility = View.VISIBLE
                    holder.bolusType.visibility = View.GONE
                    holder.duration.visibility = View.GONE
                    holder.dailyBasal.visibility = View.GONE
                    holder.dailyBolus.visibility = View.GONE
                    holder.dailyTotal.visibility = View.GONE
                    holder.alarm.visibility = View.GONE
                }
            }
        }

        override fun getItemCount(): Int {
            return historyList.size
        }

        inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

            var time: TextView = itemView.findViewById(R.id.diaconn_g8_history_time)
            var value: TextView = itemView.findViewById(R.id.diaconn_g8_history_value)
            var bolusType: TextView = itemView.findViewById(R.id.diaconn_g8_history_bolustype)
            var stringValue: TextView = itemView.findViewById(R.id.diaconn_g8_history_stringvalue)
            var duration: TextView = itemView.findViewById(R.id.diaconn_g8_history_duration)
            var dailyBasal: TextView = itemView.findViewById(R.id.diaconn_g8_history_dailybasal)
            var dailyBolus: TextView = itemView.findViewById(R.id.diaconn_g8_history_dailybolus)
            var dailyTotal: TextView = itemView.findViewById(R.id.diaconn_g8_history_dailytotal)
            var alarm: TextView = itemView.findViewById(R.id.diaconn_g8_history_alarm)
        }
    }

    private fun swapAdapter(type: Byte) {
        disposable += diaconnHistoryRecordDao
            .allFromByType(dateUtil.now() - T.months(1).msecs(), type)
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .subscribe { historyList -> binding.recyclerview.swapAdapter(RecyclerViewAdapter(historyList), false) }
    }

    private fun clearCardView() = binding.recyclerview.swapAdapter(RecyclerViewAdapter(ArrayList()), false)
}