package info.nightscout.source

import android.os.Bundle
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.util.forEach
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.android.support.DaggerFragment
import info.nightscout.core.extensions.directionToIcon
import info.nightscout.core.extensions.valueToUnitsString
import info.nightscout.core.ui.dialogs.OKDialog
import info.nightscout.core.utils.ActionModeHelper
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.database.entities.GlucoseValue
import info.nightscout.database.entities.UserEntry.Action
import info.nightscout.database.entities.UserEntry.Sources
import info.nightscout.database.entities.ValueWithUnit
import info.nightscout.database.impl.AppRepository
import info.nightscout.database.impl.transactions.InvalidateGlucoseValueTransaction
import info.nightscout.interfaces.logging.UserEntryLogger
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.plugin.PluginBase
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventNewBG
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.extensions.toVisibility
import info.nightscout.shared.extensions.toVisibilityKeepSpace
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import info.nightscout.source.databinding.SourceFragmentBinding
import info.nightscout.source.databinding.SourceItemBinding
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class BGSourceFragment : DaggerFragment(), MenuProvider {

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
    private lateinit var actionHelper: ActionModeHelper<GlucoseValue>
    private var _binding: SourceFragmentBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        SourceFragmentBinding.inflate(inflater, container, false).also {
            _binding = it
            actionHelper = ActionModeHelper(rh, activity, this)
            actionHelper.setUpdateListHandler { binding.recyclerview.adapter?.notifyDataSetChanged() }
            actionHelper.setOnRemoveHandler { handler -> removeSelected(handler) }
            requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
        }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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
        actionHelper.finish()
        disposable.clear()
        super.onPause()
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        actionHelper.onCreateOptionsMenu(menu, inflater)
        actionHelper.onPrepareOptionsMenu(menu)
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        binding.recyclerview.adapter = null // avoid leaks
        _binding = null
    }

    override fun onMenuItemSelected(item: MenuItem) =
        if (actionHelper.onOptionsItemSelected(item)) true
        else super.onContextItemSelected(item)

    inner class RecyclerViewAdapter internal constructor(private var glucoseValues: List<GlucoseValue>) : RecyclerView.Adapter<RecyclerViewAdapter.GlucoseValuesViewHolder>() {

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): GlucoseValuesViewHolder {
            val v = LayoutInflater.from(viewGroup.context).inflate(R.layout.source_item, viewGroup, false)
            return GlucoseValuesViewHolder(v)
        }

        override fun onBindViewHolder(holder: GlucoseValuesViewHolder, position: Int) {
            val glucoseValue = glucoseValues[position]
            holder.binding.ns.visibility = (glucoseValue.interfaceIDs.nightscoutId != null).toVisibilityKeepSpace()
            holder.binding.invalid.visibility = (!glucoseValue.isValid).toVisibility()
            val newDay = position == 0 || !dateUtil.isSameDay(glucoseValue.timestamp, glucoseValues[position - 1].timestamp)
            holder.binding.date.visibility = newDay.toVisibility()
            holder.binding.date.text = if (newDay) dateUtil.dateStringRelative(glucoseValue.timestamp, rh) else ""
            holder.binding.time.text = dateUtil.timeStringWithSeconds(glucoseValue.timestamp)
            holder.binding.value.text = glucoseValue.valueToUnitsString(profileFunction.getUnits())
            holder.binding.direction.setImageResource(glucoseValue.trendArrow.directionToIcon())
            if (position > 0) {
                val previous = glucoseValues[position - 1]
                val diff = previous.timestamp - glucoseValue.timestamp
                if (diff < T.secs(20).msecs())
                    holder.binding.root.setBackgroundColor(rh.gac(context, info.nightscout.core.ui.R.attr.bgsourceError))
            }

            holder.binding.root.setOnLongClickListener {
                if (actionHelper.startRemove()) {
                    holder.binding.cbRemove.toggle()
                    actionHelper.updateSelection(position, glucoseValue, holder.binding.cbRemove.isChecked)
                    return@setOnLongClickListener true
                }
                false
            }
            holder.binding.root.setOnClickListener {
                if (actionHelper.isRemoving) {
                    holder.binding.cbRemove.toggle()
                    actionHelper.updateSelection(position, glucoseValue, holder.binding.cbRemove.isChecked)
                }
            }
            holder.binding.cbRemove.setOnCheckedChangeListener { _, value ->
                actionHelper.updateSelection(position, glucoseValue, value)
            }
            holder.binding.cbRemove.isChecked = actionHelper.isSelected(position)
            holder.binding.cbRemove.visibility = actionHelper.isRemoving.toVisibility()
        }

        override fun getItemCount() = glucoseValues.size

        inner class GlucoseValuesViewHolder(view: View) : RecyclerView.ViewHolder(view) {

            val binding = SourceItemBinding.bind(view)
        }
    }

    private fun getConfirmationText(selectedItems: SparseArray<GlucoseValue>): String {
        if (selectedItems.size() == 1) {
            val glucoseValue = selectedItems.valueAt(0)
            return dateUtil.dateAndTimeString(glucoseValue.timestamp) + "\n" + glucoseValue.valueToUnitsString(profileFunction.getUnits())
        }
        return rh.gs(info.nightscout.core.ui.R.string.confirm_remove_multiple_items, selectedItems.size())
    }

    private fun removeSelected(selectedItems: SparseArray<GlucoseValue>) {
        activity?.let { activity ->
            OKDialog.showConfirmation(activity, rh.gs(info.nightscout.core.ui.R.string.removerecord), getConfirmationText(selectedItems), Runnable {
                selectedItems.forEach { _, glucoseValue ->
                    val source = when ((activePlugin.activeBgSource as PluginBase).pluginDescription.pluginName) {
                        R.string.dexcom_app_patched -> Sources.Dexcom
                        R.string.eversense          -> Sources.Eversense
                        R.string.glimp              -> Sources.Glimp
                        R.string.mm640g             -> Sources.MM640g
                        R.string.ns_client_bg       -> Sources.NSClientSource
                        R.string.poctech            -> Sources.PocTech
                        R.string.tomato             -> Sources.Tomato
                        R.string.glunovo            -> Sources.Glunovo
                        R.string.intelligo          -> Sources.Intelligo
                        R.string.source_xdrip       -> Sources.Xdrip
                        R.string.aidex              -> Sources.Aidex
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
                actionHelper.finish()
            })
        }
    }
}
