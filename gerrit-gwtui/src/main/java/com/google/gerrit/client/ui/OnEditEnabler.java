// Copyright (C) 2010 The Android Open Source Project
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
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.FocusEvent;
import com.google.gwt.event.dom.client.FocusHandler;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.dom.client.MouseUpEvent;
import com.google.gwt.event.dom.client.MouseUpHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.FocusWidget;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.TextBoxBase;
import com.google.gwt.user.client.ui.ValueBoxBase;
import java.util.HashMap;
import java.util.Map;

/**
 * Enables a FocusWidget (e.g. a Button) if an edit is detected from any registered input widget.
 */
public class OnEditEnabler
    implements KeyPressHandler,
        KeyDownHandler,
        MouseUpHandler,
        ChangeHandler,
        ValueChangeHandler<Object> {

  private final FocusWidget widget;
  private Map<TextBoxBase, String> strings = new HashMap<>();
  private String originalValue;

  // The first parameter to the contructors must be the FocusWidget to enable,
  // subsequent parameters are widgets to listenTo.

  public OnEditEnabler(FocusWidget w, TextBoxBase tb) {
    this(w);
    originalValue = tb.getValue().trim();
    listenTo(tb);
  }

  public OnEditEnabler(FocusWidget w, ListBox lb) {
    this(w);
    listenTo(lb);
  }

  public OnEditEnabler(FocusWidget w, CheckBox cb) {
    this(w);
    listenTo(cb);
  }

  public OnEditEnabler(FocusWidget w) {
    widget = w;
  }

  public void updateOriginalValue(TextBoxBase tb) {
    originalValue = tb.getValue().trim();
  }

  // Register input widgets to be listened to

  public void listenTo(TextBoxBase tb) {
    strings.put(tb, tb.getText().trim());
    tb.addKeyPressHandler(this);

    // Is there another way to capture middle button X11 pastes in browsers
    // which do not yet support ONPASTE events (Firefox)?
    tb.addMouseUpHandler(this);

    // Resetting the "original text" on focus ensures that we are
    // up to date with non-user updates of the text (calls to
    // setText()...) and also up to date with user changes which
    // occurred after enabling "widget".
    tb.addFocusHandler(
        new FocusHandler() {
          @Override
          public void onFocus(FocusEvent event) {
            strings.put(tb, tb.getText().trim());
          }
        });

    // CTRL-V Pastes in Chrome seem only detectable via BrowserEvents or
    // KeyDownEvents, the latter is better.
    tb.addKeyDownHandler(this);
  }

  public void listenTo(ListBox lb) {
    lb.addChangeHandler(this);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public void listenTo(CheckBox cb) {
    cb.addValueChangeHandler((ValueChangeHandler) this);
  }

  // Handlers

  @Override
  public void onKeyPress(KeyPressEvent e) {
    on(e);
  }

  @Override
  public void onKeyDown(KeyDownEvent e) {
    on(e);
  }

  @Override
  public void onMouseUp(MouseUpEvent e) {
    on(e);
  }

  @Override
  public void onChange(ChangeEvent e) {
    on(e);
  }

  @SuppressWarnings("rawtypes")
  @Override
  public void onValueChange(ValueChangeEvent e) {
    on(e);
  }

  private void on(GwtEvent<?> e) {
    if (widget.isEnabled()
        || !(e.getSource() instanceof FocusWidget)
        || !((FocusWidget) e.getSource()).isEnabled()) {
      if (e.getSource() instanceof ValueBoxBase) {
        final TextBoxBase box = ((TextBoxBase) e.getSource());
        Scheduler.get()
            .scheduleDeferred(
                new ScheduledCommand() {
                  @Override
                  public void execute() {
                    if (box.getValue().trim().equals(originalValue)) {
                      widget.setEnabled(false);
                    }
                  }
                });
      }
      return;
    }

    if (e.getSource() instanceof TextBoxBase) {
      onTextBoxBase((TextBoxBase) e.getSource());
    } else {
      // For many widgets, we can assume that a change is an edit. If
      // a widget does not work that way, it should be special cased
      // above.
      widget.setEnabled(true);
    }
  }

  private void onTextBoxBase(TextBoxBase tb) {
    // The text appears to not get updated until the handlers complete.
    Scheduler.get()
        .scheduleDeferred(
            new ScheduledCommand() {
              @Override
              public void execute() {
                String orig = strings.get(tb);
                if (orig == null) {
                  orig = "";
                }
                if (!orig.equals(tb.getText().trim())) {
                  widget.setEnabled(true);
                }
              }
            });
  }
}
