package org.localroute.android

class LocalRouteApplication : android.app.Application() {
    override fun onCreate() {
        super.onCreate()
        org.localroute.android.core.DiagnosticJournal.initialize(this)
    }
}
