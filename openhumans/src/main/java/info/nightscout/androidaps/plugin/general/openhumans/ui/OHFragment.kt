package info.nightscout.androidaps.plugin.general.openhumans.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.lifecycle.LiveData
import com.google.android.material.button.MaterialButton
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.plugin.general.openhumans.R
import info.nightscout.androidaps.plugin.general.openhumans.OpenHumansState
import info.nightscout.androidaps.plugin.general.openhumans.OpenHumansUploader
import javax.inject.Inject

class OHFragment : DaggerFragment() {

    @Inject
    internal lateinit var stateLiveData: LiveData<OpenHumansState?>
    @Inject
    internal lateinit var plugin: OpenHumansUploader

    private lateinit var setup: MaterialButton
    private lateinit var logout: MaterialButton
    private lateinit var uploadNow: MaterialButton
    private lateinit var info: TextView
    private lateinit var memberId: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val contextWrapper = ContextThemeWrapper(requireActivity(), R.style.OpenHumans)
        val wrappedInflater = inflater.cloneInContext(contextWrapper)
        val view = wrappedInflater.inflate(R.layout.fragment_open_humans_new, container, false)
        setup = view.findViewById(R.id.setup)
        setup.setOnClickListener { startActivity(Intent(context, OHLoginActivity::class.java)) }
        logout = view.findViewById(R.id.logout)
        logout.setOnClickListener { plugin.logout() }
        info = view.findViewById(R.id.info)
        memberId = view.findViewById(R.id.member_id)
        uploadNow = view.findViewById(R.id.upload_now)
        uploadNow.setOnClickListener { plugin.uploadNow() }
        return view
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        stateLiveData.observe(this) { state ->
            if (state == null) {
                setup.visibility = View.VISIBLE
                logout.visibility = View.GONE
                memberId.visibility = View.GONE
                uploadNow.visibility = View.GONE
                info.setText(R.string.not_setup_info)
            } else {
                setup.visibility = View.GONE
                logout.visibility = View.VISIBLE
                memberId.visibility = View.VISIBLE
                uploadNow.visibility = View.VISIBLE
                memberId.text = getString(R.string.project_member_id, state.projectMemberId)
                info.setText(R.string.setup_completed_info)
            }
        }
    }

}