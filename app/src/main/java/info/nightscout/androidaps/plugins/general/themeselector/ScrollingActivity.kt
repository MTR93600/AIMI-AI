package info.nightscout.androidaps.plugins.general.themeselector

import android.R.attr
import android.annotation.SuppressLint
import android.graphics.Point
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.CompoundButton
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.skydoves.colorpickerview.ColorPickerDialog
import com.skydoves.colorpickerview.ColorPickerView
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import com.skydoves.colorpickerview.preference.ColorPickerPreferenceManager
import info.nightscout.androidaps.MainActivity
import info.nightscout.androidaps.R
import info.nightscout.androidaps.databinding.ThemeselectorScrollingFragmentBinding
import info.nightscout.androidaps.plugins.general.colorpicker.CustomFlag
import info.nightscout.androidaps.plugins.general.themeselector.adapter.RecyclerViewClickListener
import info.nightscout.androidaps.plugins.general.themeselector.adapter.ThemeAdapter
import info.nightscout.androidaps.plugins.general.themeselector.model.Theme
import info.nightscout.androidaps.plugins.general.themeselector.util.ThemeUtil.THEME_DARKSIDE
import info.nightscout.androidaps.plugins.general.themeselector.util.ThemeUtil.getThemeId
import info.nightscout.androidaps.plugins.general.themeselector.util.ThemeUtil.themeList
import info.nightscout.androidaps.plugins.general.themeselector.view.ThemeView
import info.nightscout.androidaps.plugins.general.themes.ThemeSwitcherPlugin
import java.util.*
import javax.inject.Inject

class ScrollingActivity : MainActivity(), View.OnClickListener {
    companion object {
        var mThemeList: MutableList<Theme> = ArrayList()
        var selectedTheme = 0

        init {
            selectedTheme = 0
        }
    }

    @Inject lateinit var themeSwitcherPlugin: ThemeSwitcherPlugin

    private lateinit var binding: ThemeselectorScrollingFragmentBinding

    private var actualTheme = 0
    private var mAdapter: ThemeAdapter? = null
    private var mBottomSheetBehavior: BottomSheetBehavior<*>? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ThemeselectorScrollingFragmentBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initBottomSheet()
        prepareThemeData()

