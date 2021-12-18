package info.nightscout.androidaps.activities

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.text.TextUtils
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.core.databinding.ActivityTddStatsBinding
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.TotalDailyDose
import info.nightscout.androidaps.events.EventDanaRSyncStatus
import info.nightscout.androidaps.events.EventPumpStatusChanged
import info.nightscout.androidaps.extensions.total
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.CommandQueue
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.shared.SafeParse
import info.nightscout.shared.sharedPreferences.SP
import io.reactivex.disposables.CompositeDisposable
import java.text.DateFormat
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import kotlin.math.min
import kotlin.math.roundToInt

class TDDStatsActivity : NoSplashAppCompatActivity() {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var sp: SP
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var aapsSchedulers: AapsSchedulers

    private lateinit var binding: ActivityTddStatsBinding
    private val disposable = CompositeDisposable()

    lateinit var tbb: String
    private var magicNumber = 0.0
    private var decimalFormat: DecimalFormat = DecimalFormat("0.000")
    private var historyList: MutableList<TotalDailyDose> = mutableListOf()
    private var dummies: MutableList<TotalDailyDose> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTddStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        binding.connectionStatus.visibility = View.GONE
        binding.message.visibility = View.GONE
        binding.totalBaseBasal2.isEnabled = false
        binding.totalBaseBasal2.isClickable = false
        binding.totalBaseBasal2.isFocusable = false
        binding.totalBaseBasal2.inputType = 0
        tbb = sp.getString("TBB", "10.00")
        val profile = profileFunction.getProfile()
        if (profile != null) {
            val cppTBB = profile.baseBasalSum()
            tbb = decimalFormat.format(cppTBB)
            sp.putString("TBB", tbb)
        }
        binding.totalBaseBasal.setText(tbb)
        if (!activePlugin.activePump.pumpDescription.needsManualTDDLoad) binding.reload.visibility = View.GONE

        // stats table

