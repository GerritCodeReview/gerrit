// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gwtexpui.globalkey.client;

import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.Widget;


public class GlobalKey {
  public static final KeyPressHandler STOP_PROPAGATION = new KeyPressHandler() {
    @Override
    public void onKeyPress(final KeyPressEvent event) {
      event.stopPropagation();
    }
  };

  private static State global;
  static State active;
  private static CloseHandler<PopupPanel> restoreGlobal;
  private static Timer restoreTimer;

  static {
    KeyResources.I.css().ensureInjected();
  }

  private static void initEvents() {
    if (active == null) {
      DocWidget.get().addKeyPressHandler(new KeyPressHandler() {
        @Override
        public void onKeyPress(final KeyPressEvent event) {
          final KeyCommandSet s = active.live;
          if (s != active.all) {
            active.live = active.all;
            restoreTimer.cancel();
          }
          s.onKeyPress(event);
        }
      });

      restoreTimer = new Timer() {
        @Override
        public void run() {
          active.live = active.all;
        }
      };

      global = new State(null);
      active = global;
    }
  }

  private static void initDialog() {
    if (restoreGlobal == null) {
      restoreGlobal = new CloseHandler<PopupPanel>() {
        @Override
        public void onClose(final CloseEvent<PopupPanel> event) {
          active = global;
        }
      };
    }
  }

  static void temporaryWithTimeout(final KeyCommandSet s) {
    active.live = s;
    restoreTimer.schedule(250);
  }

  public static void dialog(final PopupPanel panel) {
    initEvents();
    initDialog();
    assert panel.isShowing();
    assert active == global;
    active = new State(panel);
    active.add(new HidePopupPanelCommand(0, KeyCodes.KEY_ESCAPE, panel));
    panel.addCloseHandler(restoreGlobal);
  }

  public static HandlerRegistration addApplication(final Widget widget,
      final KeyCommand appKey) {
    initEvents();
    final State state = stateFor(widget);
    state.add(appKey);
    return new HandlerRegistration() {
      @Override
      public void removeHandler() {
        state.remove(appKey);
      }
    };
  }

  public static HandlerRegistration add(final Widget widget,
      final KeyCommandSet cmdSet) {
    initEvents();
    final State state = stateFor(widget);
    state.add(cmdSet);
    return new HandlerRegistration() {
      @Override
      public void removeHandler() {
        state.remove(cmdSet);
      }
    };
  }

  private static State stateFor(Widget w) {
    while (w != null) {
      if (w == active.root) {
        return active;
      }
      w = w.getParent();
    }
    return global;
  }

  public static void filter(final KeyCommandFilter filter) {
    active.filter(filter);
    if (active != global) {
      global.filter(filter);
    }
  }

  private GlobalKey() {
  }

  static class State {
    final Widget root;
    final KeyCommandSet app;
    final KeyCommandSet all;
    KeyCommandSet live;

    State(final Widget r) {
      root = r;

      app = new KeyCommandSet(KeyConstants.I.applicationSection());
      app.add(ShowHelpCommand.INSTANCE);

      all = new KeyCommandSet();
      all.add(app);

      live = all;
    }

    void add(final KeyCommand k) {
      app.add(k);
      all.add(k);
    }

    void remove(final KeyCommand k) {
      app.remove(k);
      all.remove(k);
    }

    void add(final KeyCommandSet s) {
      all.add(s);
    }

    void remove(final KeyCommandSet s) {
      all.remove(s);
    }

    void filter(final KeyCommandFilter f) {
      all.filter(f);
    }
  }
}
