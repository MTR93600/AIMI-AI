package info.nightscout.androidaps.plugins.general.food

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.Food
import info.nightscout.androidaps.database.entities.UserEntry.Action
import info.nightscout.androidaps.database.entities.UserEntry.Sources
import info.nightscout.androidaps.database.transactions.InvalidateFoodTransaction
import info.nightscout.androidaps.databinding.FoodFragmentBinding
import info.nightscout.androidaps.databinding.FoodItemBinding
import info.nightscout.androidaps.dialogs.WizardDialog
import info.nightscout.androidaps.events.EventFoodDatabaseChanged
import info.nightscout.androidaps.extensions.toVisibility
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.protection.ProtectionCheck
import info.nightscout.androidaps.interfaces.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.androidaps.utils.ui.UIRunnable
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class FoodFragment : DaggerFragment() {

    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var protectionCheck: ProtectionCheck

    private val disposable = CompositeDisposable()
    private var unfiltered: List<Food> = arrayListOf()
    private var filtered: MutableList<Food> = arrayListOf()

    private var _binding: FoodFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FoodFragmentBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerview.setHasFixedSize(true)
        binding.recyclerview.layoutManager = LinearLayoutManager(view.context)

        binding.filterInputLayout.setEndIconOnClickListener {
            binding.filter.setText("")
            binding.categoryList.setText(rh.gs(R.string.none), false)
            binding.subcategoryList.setText(rh.gs(R.string.none), false)
            filterData()
        }
        binding.categoryList.onItemClickListener = AdapterView.OnItemClickListener { _, _, _, _ -> fillSubcategories(); filterData() }
        binding.subcategoryList.onItemClickListener = AdapterView.OnItemClickListener { _, _, _, _ -> filterData() }
        binding.filter.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                filterData()
            }

            override fun afterTextChanged(s: Editable) {}
        })
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable += rxBus
            .toObservable(EventFoodDatabaseChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .debounce(1L, TimeUnit.SECONDS)
            .subscribe({ swapAdapter() }, fabricPrivacy::logException)
        swapAdapter()
    }

    private fun swapAdapter() {
        disposable += repository
            .getFoodData()
            .observeOn(aapsSchedulers.main)
            .subscribe { list ->
                unfiltered = list
                fillCategories()
                fillSubcategories()
                filterData()
                binding.recyclerview.swapAdapter(RecyclerViewAdapter(filtered), true)
            }
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun fillCategories() {
        val catSet: MutableSet<CharSequence> = HashSet()
        for (f in unfiltered) {
            val category = f.category
            if (!category.isNullOrBlank()) catSet.add(category)
        }
        // make it unique
        val categories = ArrayList(catSet)
        categories.add(0, rh.gs(R.string.none))
        context?.let { context ->
            binding.categoryList.setAdapter(ArrayAdapter(context, R.layout.spinner_centered, categories))
            binding.categoryList.setText(rh.gs(R.string.none), false)
        }
    }

    private fun fillSubcategories() {
        val categoryFilter = binding.categoryList.text.toString()
        val subCatSet: MutableSet<CharSequence> = HashSet()
        if (categoryFilter != rh.gs(R.string.none)) {
            for (f in unfiltered) {
                if (f.category != null && f.category == categoryFilter) {
                    val subCategory = f.subCategory
                    if (!subCategory.isNullOrEmpty()) subCatSet.add(subCategory)
                }
            }
        }
        // make it unique
        val subcategories = ArrayList(subCatSet)
        subcategories.add(0, rh.gs(R.string.none))
        context?.let { context ->
            binding.subcategoryList.setAdapter(ArrayAdapter(context, R.layout.spinner_centered, subcategories))
            binding.subcategoryList.setText(rh.gs(R.string.none), false)
        }
    }

    private fun filterData() {
        val textFilter = binding.filter.text.toString()
        val categoryFilter = binding.categoryList.text.toString()
        val subcategoryFilter = binding.subcategoryList.text.toString()
        val newFiltered = ArrayList<Food>()
        for (f in unfiltered) {
            if (f.category == null || f.subCategory == null) continue
            if (subcategoryFilter != rh.gs(R.string.none) && f.subCategory != subcategoryFilter) continue
            if (categoryFilter != rh.gs(R.string.none) && f.category != categoryFilter) continue
            if (textFilter != "" && !f.name.lowercase(Locale.getDefault()).contains(textFilter.lowercase(Locale.getDefault()))) continue
            newFiltered.add(f)
        }
        filtered = newFiltered
        binding.recyclerview.swapAdapter(RecyclerViewAdapter(filtered), true)
    }

    fun Int?.isNotZero(): Boolean = this != null && this != 0

    inner class RecyclerViewAdapter internal constructor(private var foodList: List<Food>) : RecyclerView.Adapter<RecyclerViewAdapter.FoodsViewHolder>() {

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): FoodsViewHolder {
            val v = LayoutInflater.from(viewGroup.context).inflate(R.layout.food_item, viewGroup, false)
            return FoodsViewHolder(v)
        }

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: FoodsViewHolder, position: Int) {
            val food = foodList[position]
            holder.binding.name.text = food.name
            holder.binding.portion.text = food.portion.toString() + food.unit
            holder.binding.carbs.text = food.carbs.toString() + rh.gs(R.string.shortgramm)
            holder.binding.fat.text = rh.gs(R.string.shortfat) + ": " + food.fat + rh.gs(R.string.shortgramm)
            holder.binding.fat.visibility = food.fat.isNotZero().toVisibility()
            holder.binding.protein.text = rh.gs(R.string.shortprotein) + ": " + food.protein + rh.gs(R.string.shortgramm)
            holder.binding.protein.visibility = food.protein.isNotZero().toVisibility()
            holder.binding.energy.text = rh.gs(R.string.shortenergy) + ": " + food.energy + rh.gs(R.string.shortkilojoul)
            holder.binding.energy.visibility = food.energy.isNotZero().toVisibility()
            holder.binding.icRemove.tag = food
            holder.binding.icCalculator.tag = food
        }

        override fun getItemCount(): Int = foodList.size

        inner class FoodsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

            val binding = FoodItemBinding.bind(itemView)

            init {
                binding.icRemove.setOnClickListener { v: View ->
                    val food = v.tag as Food
                    activity?.let { activity ->
                        OKDialog.showConfirmation(activity, rh.gs(R.string.removerecord) + "\n" + food.name, {
                            uel.log(Action.FOOD_REMOVED, Sources.Food, food.name)
                            disposable += repository.runTransactionForResult(InvalidateFoodTransaction(food.id))
                                .subscribe(
                                    { aapsLogger.error(LTag.DATABASE, "Invalidated food $it") },
                                    { aapsLogger.error(LTag.DATABASE, "Error while invalidating food", it) }
                                )
                        }, null)
                    }
                }
                binding.icCalculator.setOnClickListener { v: View ->
                    val food = v.tag as Food
                    activity?.let { activity ->
                        protectionCheck.queryProtection(activity, ProtectionCheck.Protection.BOLUS, UIRunnable {
                            if (isAdded)
                                WizardDialog().also { dialog ->
                                    dialog.arguments = Bundle().also { bundle ->
                                        bundle.putDouble("carbs_input", food.carbs.toDouble())
                                        bundle.putString("notes_input", " ${food.name} - ${food.carbs}g")
                                    }
                                }.show(childFragmentManager, "Food Item")
                        })
                    }
                }
            }
        }
    }
}