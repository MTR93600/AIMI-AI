package info.nightscout.androidaps.plugins.pump.eopatch.bindingadapters

import android.view.View
import java.util.concurrent.atomic.AtomicBoolean

class OnSafeClickListener(
        private val clickListener: View.OnClickListener,
        private val intervalMs: Long = MIN_CLICK_INTERVAL
) : View.OnClickListener {
    private var canClick = AtomicBoolean(true)

    override fun onClick(v: View?) {
        if (canClick.getAndSet(false)) {
            v?.run {
                postDelayed({
                    canClick.set(true)
                }, intervalMs)
                clickListener.onClick(v)
            }
        }
    }
    companion object {
        // 중복 클릭 방지 시간 설정
        private const val MIN_CLICK_INTERVAL: Long = 1000
    }
}