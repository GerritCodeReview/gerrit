package com.google.gerrit.client.ui;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.RepeatingCommand;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.dom.client.MouseOverEvent;
import com.google.gwt.event.dom.client.MouseOverHandler;
import com.google.gwtexpui.globalkey.client.DocWidget;

public class UserActivityMonitor {
  private static final MonitorImpl impl;
  private static final long TIMEOUT = 2 * 3600 * 1000;

  public static boolean isActive() {
    return impl.recent || impl.active;
  }

  static {
    impl = new MonitorImpl();
    DocWidget.get().addKeyPressHandler(impl);
    DocWidget.get().addMouseOverHandler(impl);
    Scheduler.get().scheduleFixedDelay(impl, 5 * 60 * 1000);
  }

  private UserActivityMonitor() {
  }

  private static class MonitorImpl implements RepeatingCommand,
      KeyPressHandler, MouseOverHandler {
    private boolean recent = true;
    private boolean active = true;
    private long last = System.currentTimeMillis();

    @Override
    public void onKeyPress(KeyPressEvent event) {
      active = true;
    }

    @Override
    public void onMouseOver(MouseOverEvent event) {
      active = true;
    }

    @Override
    public boolean execute() {
      long now = System.currentTimeMillis();
      if (active) {
        recent = true;
        active = false;
        last = now;
      } else if ((now - last) > TIMEOUT) {
        recent = false;
      }
      return true;
    }
  }
}
