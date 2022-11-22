package info.nightscout.androidaps.interfaces.stats

import android.content.Context
import android.widget.TableLayout

interface DexcomTirCalculator {

    fun calculate(): DexcomTIR
    fun stats(context: Context): TableLayout
}