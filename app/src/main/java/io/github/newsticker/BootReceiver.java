package io.github.newsticker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Bring the overlay back after a TV reboot, only when the owner left it enabled. */
public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (!"android.intent.action.BOOT_COMPLETED".equals(i.getAction())) return;
        if (!Prefs.enabled(c)) return;
        Intent s = new Intent(c, TickerService.class);
        c.startForegroundService(s);
    }
}
