package com.lifebook.ledger.receiver;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import com.lifebook.ledger.MainActivity;
import com.lifebook.ledger.R;
import com.lifebook.ledger.util.NotifyManager;

public class NotifyReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        int code = intent.getIntExtra("code", 0);
        if (code == 0) return;
        NotifyManager.ensureChannel(context);

        String title;
        String text;
        int notifId;
        if (code == NotifyManager.REQ_DAILY) {
            title = "该记账啦 📝";
            text = "今天的花销记了吗？打开「生活记账本」随手记一笔，钱花去哪里一目了然。";
            notifId = 1;
        } else if (code == NotifyManager.REQ_WEEKLY) {
            title = "你的本周账报 📊";
            text = NotifyManager.weeklyReportText(context);
            notifId = 2;
        } else {
            title = "你的本月账报 📊";
            text = NotifyManager.monthlyReportText(context);
            notifId = 3;
        }

        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            // 未授权通知，跳过展示，但仍继续排下一次
            NotifyManager.scheduleAll(context);
            return;
        }

        try {
            PendingIntent content = PendingIntent.getActivity(context, 0,
                    new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification n = new Notification.Builder(context, NotifyManager.CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notif)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setStyle(new Notification.BigTextStyle().bigText(text))
                    .setAutoCancel(true)
                    .setContentIntent(content)
                    .build();
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(notifId, n);
        } catch (Exception ignored) {
        }
        NotifyManager.scheduleAll(context);
    }
}
