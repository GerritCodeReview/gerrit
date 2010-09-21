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

import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.FocusEvent;
import com.google.gwt.event.dom.client.FocusHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.dom.client.MouseUpEvent;
import com.google.gwt.event.dom.client.MouseUpHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.DeferredCommand;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.EventListener;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.FocusWidget;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.TextBoxBase;

import java.util.HashMap;
import java.util.Map;


/** Enables a FocusWidget (e.g. a Button) if an edit is detected from any
 *  registered input widget.
 */
public class OnEditEnabler implements KeyPressHandler, MouseUpHandler,
   ChangeHandler, ValueChangeHandler, EventListener {

  /* Dirt simple info holder class to put into a Map */
  private class TBBInfo {
    TextBoxBase textBoxBase;
    EventListener listener;

    public TBBInfo(TextBoxBase tb, EventListener l) {
      textBoxBase = tb;
      listener = l;
    }
  }

  private final FocusWidget widget;
  private Map<TextBoxBase, String> strings = new HashMap<TextBoxBase, String>();
  private Map<Element, TBBInfo> tbis = new HashMap<Element, TBBInfo>();


  // The first parameter to the contructors must be the FocusWidget to enable,
  // subsequent parameters are widgets to listenTo.

  public OnEditEnabler(final FocusWidget w, final TextBoxBase tb) {
    this(w);
    listenTo(tb);
  }

  public OnEditEnabler(final FocusWidget w, final ListBox lb) {
    this(w);
    listenTo(lb);
  }

  public OnEditEnabler(final FocusWidget w, final CheckBox cb) {
    this(w);
    listenTo(cb);
  }

  public OnEditEnabler(final FocusWidget w) {
    widget = w;
  }


  // Register input widgets to be listened to

  public void listenTo(final TextBoxBase tb) {
    strings.put(tb, tb.getText());
    tb.addKeyPressHandler(this);

    // Is there another way to capture middle button X11 pastes in browsers
    // which do not yet support ONPASTE events (Firefox)?
    tb.addMouseUpHandler(this);

    // Resetting the "original text" on focus ensures that we are
    // up to date with non-user updates of the text (calls to
    // setText()...) and also up to date with user changes which
    // occured after enabling "widget".
    tb.addFocusHandler(new FocusHandler() {
        @Override
        public void onFocus(FocusEvent event) {
          strings.put(tb, tb.getText());
        }
      });

    // CTRL-V Pastes in Chrome seem only detectable via BrowserEvents.
    // Since passed in widgets cannot be extended, go straight to the
    // Element itself and intercept all its events!
    tb.sinkEvents(Event.ONPASTE);
    Element el = tb.getElement();
    tbis.put(el, new TBBInfo(tb, Event.getEventListener(el)));
    Event.setEventListener(el, this);
  }

  public void listenTo(final ListBox lb) {
    lb.addChangeHandler(this);
  }

  public void listenTo(final CheckBox cb) {
    cb.addValueChangeHandler(this);
  }


  // Handlers

  @Override
  public void onMouseUp(final MouseUpEvent e) {
    on(e);
  }

  @Override
  public void onKeyPress(final KeyPressEvent e) {
    on(e);
  }

  @Override
  public void onChange(final ChangeEvent e) {
    on(e);
  }

  @Override
  public void onValueChange(final ValueChangeEvent e) {
    on(e);
  }

  @Override
  public void onBrowserEvent(Event e) {
    TBBInfo tbi = tbis.get(Element.as(e.getEventTarget()));

    // Made this class the sole listener, better be sure to forward
    // events to original listener.
    tbi.listener.onBrowserEvent(e);

    if (e.getTypeInt() == Event.ONPASTE) {
      onTextBoxBase(tbi.textBoxBase);
    }
  }


  private void on(final GwtEvent e) {
    if (widget.isEnabled() ||
        ! (e.getSource() instanceof FocusWidget) ||
        ! ((FocusWidget) e.getSource()).isEnabled() ) {
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

  private void onTextBoxBase(final TextBoxBase tb) {
    // The text appears to not get updated until the handlers complete.
    DeferredCommand.add(new Command() {
      public void execute() {
        String orig = strings.get(tb);
        orig = orig == null ? "" : orig;
        if (! orig.equals(tb.getText())) {
          widget.setEnabled(true);
        }
      }
    });
  }
}
