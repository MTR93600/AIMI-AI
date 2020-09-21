package info.nightscout.androidaps.plugins.profile.ns

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.profile.ns.events.EventNSProfileUpdateGUI
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.extensions.plusAssign
import info.nightscout.androidaps.utils.resources.ResourceHelper
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.close.*
import kotlinx.android.synthetic.main.nsprofile_fragment.*
import kotlinx.android.synthetic.main.profileviewer_fragment.*
import javax.inject.Inject

class NSProfileFragment : DaggerFragment() {

    @Inject lateinit var treatmentsPlugin: TreatmentsPlugin
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var nsProfilePlugin: NSProfilePlugin

    private var disposable: CompositeDisposable = CompositeDisposable()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.nsprofile_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        close.visibility = View.GONE // not needed for fragment

        nsprofile_profileswitch.setOnClickListener {
            val name = nsprofile_spinner.selectedItem?.toString() ?: ""
            nsProfilePlugin.profile?.let { store ->
                store.getSpecificProfile(name)?.let {
                    activity?.let { activity ->
                        OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.nsprofile),
                            resourceHelper.gs(R.string.activate_profile) + ": " + name + " ?", Runnable {
                            treatmentsPlugin.doProfileSwitch(store, name, 0, 100, 0, DateUtil.now())
                        })
                    }
                }
            }
        }

        nsprofile_spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
                profileview_invalidprofile.visibility = View.VISIBLE
                profileview_noprofile.visibility = View.VISIBLE
                profileview_units.text = ""
                profileview_dia.text = ""
                profileview_activeprofile.text = ""
                profileview_ic.text = ""
                profileview_isf.text = ""
                profileview_basal.text = ""
                profileview_basaltotal.text = ""
                profileview_target.text = ""
                nsprofile_profileswitch.visibility = View.GONE
            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val name = nsprofile_spinner.getItemAtPosition(position).toString()

                nsprofile_profileswitch.visibility = View.GONE

                nsProfilePlugin.profile?.let { store ->
                    store.getSpecificProfile(name)?.let { profile ->
                        profileview_units.text = profile.units
                        profileview_dia.text = resourceHelper.gs(R.string.format_hours, profile.dia)
                        profileview_activeprofile.text = name
                        profileview_ic.text = profile.icList
                        profileview_isf.text = profile.isfList
                        profileview_basal.text = profile.basalList
                        profileview_basaltotal.text = String.format(resourceHelper.gs(R.string.profile_total), DecimalFormatter.to2Decimal(profile.baseBasalSum()))
                        profileview_target.text = profile.targetList
                        basal_graph.show(profile)
                        if (profile.isValid("NSProfileFragment")) {
                            profileview_invalidprofile.visibility = View.GONE
                            nsprofile_profileswitch.visibility = View.VISIBLE
                        } else {
                            profileview_invalidprofile.visibility = View.VISIBLE
                            nsprofile_profileswitch.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable += rxBus
            .toObservable(EventNSProfileUpdateGUI::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateGUI() }, { fabricPrivacy.logException(it) })
        updateGUI()
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    @Synchronized
    fun updateGUI() {
        if (profileview_noprofile == null) return
        profileview_noprofile.visibility = View.VISIBLE

        nsProfilePlugin.profile?.let { profileStore ->
            val profileList = profileStore.getProfileList()
            val adapter = ArrayAdapter(context!!, R.layout.spinner_centered, profileList)
            nsprofile_spinner.adapter = adapter
            // set selected to actual profile
            for (p in profileList.indices) {
                if (profileList[p] == profileFunction.getProfileName())
                    nsprofile_spinner.setSelection(p)
            }
            profileview_noprofile.visibility = View.GONE
        }
    }
}
