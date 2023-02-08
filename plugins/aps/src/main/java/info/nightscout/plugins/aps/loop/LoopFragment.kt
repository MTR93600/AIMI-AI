package info.nightscout.plugins.aps.loop

import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import dagger.android.support.DaggerFragment
import info.nightscout.core.pump.toHtml
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.interfaces.aps.Loop
import info.nightscout.interfaces.constraints.Constraint
import info.nightscout.interfaces.utils.HtmlHelper
import info.nightscout.plugins.aps.R
import info.nightscout.plugins.aps.databinding.LoopFragmentBinding
import info.nightscout.plugins.aps.loop.events.EventLoopSetLastRunGui
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventLoopUpdateGui
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class LoopFragment : DaggerFragment(), MenuProvider {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var sp: SP
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var loop: Loop
    @Inject lateinit var dateUtil: DateUtil

    @Suppress("PrivatePropertyName")
    private val ID_MENU_RUN = 501

    private var handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
    private var disposable: CompositeDisposable = CompositeDisposable()

    private var _binding: LoopFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        LoopFragmentBinding.inflate(inflater, container, false).also {
            _binding = it
            requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
        }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.swipeRefresh.setColorSchemeColors(rh.gac(context, android.R.attr.colorPrimaryDark), rh.gac(context, android.R.attr.colorPrimary), rh.gac(context,com.google.android.material.R.attr.colorSecondary))
        binding.swipeRefresh.setOnRefreshListener {
            binding.lastrun.text = rh.gs(R.string.executing)
            handler.post {
                loop.invoke("Loop swipe refresh", true)
            }
        }
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        menu.add(Menu.FIRST, ID_MENU_RUN, 0, rh.gs(R.string.run_now)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.setGroupDividerEnabled(true)
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            ID_MENU_RUN -> {
                binding.lastrun.text = rh.gs(R.string.executing)
                handler.post { loop.invoke("Loop menu", true) }
                true
            }

            else        -> false
        }

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable += rxBus
            .toObservable(EventLoopUpdateGui::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           updateGUI()
                       }, fabricPrivacy::logException)

        disposable += rxBus
            .toObservable(EventLoopSetLastRunGui::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           clearGUI()
                           binding.lastrun.text = it.text
                       }, fabricPrivacy::logException)

        updateGUI()
        sp.putBoolean(info.nightscout.core.utils.R.string.key_objectiveuseloop, true)
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
        handler.removeCallbacksAndMessages(null)
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    @Synchronized
    fun updateGUI() {
        if (_binding == null) return
        loop.lastRun?.let {
            binding.request.text = it.request?.toSpanned() ?: ""
            binding.constraintsprocessed.text = it.constraintsProcessed?.toSpanned() ?: ""
            binding.source.text = it.source ?: ""
            binding.lastrun.text = dateUtil.dateAndTimeString(it.lastAPSRun)
            binding.smbrequestTime.text = dateUtil.dateAndTimeAndSecondsString(it.lastSMBRequest)
            binding.smbexecutionTime.text = dateUtil.dateAndTimeAndSecondsString(it.lastSMBEnact)
            binding.tbrrequestTime.text = dateUtil.dateAndTimeAndSecondsString(it.lastTBRRequest)
            binding.tbrexecutionTime.text = dateUtil.dateAndTimeAndSecondsString(it.lastTBREnact)

            binding.tbrsetbypump.text = it.tbrSetByPump?.let { tbrSetByPump -> HtmlHelper.fromHtml(tbrSetByPump.toHtml(rh)) }
                ?: ""
            binding.smbsetbypump.text = it.smbSetByPump?.let { smbSetByPump -> HtmlHelper.fromHtml(smbSetByPump.toHtml(rh)) }
                ?: ""

            var constraints =
                it.constraintsProcessed?.let { constraintsProcessed ->
                    val allConstraints = Constraint(0.0)
                    constraintsProcessed.rateConstraint?.let { rateConstraint -> allConstraints.copyReasons(rateConstraint) }
                    constraintsProcessed.smbConstraint?.let { smbConstraint -> allConstraints.copyReasons(smbConstraint) }
                    allConstraints.getMostLimitedReasons(aapsLogger)
                } ?: ""
            constraints += loop.closedLoopEnabled?.getReasons(aapsLogger) ?: ""
            binding.constraints.text = constraints
            binding.swipeRefresh.isRefreshing = false
        }
    }

    @Synchronized
    private fun clearGUI() {
        binding.request.text = ""
        binding.constraints.text = ""
        binding.constraintsprocessed.text = ""
        binding.source.text = ""
        binding.lastrun.text = ""
        binding.smbrequestTime.text = ""
        binding.smbexecutionTime.text = ""
        binding.tbrrequestTime.text = ""
        binding.tbrexecutionTime.text = ""
        binding.tbrsetbypump.text = ""
        binding.smbsetbypump.text = ""
        binding.swipeRefresh.isRefreshing = false
    }
}
