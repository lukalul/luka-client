package dev.cbui.hud.module;

import dev.cbui.hud.Anchor;
import dev.cbui.hud.Setting;

import java.util.Calendar;

/** Real-world local time in 12 or 24 hour format, refreshed once per second. */
public final class ClockModule extends LabelModule {
    private final Setting.Choice format = grouped("Display", add(Setting.tip(new Setting.Choice("Format", "24h", "24h", "12h"), "24-hour (13:37) or 12-hour (1:37 PM)")));
    private final Setting.Toggle seconds = grouped("Display", add(new Setting.Toggle("Show seconds", false, "Add seconds to the time")));
    private final Calendar calendar = Calendar.getInstance();
    private final StringBuilder builder = new StringBuilder(12);
    private int ticks = 20;

    public ClockModule() { super("Clock", Anchor.TOP_LEFT, 5, 49, "13:37"); }

    @Override public void tick() {
        if (++ticks < 20) return;
        ticks = 0;
        calendar.setTimeInMillis(System.currentTimeMillis());
        boolean twelve = "12h".equals(format.get());
        int hour = calendar.get(twelve ? Calendar.HOUR : Calendar.HOUR_OF_DAY);
        if (twelve && hour == 0) hour = 12;
        builder.setLength(0);
        if (!twelve && hour < 10) builder.append('0');
        builder.append(hour).append(':');
        two(calendar.get(Calendar.MINUTE));
        if (seconds.get()) { builder.append(':'); two(calendar.get(Calendar.SECOND)); }
        if (twelve) builder.append(calendar.get(Calendar.AM_PM) == Calendar.AM ? " AM" : " PM");
        label(builder.toString());
    }

    private void two(int v) { if (v < 10) builder.append('0'); builder.append(v); }
}
