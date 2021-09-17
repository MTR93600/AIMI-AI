package info.nightscout.androidaps.utils.rx

import io.reactivex.Scheduler
import io.reactivex.android.plugins.RxAndroidPlugins
import io.reactivex.plugins.RxJavaPlugins
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

class RxSchedulerRule(val scheduler: Scheduler) : TestRule {

    override fun apply(base: Statement, description: Description) =
        object : Statement() {
            override fun evaluate() {
                RxAndroidPlugins.reset()
                RxAndroidPlugins.setInitMainThreadSchedulerHandler { scheduler }
                RxJavaPlugins.reset()
                RxJavaPlugins.setIoSchedulerHandler { scheduler }
                RxJavaPlugins.setNewThreadSchedulerHandler { scheduler }
                RxJavaPlugins.setComputationSchedulerHandler { scheduler }

                try {
                    base.evaluate()
                } finally {
                    RxJavaPlugins.reset()
                    RxAndroidPlugins.reset()
                }

            }
        }
}