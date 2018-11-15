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

import com.google.gwt.event.dom.client.DomEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.Widget;

public class GlobalKey {
  public static final KeyPressHandler STOP_PROPAGATION = DomEvent::stopPropagation;

  private static State global;
  static State active;
  private static CloseHandler<PopupPanel> restoreGlobal;
  private static Timer restoreTimer;

  static {
    KeyResources.I.css().ensureInjected();
  }

  private static void initEvents() {
    if (active == null) {
      DocWidget.get()
          .addKeyPressHandler(
              new KeyPressHandler() {
                @Override
                public void onKeyPress(KeyPressEvent event) {
                  final KeyCommandSet s = active.live;
                  if (s != active.all) {
                    active.live = active.all;
                    restoreTimer.cancel();
                  }
                  s.onKeyPress(event);
                }
              });

      restoreTimer =
          new Timer() {
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
      restoreGlobal =
          new CloseHandler<PopupPanel>() {
            @Override
            public void onClose(CloseEvent<PopupPanel> event) {
              active = global;
            }
          };
    }
  }

  static void temporaryWithTimeout(KeyCommandSet s) {
    active.live = s;
    restoreTimer.schedule(250);
  }

  public static void dialog(PopupPanel panel) {
    initEvents();
    initDialog();
    assert panel.isShowing();
    assert active == global;
    active = new State(panel);
    active.add(new HidePopupPanelCommand(0, KeyCodes.KEY_ESCAPE, panel));
    panel.addCloseHandler(restoreGlobal);
    panel.addDomHandler(
        new KeyDownHandler() {
          @Override
          public void onKeyDown(KeyDownEvent event) {
            if (event.getNativeKeyCode() == KeyCodes.KEY_ESCAPE) {
              panel.hide();
            }
          }
        },
        KeyDownEvent.getType());
  }

  public static HandlerRegistration addApplication(Widget widget, KeyCommand appKey) {
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

  public static HandlerRegistration add(Widget widget, KeyCommandSet cmdSet) {
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

  public static void filter(KeyCommandFilter filter) {
    active.filter(filter);
    if (active != global) {
      global.filter(filter);
    }
  }

  private GlobalKey() {}

  static class State {
    final Widget root;
    final KeyCommandSet app;
    final KeyCommandSet all;
    KeyCommandSet live;

    State(Widget r) {
      root = r;

      app = new KeyCommandSet(KeyConstants.I.applicationSection());
      app.add(ShowHelpCommand.INSTANCE);

      all = new KeyCommandSet();
      all.add(app);

      live = all;
    }

    void add(KeyCommand k) {
      app.add(k);
      all.add(k);
    }

    void remove(KeyCommand k) {
      app.remove(k);
      all.remove(k);
    }

    void add(KeyCommandSet s) {
      all.add(s);
    }

    void remove(KeyCommandSet s) {
      all.remove(s);
    }

    void filter(KeyCommandFilter f) {
      all.filter(f);
    }
  }
}
