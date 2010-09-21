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

import com.google.gwt.event.shared.GwtEvent;

/**
 * Event that is fired when the user "actions" a widget by either clicking on it
 * or pressing the ENTER key when the widget has the key focus.
 */
public class ActionEvent extends GwtEvent<ActionHandler> {

  /**
   * Event type for action events. Represents the meta-data associated with this
   * event.
   */
  private static final Type<ActionHandler> TYPE = new Type<ActionHandler>();

  /**
   * Gets the event type associated with action events.
   *
   * @return the handler type
   */
  public static Type<ActionHandler> getType() {
    return TYPE;
  }

  public ActionEvent() {
  }

  @Override
  public Type<ActionHandler> getAssociatedType() {
    return TYPE;
  }

  @Override
  protected void dispatch(ActionHandler handler) {
    handler.onAction(this);
  }
}
