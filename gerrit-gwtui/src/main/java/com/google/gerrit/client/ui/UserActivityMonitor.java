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
import com.google.gwt.event.dom.client.MouseOverEvent;
import com.google.gwt.event.dom.client.MouseOverHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.History;
import com.google.gwtexpui.globalkey.client.DocWidget;

/** Checks for user keyboard and mouse activity. */
public class UserActivityMonitor {
  private static final MonitorImpl impl;
  private static final long TIMEOUT = 2 * 3600 * 1000;

  /**
   * @return true if there has been keyboard and/or mouse activity in recent
   *         enough history to believe a user is still controlling this session.
   */
  public static boolean isActive() {
    return impl.recent || impl.active;
  }

  static {
    impl = new MonitorImpl();
    DocWidget.get().addKeyPressHandler(impl);
    DocWidget.get().addMouseOverHandler(impl);
    History.addValueChangeHandler(impl);
    Scheduler.get().scheduleFixedDelay(impl, 5 * 60 * 1000);
  }

  private UserActivityMonitor() {
  }

  private static class MonitorImpl implements RepeatingCommand,
      KeyPressHandler, MouseOverHandler, ValueChangeHandler<String> {
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
    public void onValueChange(ValueChangeEvent<String> event) {
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
