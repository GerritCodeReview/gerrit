// Copyright (C) 2013 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.client.ui;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.RepeatingCommand;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.dom.client.MouseMoveEvent;
import com.google.gwt.event.dom.client.MouseMoveHandler;
import com.google.gwt.event.logical.shared.HasValueChangeHandlers;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.EventBus;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.event.shared.SimpleEventBus;
import com.google.gwt.user.client.History;
import com.google.gwtexpui.globalkey.client.DocWidget;

/** Checks for user keyboard and mouse activity. */
public class UserActivityMonitor {
  private static final long TIMEOUT = 10 * 60 * 1000;
  private static final MonitorImpl impl;

  /**
   * @return true if there has been keyboard and/or mouse activity in recent enough history to
   *     believe a user is still controlling this session.
   */
  public static boolean isActive() {
    return impl.active || impl.recent;
  }

  public static HandlerRegistration addValueChangeHandler(ValueChangeHandler<Boolean> handler) {
    return impl.addValueChangeHandler(handler);
  }

  static {
    impl = new MonitorImpl();
    DocWidget.get().addKeyPressHandler(impl);
    DocWidget.get().addMouseMoveHandler(impl);
    History.addValueChangeHandler(impl);
    Scheduler.get().scheduleFixedDelay(impl, 60 * 1000);
  }

  private UserActivityMonitor() {}

  private static class MonitorImpl
      implements RepeatingCommand,
          KeyPressHandler,
          MouseMoveHandler,
          ValueChangeHandler<String>,
          HasValueChangeHandlers<Boolean> {
    private final EventBus bus = new SimpleEventBus();
    private boolean recent = true;
    private boolean active = true;
    private long last = System.currentTimeMillis();

    @Override
    public void onKeyPress(KeyPressEvent event) {
      recent = true;
    }

    @Override
    public void onMouseMove(MouseMoveEvent event) {
      recent = true;
    }

    @Override
    public void onValueChange(ValueChangeEvent<String> event) {
      recent = true;
    }

    @Override
    public boolean execute() {
      long now = System.currentTimeMillis();
      if (recent) {
        if (!active) {
          ValueChangeEvent.fire(this, active);
        }
        recent = false;
        active = true;
        last = now;
      } else if (active && (now - last) > TIMEOUT) {
        active = false;
        ValueChangeEvent.fire(this, false);
      }
      return true;
    }

    @Override
    public HandlerRegistration addValueChangeHandler(ValueChangeHandler<Boolean> handler) {
      return bus.addHandler(ValueChangeEvent.getType(), handler);
    }

    @Override
    public void fireEvent(GwtEvent<?> event) {
      bus.fireEvent(event);
    }
  }
}
