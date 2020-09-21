package info.nightscout.androidaps.setupwizard

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.setupwizard.elements.SWItem
import info.nightscout.androidaps.utils.resources.ResourceHelper
import java.util.*
import javax.inject.Inject

class SWScreen(val injector: HasAndroidInjector, private var header: Int) {

    @Inject lateinit var resourceHelper: ResourceHelper

    var items: MutableList<SWItem> = ArrayList()
    var validator: SWValidator? = null
    var visibility: SWValidator? = null
    var skippable = false

    init {
        injector.androidInjector().inject(this)
    }

    fun getHeader(): String {
        return resourceHelper.gs(header)
    }

    fun skippable(skippable: Boolean): SWScreen {
        this.skippable = skippable
        return this
    }

    fun add(newItem: SWItem): SWScreen {
        items.add(newItem)
        return this
    }

    fun validator(validator: SWValidator): SWScreen {
        this.validator = validator
        return this
    }

    fun visibility(visibility: SWValidator): SWScreen {
        this.visibility = visibility
        return this
    }

    fun processVisibility() {
        for (i in items) i.processVisibility()
    }
}