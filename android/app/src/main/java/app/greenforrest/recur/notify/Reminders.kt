// Background reminders. A periodic worker runs a few times a day, asks the core which
// reminders are due (dueReminders buckets them, so a missed day still fires late rather
// than never) and posts each key once, tracked in Store.notified.
package app.greenforrest.recur.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.greenforrest.recur.R
import app.greenforrest.recur.RecurApp
import app.greenforrest.recur.core.Dates
import app.greenforrest.recur.core.Schedule
import app.greenforrest.recur.ui.MainActivity
import app.greenforrest.recur.ui.money
import java.util.concurrent.TimeUnit

object Reminders {
  const val CHANNEL = "reminders"

  fun createChannel(ctx: Context) {
    val ch = NotificationChannel(CHANNEL, "Renewals and contract ends", NotificationManager.IMPORTANCE_DEFAULT)
    ch.description = "A reminder before something renews, a trial ends or a contract ends."
    ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
  }

  fun schedule(ctx: Context) {
    // Every 6 hours: a reminder lands the same morning even if the phone slept through one run.
    val req = PeriodicWorkRequestBuilder<ReminderWorker>(6, TimeUnit.HOURS).build()
    WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("recur-reminders", ExistingPeriodicWorkPolicy.KEEP, req)
  }

  fun canNotify(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

  /** Posts every due reminder not shown yet. Safe to call from the app or the worker. */
  fun postDue(ctx: Context) {
    val repo = (ctx.applicationContext as RecurApp).repo
    val store = repo.store.value
    val today = Dates.today()
    val due = Schedule.dueReminders(store.subscriptions, today).filter { it.key !in store.notified }
    if (due.isEmpty() || !canNotify(ctx)) return
    val open = PendingIntent.getActivity(
      ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
      PendingIntent.FLAG_IMMUTABLE,
    )
    val nm = NotificationManagerCompat.from(ctx)
    for (r in due) {
      val n = NotificationCompat.Builder(ctx, CHANNEL)
        .setSmallIcon(R.drawable.ic_stat_recur)
        .setContentTitle(title(r))
        .setContentText(body(r, store.currency))
        .setStyle(NotificationCompat.BigTextStyle().bigText(body(r, store.currency)))
        .setContentIntent(open)
        .setAutoCancel(true)
        .build()
      try { nm.notify(r.key.hashCode(), n) } catch (_: SecurityException) { return }
    }
    repo.update { s -> s.copy(notified = Schedule.pruneNotified(s.notified + due.map { it.key }, today)) }
  }

  fun title(r: Schedule.Reminder): String {
    val whenText = when (r.daysUntil) { 0L -> "today"; 1L -> "tomorrow"; else -> "in ${r.daysUntil} days" }
    return when (r.type) {
      "contract-end" -> "${r.name}: contract ends $whenText"
      "trial-end" -> "${r.name}: trial ends $whenText"
      else -> "${r.name} renews $whenText"
    }
  }

  fun body(r: Schedule.Reminder, currency: String): String = when (r.type) {
    "contract-end" -> (if (r.amount > 0) "Renewal cost ${money(r.amount, currency)}. " else "") +
      "Decide whether to renew, upgrade or switch."
    "trial-end" -> "First charge of ${money(r.amount, currency)}. Cancel before then if you don't want it."
    else -> "${money(r.amount, currency)} on ${r.date}."
  }
}

class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
  override suspend fun doWork(): Result {
    Reminders.postDue(applicationContext)
    return Result.success()
  }
}
