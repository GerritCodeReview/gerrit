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

package com.google.gerrit.client.change;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.info.ChangeInfo;
import com.google.gerrit.client.ui.UserActivityMonitor;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.rpc.AsyncCallback;

class UpdateCheckTimer extends Timer implements ValueChangeHandler<Boolean> {
  private static final int MAX_PERIOD = 3 * 60 * 1000;
  private static final int IDLE_PERIOD = 2 * 3600 * 1000;
  private static final int POLL_PERIOD = Gerrit.info().change().updateDelay() * 1000;

  private final ChangeScreen screen;
  private int delay;
  private boolean running;

  UpdateCheckTimer(ChangeScreen screen) {
    this.screen = screen;
    this.delay = POLL_PERIOD;
  }

  void schedule() {
    scheduleRepeating(delay);
  }

  @Override
  public void run() {
    if (!screen.isAttached()) {
      // screen should have cancelled this timer.
      cancel();
      return;
    } else if (running) {
      return;
    }

    running = true;
    screen.loadChangeInfo(
        false,
        new AsyncCallback<ChangeInfo>() {
          @Override
          public void onSuccess(ChangeInfo info) {
            running = false;
            screen.showUpdates(info);

            int d = UserActivityMonitor.isActive() ? POLL_PERIOD : IDLE_PERIOD;
            if (d != delay) {
              delay = d;
              schedule();
            }
          }

          @Override
          public void onFailure(Throwable caught) {
            // On failures increase the delay time and try again,
            // but place an upper bound on the delay.
            running = false;
            delay =
                (int)
                    Math.max(
                        delay * (1.5 + Math.random()),
                        UserActivityMonitor.isActive() ? MAX_PERIOD : IDLE_PERIOD + MAX_PERIOD);
            schedule();
          }
        });
  }

  @Override
  public void onValueChange(ValueChangeEvent<Boolean> event) {
    if (event.getValue()) {
      delay = POLL_PERIOD;
      run();
    } else {
      delay = IDLE_PERIOD;
    }
    schedule();
  }
}
