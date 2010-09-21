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

package com.google.gerrit.client;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.shared.HandlerManager;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.Button;

/**
 * A standard push-button widget that allows the registration of
 * {@link ActionHandler} handlers for handling {@link ActionEvent} events.
 * Action events are fired when this button is "actioned" by either clicking on
 * it or pressing the ENTER key when this button has the key focus.
 */
public class ActionButton extends Button {

  final private HandlerManager handlerManager = new HandlerManager(this);

  public ActionButton() {
    super();
    registerHandlers();
  }

  public ActionButton(String html) {
    super(html);
    registerHandlers();
  }

  /**
   * Registers a {@link ClickHandler} and a {@link KeyPressHandler} for this
   * button. These handlers ensure that an {@link ActionEvent} is fired if the
   * user clicks on the button or if the user activates the button by pressing
   * the ENTER key.
   */
  private void registerHandlers() {
    addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        handlerManager.fireEvent(new ActionEvent());
      }
    });

    addKeyPressHandler(new KeyPressHandler() {
      @Override
      public void onKeyPress(KeyPressEvent event) {
        if (event.getCharCode() == KeyCodes.KEY_ENTER) {
          handlerManager.fireEvent(new ActionEvent());
        }
      }
    });
  }

  /**
   * Registers an {@link ActionHandler} for this button.
   *
   * @param actionHandler the {@link ActionHandler} to be registered
   * @return the registration for the handler
   */
  public HandlerRegistration addActionHandler(final ActionHandler handler) {
    return handlerManager.addHandler(ActionEvent.getType(),handler);
  }
}