        // add stats headers to tables
        binding.mainTable.addView(
            TableRow(this).also { trHead ->
                trHead.setBackgroundColor(Color.DKGRAY)
                trHead.layoutParams = TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT)
                trHead.addView(TextView(this).also { labelDate ->
                    labelDate.text = rh.gs(R.string.date)
                    labelDate.setTextColor(Color.WHITE)
                })
                trHead.addView(TextView(this).also { labelBasalRate ->
                    labelBasalRate.text = rh.gs(R.string.basalrate)
                    labelBasalRate.setTextColor(Color.WHITE)
                })
                trHead.addView(TextView(this).also { labelBolus ->
                    labelBolus.text = rh.gs(R.string.bolus)
                    labelBolus.setTextColor(Color.WHITE)
                })
                trHead.addView(TextView(this).also { labelTdd ->
                    labelTdd.text = rh.gs(R.string.tdd)
                    labelTdd.setTextColor(Color.WHITE)
                })
                trHead.addView(TextView(this).also { labelRatio ->
                    labelRatio.text = rh.gs(R.string.ratio)
                    labelRatio.setTextColor(Color.WHITE)
                })
            }, TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT)
        )

        // cumulative table
        binding.cumulativeTable.addView(
            TableRow(this).also { ctrHead ->
                ctrHead.setBackgroundColor(Color.DKGRAY)
                ctrHead.layoutParams = TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT)
                ctrHead.addView(TextView(this).also { labelCumAmountDays ->
                    labelCumAmountDays.text = rh.gs(R.string.amount_days)
                    labelCumAmountDays.setTextColor(Color.WHITE)
                })
                ctrHead.addView(TextView(this).also { labelCumTdd ->
                    labelCumTdd.text = rh.gs(R.string.tdd)
                    labelCumTdd.setTextColor(Color.WHITE)
                })
                ctrHead.addView(TextView(this).also { labelCumRatio ->
                    labelCumRatio.text = rh.gs(R.string.ratio)
                    labelCumRatio.setTextColor(Color.WHITE)
                })
            }, TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT)
        )

        // exponential table
        binding.expweightTable.addView(
            TableRow(this).also { etrHead ->
                etrHead.setBackgroundColor(Color.DKGRAY)
                etrHead.layoutParams = TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT)
                etrHead.addView(TextView(this).also { labelExpWeight ->
                    labelExpWeight.text = rh.gs(R.string.weight)
                    labelExpWeight.setTextColor(Color.WHITE)
                })
                etrHead.addView(TextView(this).also { labelExpTdd ->
                    labelExpTdd.text = rh.gs(R.string.tdd)
                    labelExpTdd.setTextColor(Color.WHITE)
                })
                etrHead.addView(TextView(this).also { labelExpRatio ->
                    labelExpRatio.text = rh.gs(R.string.ratio)
                    labelExpRatio.setTextColor(Color.WHITE)
                })
            }, TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT)
        )

        binding.reload.setOnClickListener {
            binding.reload.visibility = View.GONE
            binding.connectionStatus.visibility = View.VISIBLE
            binding.message.visibility = View.VISIBLE
            binding.message.text = rh.gs(R.string.warning_Message)
            commandQueue.loadTDDs(object : Callback() {
                override fun run() {
                    loadDataFromDB()
                    runOnUiThread {
                        binding.reload.visibility = View.VISIBLE
                        binding.connectionStatus.visibility = View.GONE
                        binding.message.visibility = View.GONE
                    }
                }
            })
        }
        binding.totalBaseBasal.setOnEditorActionListener { _: TextView?, actionId: Int, _: KeyEvent? ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                binding.totalBaseBasal.clearFocus()
                return@setOnEditorActionListener true
            }
            false
        }
        binding.totalBaseBasal.setOnFocusChangeListener { _: View?, hasFocus: Boolean ->
            if (hasFocus) {
                binding.totalBaseBasal.text.clear()
            } else {
                sp.putString("TBB", binding.totalBaseBasal.text.toString())
                tbb = sp.getString("TBB", "")
                loadDataFromDB()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(binding.totalBaseBasal.windowToken, 0)
            }
        }
        loadDataFromDB()
    }

    override fun onResume() {
        super.onResume()
        disposable.add(
            rxBus
                .toObservable(EventPumpStatusChanged::class.java)
                .observeOn(aapsSchedulers.main)
                .subscribe({ event -> binding.connectionStatus.text = event.getStatus(rh) }, fabricPrivacy::logException)
        )
        disposable.add(
            rxBus
                .toObservable(EventDanaRSyncStatus::class.java)
                .observeOn(aapsSchedulers.main)
                .subscribe({ event ->
                               aapsLogger.debug("EventDanaRSyncStatus: " + event.message)
                               binding.connectionStatus.text = event.message
                           }, fabricPrivacy::logException)
        )
    }

    override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val myView = currentFocus
            if (myView is EditText) {
                val rect = Rect()
                myView.getGlobalVisibleRect(rect)
                if (!rect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                    myView.clearFocus()
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    @SuppressLint("SetTextI18n")
    private fun loadDataFromDB() {
        historyList.clear()
        // timestamp DESC sorting!
        historyList.addAll(repository.getLastTotalDailyDoses(10, true).blockingGet())

        //only use newest 10
        historyList = historyList.subList(0, min(10, historyList.size))

        // dummies reset
        dummies.clear()

        //fill single gaps
        val df: DateFormat = SimpleDateFormat("dd.MM.", Locale.getDefault())
        for (i in 0 until historyList.size - 1) {
            val elem1 = historyList[i]
            val elem2 = historyList[i + 1]
            if (df.format(Date(elem1.timestamp)) != df.format(Date(elem2.timestamp + 25 * 60 * 60 * 1000))) {
                val dummy = TotalDailyDose(
                    timestamp = elem1.timestamp - T.hours(24).msecs(),
                    basalAmount = elem1.basalAmount / 2.0,
                    bolusAmount = elem1.bolusAmount / 2.0
                )
                dummies.add(dummy)
                elem1.basalAmount /= 2.0
                elem1.bolusAmount /= 2.0
            }
        }
        historyList.addAll(dummies)
        historyList.sortWith { lhs: TotalDailyDose, rhs: TotalDailyDose -> (rhs.timestamp - lhs.timestamp).toInt() }
        runOnUiThread {
            cleanTable(binding.mainTable)
            cleanTable(binding.cumulativeTable)
            cleanTable(binding.expweightTable)
            val df1: DateFormat = SimpleDateFormat("dd.MM.", Locale.getDefault())
            if (TextUtils.isEmpty(tbb)) {
                binding.totalBaseBasal.error = "Please Enter Total Base Basal"
                return@runOnUiThread
            } else {
                magicNumber = SafeParse.stringToDouble(tbb)
            }
            magicNumber *= 2.0
            binding.totalBaseBasal2.text = decimalFormat.format(magicNumber)
            var i = 0
            var sum = 0.0
            var weighted03 = 0.0
            var weighted05 = 0.0
            var weighted07 = 0.0

            //TDD table
            for (record in historyList) {
                val tdd = record.total

                // Create the table row
                binding.mainTable.addView(
                    TableRow(this@TDDStatsActivity).also { tr ->
                        if (i % 2 != 0) tr.setBackgroundColor(Color.DKGRAY)
                        if (dummies.contains(record))
                            tr.setBackgroundColor(Color.argb(125, 255, 0, 0))

                        tr.id = 100 + i
                        tr.layoutParams = TableLayout.LayoutParams(
                            TableLayout.LayoutParams.MATCH_PARENT,
                            TableLayout.LayoutParams.WRAP_CONTENT
                        )

                        // Here create the TextView dynamically
                        tr.addView(TextView(this@TDDStatsActivity).also { labelDATE ->
                            labelDATE.id = 200 + i
                            labelDATE.text = df1.format(Date(record.timestamp))
                            labelDATE.setTextColor(Color.WHITE)
                        })
                        tr.addView(TextView(this@TDDStatsActivity).also { labelBASAL ->
                            labelBASAL.id = 300 + i
                            labelBASAL.text = rh.gs(R.string.formatinsulinunits, record.basalAmount)
                            labelBASAL.setTextColor(Color.WHITE)
                        })
                        tr.addView(TextView(this@TDDStatsActivity).also { labelBOLUS ->
                            labelBOLUS.id = 400 + i
                            labelBOLUS.text = rh.gs(R.string.formatinsulinunits, record.bolusAmount)
                            labelBOLUS.setTextColor(Color.WHITE)
                        })
                        tr.addView(TextView(this@TDDStatsActivity).also { labelTDD ->
                            labelTDD.id = 500 + i
                            labelTDD.text = rh.gs(R.string.formatinsulinunits, tdd)
                            labelTDD.setTextColor(Color.WHITE)
                        })
                        tr.addView(TextView(this@TDDStatsActivity).also { labelRATIO ->
                            labelRATIO.id = 600 + i
                            labelRATIO.text = (100 * tdd / magicNumber).roundToInt().toString() + "%"
                            labelRATIO.setTextColor(Color.WHITE)
                        })
                    }, TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT)
                )
                i++
            }
            i = 0

            //cumulative TDDs
            for (record in historyList) {
                if (historyList.isNotEmpty() && df1.format(Date(record.timestamp)) == df1.format(Date()))
                //Today should not be included
                    continue
                i++
                sum += record.total

                // Create the cumulative table row
                binding.cumulativeTable.addView(
                    TableRow(this@TDDStatsActivity).also { ctr ->
                        if (i % 2 == 0) ctr.setBackgroundColor(Color.DKGRAY)
                        ctr.id = 700 + i
                        ctr.layoutParams = TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT)

                        // Here create the TextView dynamically
                        ctr.addView(TextView(this@TDDStatsActivity).also { labelDAYS ->
                            labelDAYS.id = 800 + i
                            labelDAYS.text = i.toString()
                            labelDAYS.setTextColor(Color.WHITE)
                        })

                        ctr.addView(TextView(this@TDDStatsActivity).also { labelCUMTDD ->
                            labelCUMTDD.id = 900 + i
                            labelCUMTDD.text = rh.gs(R.string.formatinsulinunits, sum / i)
                            labelCUMTDD.setTextColor(Color.WHITE)
                        })

                        ctr.addView(TextView(this@TDDStatsActivity).also { labelCUMRATIO ->
                            labelCUMRATIO.id = 1000 + i
                            labelCUMRATIO.text = (100 * sum / i / magicNumber).roundToInt().toString() + "%"
                            labelCUMRATIO.setTextColor(Color.WHITE)
                        })
                    }, TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT)
                )
            }
            if (isOldData(historyList) && activePlugin.activePump.pumpDescription.needsManualTDDLoad) {
                binding.message.visibility = View.VISIBLE
                binding.message.text = rh.gs(R.string.olddata_Message)
            } else binding.mainTable.setBackgroundColor(Color.TRANSPARENT)
            if (historyList.isNotEmpty() && df1.format(Date(historyList[0].timestamp)) == df1.format(Date())) {
                //Today should not be included
                historyList.removeAt(0)
            }
            historyList.reverse()
            i = 0
            for (record in historyList) {
                val tdd = record.total
                if (i == 0) {
                    weighted03 = tdd
                    weighted05 = tdd
                    weighted07 = tdd
                } else {
                    weighted07 = weighted07 * 0.3 + tdd * 0.7
                    weighted05 = weighted05 * 0.5 + tdd * 0.5
                    weighted03 = weighted03 * 0.7 + tdd * 0.3
                }
                i++
            }

            // Create the exponential table row
            binding.expweightTable.addView(
                TableRow(this@TDDStatsActivity).also { etr ->
                    if (i % 2 != 0) etr.setBackgroundColor(Color.DKGRAY)
                    etr.id = 1100 + i
                    etr.layoutParams = TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT)

                    // Here create the TextView dynamically
                    etr.addView(TextView(this@TDDStatsActivity).also { labelWEIGHT ->
                        labelWEIGHT.id = 1200 + i
                        labelWEIGHT.text = "0.3\n0.5\n0.7"
                        labelWEIGHT.setTextColor(Color.WHITE)
                    })
                    etr.addView(TextView(this@TDDStatsActivity).also { labelEXPTDD ->
                        labelEXPTDD.id = 1300 + i
                        labelEXPTDD.text = """
                ${rh.gs(R.string.formatinsulinunits, weighted03)}
                ${rh.gs(R.string.formatinsulinunits, weighted05)}
                ${rh.gs(R.string.formatinsulinunits, weighted07)}
                """.trimIndent()
                        labelEXPTDD.setTextColor(Color.WHITE)
                    })
                    etr.addView(TextView(this@TDDStatsActivity).also { labelEXPRATIO ->
                        labelEXPRATIO.id = 1400 + i
                        labelEXPRATIO.text = """
                ${(100 * weighted03 / magicNumber).roundToInt()}%
                ${(100 * weighted05 / magicNumber).roundToInt()}%
                ${(100 * weighted07 / magicNumber).roundToInt()}%
                """.trimIndent()
                        labelEXPRATIO.setTextColor(Color.WHITE)
                    })
                }, TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT)
            )
        }
    }

    private fun cleanTable(table: TableLayout) {
        val childCount = table.childCount
        // Remove all rows except the first one
        if (childCount > 1) table.removeViews(1, childCount - 1)
    }

    private fun isOldData(historyList: List<TotalDailyDose>): Boolean {
        val type = activePlugin.activePump.pumpDescription.pumpType
        val startsYesterday =
            type == PumpType.DANA_R || type == PumpType.DANA_RS || type == PumpType.DANA_RV2 || type == PumpType.DANA_R_KOREAN || type == PumpType.ACCU_CHEK_INSIGHT_VIRTUAL || type == PumpType.DIACONN_G8
        val df: DateFormat = SimpleDateFormat("dd.MM.", Locale.getDefault())
        return historyList.size < 3 || df.format(Date(historyList[0].timestamp)) != df.format(Date(System.currentTimeMillis() - if (startsYesterday) 1000 * 60 * 60 * 24 else 0))
    }
}