        actualTheme = sp.getInt("theme", THEME_DARKSIDE)
        val themeView = findViewById<ThemeView>(R.id.theme_selected)
        if ( mThemeList.getOrNull(actualTheme) == null ) sp.putInt("theme", THEME_DARKSIDE)
        themeView.setTheme(mThemeList[actualTheme], actualTheme)
        setBackground()
    }

    private fun setBackground() {
        // get theme attribute
        val a = TypedValue()
        val drawable: Drawable
        theme.resolveAttribute(attr.windowBackground, a, true)
        if (a.type >= TypedValue.TYPE_FIRST_COLOR_INT && a.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            // windowBackground is a color
            drawable =  ColorDrawable(a.data)
        } else {
            // windowBackground is not a color, probably a drawable
           drawable = resources.getDrawable(a.resourceId, theme)
        }


        if ( sp.getString(R.string.key_use_dark_mode, "dark") == "dark")  {
            val cd = ColorDrawable(sp.getInt("darkBackgroundColor", info.nightscout.androidaps.core.R.color.background_dark))
            if ( !sp.getBoolean("backgroundcolor", true)) {
                binding.scrollingactivity.background =  cd
            } else {
                binding.scrollingactivity.background =  drawable
            }
        } else {
            val cd = ColorDrawable(sp.getInt("lightBackgroundColor", info.nightscout.androidaps.core.R.color.background_light))
            if ( !sp.getBoolean("backgroundcolor", true)) {
                binding.scrollingactivity.background =  cd
            } else {
                binding.scrollingactivity.background =  drawable
            }
        }
    }


    private fun initBottomSheet() {
        var nightMode = true
        if ( sp.getString(R.string.key_use_dark_mode, "dark") == "light") {
            nightMode = false
        }

        // init the bottom sheet behavior
        mBottomSheetBehavior = BottomSheetBehavior.from( binding.themeSelectorBottomLayout.bottomSheet)
        val backGround = sp.getBoolean("backgroundcolor", true)
        val switchCompatBackground = binding.themeSelectorBottomLayout.switchBackgroundimage
        switchCompatBackground.isChecked = backGround
        switchCompatBackground.setOnCheckedChangeListener { compoundButton: CompoundButton, b: Boolean ->
            sp.putBoolean("backgroundcolor", b)
            val delayTime = 200
            compoundButton.postDelayed(Runnable { changeTheme(sp.getInt("theme", THEME_DARKSIDE)) }, delayTime.toLong())
        }
        val switchCompat = binding.themeSelectorBottomLayout.switchDarkMode
        switchCompat.isChecked = nightMode
        switchCompat.setOnCheckedChangeListener { compoundButton: CompoundButton, b: Boolean ->
            if (b){
                sp.putString(R.string.key_use_dark_mode, "dark")
            } else {
                sp.putString(R.string.key_use_dark_mode, "light")
            }
            if ((mBottomSheetBehavior as BottomSheetBehavior<*>).getState() == BottomSheetBehavior.STATE_EXPANDED) {
                (mBottomSheetBehavior as BottomSheetBehavior<*>).setState(BottomSheetBehavior.STATE_EXPANDED)
            }
            themeSwitcherPlugin.switchTheme()
        }
        binding.themeSelectorBottomLayout.selectBackgroundcolordark.setBackgroundColor(sp.getInt("darkBackgroundColor", ContextCompat.getColor(this, info.nightscout.androidaps.core.R.color.background_dark)))
        binding.themeSelectorBottomLayout.selectBackgroundcolorlight.setBackgroundColor(sp.getInt("lightBackgroundColor", ContextCompat.getColor(this, info.nightscout.androidaps.core.R.color
            .background_light)))
        binding.themeSelectorBottomLayout.selectBackgroundcolordark.setOnClickListener(View.OnClickListener { selectColor("dark") })
        binding.themeSelectorBottomLayout.selectBackgroundcolorlight.setOnClickListener(View.OnClickListener { selectColor("light") })

        binding.themeSelectorBottomLayout.setDefaultColorDark.setOnClickListener(View.OnClickListener {
            sp.putInt("darkBackgroundColor", ContextCompat.getColor(this, R.color.background_dark))
            binding.themeSelectorBottomLayout.selectBackgroundcolordark.setBackgroundColor(getColor((R.color.background_dark)))
            val delayTime = 200
            binding.themeSelectorBottomLayout.selectBackgroundcolordark.postDelayed({ changeTheme(sp.getInt("theme", THEME_DARKSIDE)) }, delayTime.toLong())
        })

        binding.themeSelectorBottomLayout.setDefaultColorLight.setOnClickListener(View.OnClickListener {
            sp.putInt("lightBackgroundColor", ContextCompat.getColor(this, R.color.background_light))
            binding.themeSelectorBottomLayout.selectBackgroundcolorlight.setBackgroundColor(getColor((R.color.background_light)))
            val delayTime = 200
            binding.themeSelectorBottomLayout.selectBackgroundcolorlight.postDelayed({ changeTheme(sp.getInt("theme", THEME_DARKSIDE)) }, delayTime.toLong())
        })

        mAdapter = ThemeAdapter(sp, mThemeList, object : RecyclerViewClickListener {
            override fun onClick(view: View?, position: Int) {
                (mBottomSheetBehavior as BottomSheetBehavior<*>).setState(BottomSheetBehavior.STATE_EXPANDED)
                view!!.postDelayed({
                    val themeView = findViewById<ThemeView>(R.id.theme_selected)
                    themeView.setTheme(mThemeList[selectedTheme], getThemeId(selectedTheme))
                    changeTheme(selectedTheme)
                }, 500)
            }
        })
        val mLayoutManager: RecyclerView.LayoutManager = GridLayoutManager(applicationContext, 3)
        binding.themeSelectorBottomLayout.recyclerView.setLayoutManager(mLayoutManager)
        binding.themeSelectorBottomLayout.recyclerView.setItemAnimator(DefaultItemAnimator())
        binding.themeSelectorBottomLayout.recyclerView.setAdapter(mAdapter)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun prepareThemeData() {
        mThemeList.clear()
        mThemeList.addAll(themeList)
        mAdapter!!.notifyDataSetChanged()
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.fab -> when (mBottomSheetBehavior!!.state) {
                BottomSheetBehavior.STATE_HIDDEN    -> mBottomSheetBehavior!!.setState(BottomSheetBehavior.STATE_EXPANDED)
                BottomSheetBehavior.STATE_COLLAPSED -> mBottomSheetBehavior!!.setState(BottomSheetBehavior.STATE_EXPANDED)
                BottomSheetBehavior.STATE_EXPANDED  -> mBottomSheetBehavior!!.setState(BottomSheetBehavior.STATE_COLLAPSED)
            }
        }
    }

    private fun selectColor(lightOrDark: String) {
        val colorPickerDialog = ColorPickerDialog.Builder(this)
            .setTitle("Select Background Color")
            .setPreferenceName("MyColorPickerDialog")
            .setPositiveButton(getString(R.string.confirm),
                ColorEnvelopeListener { envelope, _ -> //setLayoutColor(envelope);
                    if (lightOrDark === "light") {
                        sp.putInt("lightBackgroundColor", envelope.color)
                        binding.themeSelectorBottomLayout.selectBackgroundcolorlight.setBackgroundColor(envelope.color)
                        val delayTime = 200
                        binding.themeSelectorBottomLayout.selectBackgroundcolorlight.postDelayed({ changeTheme(sp.getInt("theme", THEME_DARKSIDE)) }, delayTime.toLong())
                    } else if (lightOrDark === "dark") {
                        sp.putInt("darkBackgroundColor", envelope.color)
                        binding.themeSelectorBottomLayout.selectBackgroundcolordark.setBackgroundColor(envelope.color)
                        val delayTime = 200
                        binding.themeSelectorBottomLayout.selectBackgroundcolordark.postDelayed({ changeTheme(sp.getInt("theme", THEME_DARKSIDE)) }, delayTime.toLong())
                    }
                })
            .setNegativeButton(getString(R.string.cancel)
            ) { dialogInterface, _ -> dialogInterface.dismiss() }
            .attachAlphaSlideBar(false) // default is true. If false, do not show the AlphaSlideBar.
            .attachBrightnessSlideBar(true) // default is true. If false, do not show the BrightnessSlideBar.
            .setBottomSpace(12) // set bottom space between the last slidebar and buttons.

        val colorPickerView: ColorPickerView = colorPickerDialog.colorPickerView
        colorPickerView.setFlagView(CustomFlag(this, R.layout.colorpicker_flagview)) // sets a custom flagView
        colorPickerView.preferenceName = "AAPS-ColorPicker"
        colorPickerView.setLifecycleOwner(this)

        val manager = ColorPickerPreferenceManager.getInstance(this)
        if (lightOrDark === "light") {
            manager.setColor("AAPS-ColorPicker", sp.getInt("lightBackgroundColor", ContextCompat.getColor(this, info.nightscout.androidaps.core.R.color.background_light)))
        } else if (lightOrDark === "dark") {
            manager.setColor("AAPS-ColorPicker", sp.getInt("darkBackgroundColor", ContextCompat.getColor(this, info.nightscout.androidaps.core.R.color.background_dark)))
        }
        var point = Point(0,0)
        manager.getSelectorPosition("AAPS-ColorPicker",point)
        if(point === Point(0,0)) manager.setSelectorPosition("AAPS-ColorPicker", Point(530, 520))
        colorPickerDialog.show()

        setBackground()
    }
}