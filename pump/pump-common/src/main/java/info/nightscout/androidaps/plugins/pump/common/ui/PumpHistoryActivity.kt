package info.nightscout.androidaps.plugins.pump.common.ui

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.android.support.DaggerAppCompatActivity
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.plugins.pump.common.R
import info.nightscout.androidaps.plugins.pump.common.databinding.PumpHistoryActivityBinding
import info.nightscout.androidaps.plugins.pump.common.defs.PumpHistoryEntryGroup
import info.nightscout.androidaps.plugins.pump.common.driver.PumpDriverConfigurationCapable
import info.nightscout.androidaps.plugins.pump.common.driver.history.PumpHistoryDataProvider
import info.nightscout.androidaps.plugins.pump.common.driver.history.PumpHistoryEntry
import info.nightscout.androidaps.plugins.pump.common.driver.history.PumpHistoryText
import info.nightscout.androidaps.interfaces.ResourceHelper
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import javax.inject.Inject

class PumpHistoryActivity : DaggerAppCompatActivity() {

    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var context: Context

    var filteredHistoryList: MutableList<PumpHistoryEntry> = mutableListOf()
    var typeListFull: List<TypeList>? = null
    var fullList: MutableList<PumpHistoryEntry> = mutableListOf()

    private lateinit var historyDataProvider: PumpHistoryDataProvider
    private lateinit var binding: PumpHistoryActivityBinding

    var manualChange = false

    lateinit var recyclerViewAdapter: RecyclerViewAdapter

    private fun prepareData() {

        val allData = historyDataProvider.getInitialData()

        aapsLogger.info(LTag.PUMP, "Loaded ${allData.size} items from database. [initialSize=${historyDataProvider.getInitialPeriod()}]")

        this.fullList.addAll(allData)
    }

    private fun filterHistory(group: PumpHistoryEntryGroup) {
        filteredHistoryList.clear()

        if (group === PumpHistoryEntryGroup.All) {
            filteredHistoryList.addAll(fullList)
        } else {
            for (pumpHistoryEntry in fullList) {
                if (historyDataProvider.isItemInSelection(pumpHistoryEntry.getEntryTypeGroup(), group)) {
                    filteredHistoryList.add(pumpHistoryEntry)
                }
            }
        }

        aapsLogger.info(LTag.PUMP, "Filtered list ${filteredHistoryList.size} items (group ${group}), from full list (${fullList.size}).")

        recyclerViewAdapter.setHistoryListInternal(filteredHistoryList)
        recyclerViewAdapter.notifyDataSetChanged()

    }

    override fun onResume() {
        super.onResume()
        //filterHistory(selectedGroup)
        //setHistoryTypeSpinner()
        //aapsLogger.info(LTag.PUMP, "onResume")
        //binding.pumpHistoryRoot.requestLayout()
    }

    private fun setHistoryTypeSpinner() {
        manualChange = true
        for (i in typeListFull!!.indices) {
            if (typeListFull!![i].entryGroup === selectedGroup) {
                binding.pumpHistoryType.setSelection(i)
                break
            }
        }
        SystemClock.sleep(200)
        manualChange = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = PumpHistoryActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configuration
        val activePump = activePlugin.activePump

        if (activePump is PumpDriverConfigurationCapable) {
            historyDataProvider = activePump.getPumpDriverConfiguration().getPumpHistoryDataProvider()
        } else {
            throw RuntimeException("PumpHistoryActivity can be used only with PumpDriverConfigurationCapable pump driver.")
        }

        prepareData()

        binding.pumpHistoryRecyclerView.setHasFixedSize(true)
        binding.pumpHistoryRecyclerView.layoutManager = LinearLayoutManager(this)
        recyclerViewAdapter = RecyclerViewAdapter(filteredHistoryList)
        binding.pumpHistoryRecyclerView.adapter = recyclerViewAdapter
        binding.pumpHistoryStatus.visibility = View.GONE
        typeListFull = getTypeList(historyDataProvider.getAllowedPumpHistoryGroups())
        val spinnerAdapter = ArrayAdapter(this, R.layout.spinner_centered, typeListFull!!)

        binding.pumpHistoryText.text = historyDataProvider.getText(PumpHistoryText.PUMP_HISTORY)

        binding.pumpHistoryType.adapter = spinnerAdapter
        binding.pumpHistoryType.getLayoutParams().width = fromDpToSize(historyDataProvider.getSpinnerWidthInPixels())
        binding.pumpHistoryType.requestLayout();
        binding.pumpHistoryType.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
                if (manualChange) return
                val selected = binding.pumpHistoryType.getSelectedItem() as TypeList
                showingType = selected
                selectedGroup = selected.entryGroup
                filterHistory(selectedGroup)
                val selectedText = parent!!.getChildAt(0) as TextView
                selectedText.textSize = 15.0f  // FIXME hack for selected item, also concerns pump_type marginTop

                binding.pumpHistoryTop.requestLayout()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                if (manualChange) return
                filterHistory(PumpHistoryEntryGroup.All)
            }
        })
        binding.pumpHistoryTypeText.requestLayout()
    }

    private fun getTypeList(list: List<PumpHistoryEntryGroup>?): List<TypeList> {
        val typeList = ArrayList<TypeList>()
        for (pumpHistoryEntryGroup in list!!) {
            typeList.add(TypeList(pumpHistoryEntryGroup))
        }
        return typeList
    }

    fun fromDpToSize(dpSize: Int): Int {
        val scale = context.resources.displayMetrics.density
        val pixelsFl = ((dpSize * scale) + 0.5f)
        return pixelsFl.toInt()
    }

    class TypeList internal constructor(var entryGroup: PumpHistoryEntryGroup) {

        var name: String
        override fun toString(): String {
            return name
        }

        init {
            name = entryGroup.translated!!
        }
    }

    class RecyclerViewAdapter internal constructor(
        var historyList: List<PumpHistoryEntry>
    ) : RecyclerView.Adapter<RecyclerViewAdapter.HistoryViewHolder>() {

        fun setHistoryListInternal(historyList: List<PumpHistoryEntry>) {
            this.historyList = historyList
        }

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): HistoryViewHolder {
            val v = LayoutInflater.from(viewGroup.context).inflate(
                R.layout.pump_history_item,  //
                viewGroup, false
            )
            return HistoryViewHolder(v)
        }

        override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
            val record = historyList[position]
            holder.timeView.text = record.getEntryDateTime()
            holder.typeView.text = record.getEntryType()
            holder.valueView.text = record.getEntryValue()
        }

        override fun getItemCount(): Int {
            return historyList.size
        }

        class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

            var timeView: TextView
            var typeView: TextView
            var valueView: TextView

            init {
                timeView = itemView.findViewById(R.id.pump_history_time)
                typeView = itemView.findViewById(R.id.pump_history_source)
                valueView = itemView.findViewById(R.id.pump_history_description)
            }
        }

    }

    companion object {
        var showingType: TypeList? = null
        var selectedGroup = PumpHistoryEntryGroup.All
    }
}