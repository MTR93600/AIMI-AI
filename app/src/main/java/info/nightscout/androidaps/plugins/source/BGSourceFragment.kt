package info.nightscout.androidaps.plugins.source

import android.os.Bundle
import android.util.SparseArray
import android.view.*
import androidx.appcompat.widget.Toolbar
import androidx.core.util.forEach
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.database.entities.UserEntry.Action
import info.nightscout.androidaps.database.entities.UserEntry.Sources
import info.nightscout.androidaps.database.entities.ValueWithUnit
import info.nightscout.androidaps.database.transactions.InvalidateGlucoseValueTransaction
import info.nightscout.androidaps.databinding.BgsourceFragmentBinding
import info.nightscout.androidaps.databinding.BgsourceItemBinding
import info.nightscout.androidaps.events.EventNewBG
import info.nightscout.androidaps.extensions.directionToIcon
import info.nightscout.androidaps.extensions.toVisibility
import info.nightscout.androidaps.extensions.valueToUnitsString
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class BGSourceFragment : DaggerFragment() {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var aapsLogger: AAPSLogger

    private val disposable = CompositeDisposable()
    private val millsToThePast = T.hours(36).msecs()
    private var selectedItems: SparseArray<GlucoseValue> = SparseArray()
    private var toolbar: Toolbar? = null
    private var removeActionMode: ActionMode? = null

    private var _binding: BgsourceFragmentBinding? = null
    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        BgsourceFragmentBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbar = activity?.findViewById(R.id.toolbar)
        setHasOptionsMenu(true)

        binding.recyclerview.setHasFixedSize(true)
        binding.recyclerview.layoutManager = LinearLayoutManager(view.context)
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        val now = System.currentTimeMillis()
        disposable += repository
            .compatGetBgReadingsDataFromTime(now - millsToThePast, false)
            .observeOn(aapsSchedulers.main)
            .subscribe { list -> binding.recyclerview.adapter = RecyclerViewAdapter(list) }

        disposable += rxBus
            .toObservable(EventNewBG::class.java)
            .observeOn(aapsSchedulers.io)
            .debounce(1L, TimeUnit.SECONDS)
            .subscribe({
                           disposable += repository
                               .compatGetBgReadingsDataFromTime(now - millsToThePast, false)
                               .observeOn(aapsSchedulers.main)
                               .subscribe { list -> binding.recyclerview.swapAdapter(RecyclerViewAdapter(list), true) }
                       }, fabricPrivacy::logException)
    }

    @Synchronized
    override fun onPause() {
        removeActionMode?.finish()
        disposable.clear()
        super.onPause()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_bgsource, menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        // Only show when tab bg source is shown
        menu.findItem(R.id.nav_remove_items)?.isVisible = isResumed
        super.onPrepareOptionsMenu(menu)
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        removeActionMode?.finish()
        binding.recyclerview.adapter = null // avoid leaks
        _binding = null
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.nav_remove_items -> {
                if (toolbar != null) {
                    removeActionMode = toolbar?.startActionMode(RemoveActionModeCallback()) // in overview
                } else {
                    removeActionMode = activity?.startActionMode(RemoveActionModeCallback()) // in Single FragmentActivity
                }
                true
            }

            else                  -> false
        }
    }

    inner class RecyclerViewAdapter internal constructor(private var glucoseValues: List<GlucoseValue>) : RecyclerView.Adapter<RecyclerViewAdapter.GlucoseValuesViewHolder>() {

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): GlucoseValuesViewHolder {
            val v = LayoutInflater.from(viewGroup.context).inflate(R.layout.bgsource_item, viewGroup, false)
            return GlucoseValuesViewHolder(v)
        }

        override fun onBindViewHolder(holder: GlucoseValuesViewHolder, position: Int) {
            val glucoseValue = glucoseValues[position]
            holder.binding.ns.visibility = (glucoseValue.interfaceIDs.nightscoutId != null).toVisibility()
            holder.binding.invalid.visibility = (!glucoseValue.isValid).toVisibility()
            val newDay = position == 0 || !dateUtil.isSameDay(glucoseValue.timestamp, glucoseValues[position - 1].timestamp)
            holder.binding.date.visibility = newDay.toVisibility()
            holder.binding.date.text = if (newDay) dateUtil.dateStringRelative(glucoseValue.timestamp, rh) else ""
            holder.binding.time.text = dateUtil.timeString(glucoseValue.timestamp)
            holder.binding.value.text = glucoseValue.valueToUnitsString(profileFunction.getUnits())
            holder.binding.direction.setImageResource(glucoseValue.trendArrow.directionToIcon())
            if (position > 0) {
                val previous = glucoseValues[position - 1]
                val diff = previous.timestamp - glucoseValue.timestamp
                if (diff < T.secs(20).msecs())
                    holder.binding.root.setBackgroundColor(rh.gac(context, R.attr.errorAlertBackground))
            }
            fun updateSelection(selected: Boolean) {
                if (selected) {
                    selectedItems.put(position, glucoseValue)
                } else {
                    selectedItems.remove(position)
                }
                removeActionMode?.title = rh.gs(R.string.count_selected, selectedItems.size())
            }
            holder.binding.root.setOnLongClickListener {
                if (removeActionMode == null) {
                    removeActionMode = toolbar?.startActionMode(RemoveActionModeCallback())
                }
                holder.binding.cbRemove.toggle()
                updateSelection(holder.binding.cbRemove.isChecked)
                true
            }
            holder.binding.root.setOnClickListener {
                if (removeActionMode != null) {
                    holder.binding.cbRemove.toggle()
                    updateSelection(holder.binding.cbRemove.isChecked)
                }
            }
            holder.binding.cbRemove.setOnCheckedChangeListener { _, value -> updateSelection(value) }
            holder.binding.cbRemove.isChecked = selectedItems.get(position) != null
            holder.binding.cbRemove.visibility = (removeActionMode != null).toVisibility()
        }

        override fun getItemCount(): Int = glucoseValues.size

        inner class GlucoseValuesViewHolder(view: View) : RecyclerView.ViewHolder(view) {

            val binding = BgsourceItemBinding.bind(view)
        }
    }

    inner class RemoveActionModeCallback : ActionMode.Callback {

        override fun onCreateActionMode(mode: ActionMode, menu: Menu?): Boolean {
            mode.menuInflater.inflate(R.menu.menu_delete_selection, menu)
            selectedItems.clear()
            mode.title = rh.gs(R.string.count_selected, selectedItems.size())
            binding.recyclerview.adapter?.notifyDataSetChanged()
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?) = false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            return when (item.itemId) {
                R.id.remove_selected -> {
                    removeSelected()
                    true
                }

                else                 -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            removeActionMode = null
            binding.recyclerview.adapter?.notifyDataSetChanged()
        }
    }

    private fun getConfirmationText(): String {
        if (selectedItems.size() == 1) {
            val glucoseValue = selectedItems.valueAt(0)
            return dateUtil.dateAndTimeString(glucoseValue.timestamp) + "\n" + glucoseValue.valueToUnitsString(profileFunction.getUnits())
        }
        return rh.gs(R.string.confirm_remove_multiple_items, selectedItems.size())
    }

    private fun removeSelected() {
        if (selectedItems.size() > 0)
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, rh.gs(R.string.removerecord), getConfirmationText(), Runnable {
                    selectedItems.forEach { _, glucoseValue ->
                        val source = when ((activePlugin.activeBgSource as PluginBase).pluginDescription.pluginName) {
                            R.string.dexcom_app_patched -> Sources.Dexcom
                            R.string.eversense          -> Sources.Eversense
                            R.string.Glimp              -> Sources.Glimp
                            R.string.MM640g             -> Sources.MM640g
                            R.string.nsclientbg         -> Sources.NSClientSource
                            R.string.poctech            -> Sources.PocTech
                            R.string.tomato             -> Sources.Tomato
                            R.string.glunovo            -> Sources.Glunovo
                            R.string.xdrip              -> Sources.Xdrip
                            else                        -> Sources.Unknown
                        }
                        uel.log(
                            Action.BG_REMOVED, source,
                            ValueWithUnit.Timestamp(glucoseValue.timestamp)
                        )
                        repository.runTransactionForResult(InvalidateGlucoseValueTransaction(glucoseValue.id))
                            .doOnError { aapsLogger.error(LTag.DATABASE, "Error while invalidating BG value", it) }
                            .blockingGet()
                            .also { result -> result.invalidated.forEach { aapsLogger.debug(LTag.DATABASE, "Invalidated bg $it") } }
                    }
                    removeActionMode?.finish()
                })
            }
        else
            removeActionMode?.finish()
    }
}
