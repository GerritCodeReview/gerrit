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

package com.google.gwtexpui.user.client;

import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.user.client.ui.Widget;

public class DialogVisibleEvent extends GwtEvent<DialogVisibleHandler> {
  private static Type<DialogVisibleHandler> TYPE;

  public static Type<DialogVisibleHandler> getType() {
    if (TYPE == null) {
      TYPE = new Type<DialogVisibleHandler>();
    }
    return TYPE;
  }

  private final Widget parent;
  private final boolean visible;

  DialogVisibleEvent(Widget w, boolean visible) {
    this.parent = w;
    this.visible = visible;
  }

  public boolean contains(Widget c) {
    for (; c != null; c = c.getParent()) {
      if (c == parent) {
        return true;
      }
    }
    return false;
  }

  public boolean isVisible() {
    return visible;
  }

  @Override
  public Type<DialogVisibleHandler> getAssociatedType() {
    return getType();
  }

  @Override
  protected void dispatch(DialogVisibleHandler handler) {
    handler.onDialogVisible(this);
  }
}
