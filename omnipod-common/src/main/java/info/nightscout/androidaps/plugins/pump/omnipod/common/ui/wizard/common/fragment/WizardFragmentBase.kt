package info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.common.fragment

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.navigation.fragment.findNavController
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.plugins.pump.omnipod.common.R
import info.nightscout.androidaps.plugins.pump.omnipod.common.databinding.OmnipodCommonWizardBaseFragmentBinding
import info.nightscout.androidaps.plugins.pump.omnipod.common.databinding.OmnipodCommonWizardProgressIndicationBinding
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.common.activity.OmnipodWizardActivityBase
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.common.viewmodel.ViewModelBase
import kotlin.math.roundToInt

abstract class WizardFragmentBase : DaggerFragment() {

    protected lateinit var viewModel: ViewModelBase

    var _binding: OmnipodCommonWizardBaseFragmentBinding? = null
    var _progressIndicationBinding: OmnipodCommonWizardProgressIndicationBinding? = null

    // These properties are only valid between onCreateView and
    // onDestroyView.
    val binding get() = _binding!!
    val progressIndicationBinding get() = _progressIndicationBinding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        OmnipodCommonWizardBaseFragmentBinding.inflate(inflater, container, false).also {
            _binding = it
            _progressIndicationBinding = OmnipodCommonWizardProgressIndicationBinding.bind(it.root)

            it.fragmentContent.layoutResource = getLayoutId()
            it.fragmentContent.inflate()
        }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.fragmentTitle.setText(getTitleId())

        val nextPage = getNextPageActionId()

        if (nextPage == null) {
            binding.navButtonsLayout.buttonNext.text = getString(R.string.omnipod_common_wizard_button_finish)
            binding.navButtonsLayout.buttonNext.backgroundTintList = ColorStateList.valueOf(resources.getColor(R.color.omnipod_wizard_finish_button, context?.theme))
        }

        updateProgressIndication()

        binding.navButtonsLayout.buttonNext.setOnClickListener {
            if (nextPage == null) {
                activity?.finish()
            } else {
                findNavController().navigate(nextPage)
            }
        }

        binding.navButtonsLayout.buttonCancel.setOnClickListener {
            (activity as? OmnipodWizardActivityBase)?.exitActivityAfterConfirmation()
        }
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun updateProgressIndication() {
        (activity as? OmnipodWizardActivityBase)?.let {
            val numberOfSteps = it.getActualNumberOfSteps()

            val currentFragment = getIndex() - (it.getTotalDefinedNumberOfSteps() - numberOfSteps)
            val progressPercentage = (currentFragment / numberOfSteps.toDouble() * 100).roundToInt()

            progressIndicationBinding.progressIndication.progress = progressPercentage
        }
    }

    @LayoutRes
    protected abstract fun getLayoutId(): Int

    @IdRes
    protected abstract fun getNextPageActionId(): Int?

    @StringRes
    protected fun getTitleId(): Int = viewModel.getTitleId()

    @StringRes protected fun getTextId(): Int = viewModel.getTextId()

    protected abstract fun getIndex(): Int
}