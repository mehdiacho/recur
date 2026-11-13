package app.greenforrest.recur

import android.app.Application
import app.greenforrest.recur.data.Repository
import app.greenforrest.recur.notify.Reminders

class RecurApp : Application() {
  lateinit var repo: Repository
    private set

  override fun onCreate() {
    super.onCreate()
    repo = Repository(this)
    Reminders.createChannel(this)
    Reminders.schedule(this)
  }
}
