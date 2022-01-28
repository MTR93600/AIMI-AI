package info.nightscout.androidaps.plugins.insulin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.databinding.InsulinFragmentBinding
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject

class InsulinFragment : DaggerFragment() {

    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var rh: ResourceHelper

    private var _binding: InsulinFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        _binding = InsulinFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        binding.name.text = activePlugin.activeInsulin.friendlyName
        binding.comment.text = activePlugin.activeInsulin.comment
        binding.dia.text = rh.gs(R.string.dia) + ":  " + rh.gs(R.string.format_hours, activePlugin.activeInsulin.dia)
        binding.graph.show(activePlugin.activeInsulin)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}