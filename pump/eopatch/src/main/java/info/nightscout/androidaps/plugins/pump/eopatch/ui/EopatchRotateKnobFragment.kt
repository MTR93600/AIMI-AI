package info.nightscout.androidaps.plugins.pump.eopatch.ui

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.ViewModelProvider
import info.nightscout.androidaps.plugins.pump.eopatch.R
import info.nightscout.androidaps.plugins.pump.eopatch.code.PatchStep
import info.nightscout.androidaps.plugins.pump.eopatch.databinding.FragmentEopatchRotateKnobBinding
import info.nightscout.androidaps.plugins.pump.eopatch.ui.viewmodel.EopatchViewModel
import info.nightscout.androidaps.utils.ToastUtils

class EopatchRotateKnobFragment : EoBaseFragment<FragmentEopatchRotateKnobBinding>() {

    companion object {
        fun newInstance(): EopatchRotateKnobFragment = EopatchRotateKnobFragment()
    }

    override fun getLayoutId(): Int = R.layout.fragment_eopatch_rotate_knob

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.apply {
            viewModel = ViewModelProvider(requireActivity(), viewModelFactory).get(EopatchViewModel::class.java)
            viewModel?.apply {
                initPatchStep()

                if (patchStep.value == PatchStep.ROTATE_KNOB_NEEDLE_INSERTION_ERROR) {
                    btnNegative.visibility = View.VISIBLE
                    guidelineButton.setGuidelinePercent(0.4f)

                    btnPositive.apply {
                        updateLayoutParams<ViewGroup.MarginLayoutParams> { leftMargin = 3 }
                        text = getString(R.string.retry)
                    }
                    textPatchRotateKnobDesc.visibility = View.VISIBLE
                    layoutNeedleInsertionError.visibility = View.VISIBLE
                    textRotateKnobDesc2.visibility = View.GONE
                    textRotateKnobDesc2NeedleInsertionError.visibility = View.VISIBLE
                    textActivationErrorDesc.visibility = View.GONE
                }

                setupStep.observe(viewLifecycleOwner) {
                    when (it) {
                        EopatchViewModel.SetupStep.NEEDLE_SENSING_FAILED -> {
                            checkCommunication({ startNeedleSensing() })
                        }
                        EopatchViewModel.SetupStep.ACTIVATION_FAILED     -> {
                            btnNegative.visibility = View.VISIBLE
                            guidelineButton.setGuidelinePercent(0.4f)

                            btnPositive.apply {
                                updateLayoutParams<ViewGroup.MarginLayoutParams> { leftMargin = 3 }
                                text = getString(R.string.retry)
                            }
                            textPatchRotateKnobDesc.visibility = View.GONE
                            textRotateKnobDesc2.visibility = View.GONE
                            textRotateKnobDesc2NeedleInsertionError.visibility = View.VISIBLE
                            textActivationErrorDesc.visibility = View.VISIBLE
                            ToastUtils.errorToast(requireContext(), "Activation failed!")
                        }
                        else                                             -> Unit
                    }
                }
            }
        }
    }
}