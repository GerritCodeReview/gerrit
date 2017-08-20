// Copyright (C) 2015 The Android Open Source Project
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

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.user.client.ui.UIObject;

/** Displays custom tooltip message below an element. */
public class Tooltip {
  interface Resources extends ClientBundle {
    Resources I = GWT.create(Resources.class);

    @Source("tooltip.css")
    Css css();
  }

  interface Css extends CssResource {
    String tooltip();
  }

  static {
    Resources.I.css().ensureInjected();
  }

  /**
   * Add required supporting style to enable custom tooltip rendering.
   *
   * @param o widget whose element should display a tooltip on hover.
   */
  public static void addStyle(UIObject o) {
    addStyle(o.getElement());
  }

  /**
   * Add required supporting style to enable custom tooltip rendering.
   *
   * @param e element that should display a tooltip on hover.
   */
  public static void addStyle(Element e) {
    e.addClassName(Resources.I.css().tooltip());
  }

  /**
   * Set the text displayed on hover.
   *
   * @param o widget whose hover text is being set.
   * @param text message to display on hover.
   */
  public static void setLabel(UIObject o, String text) {
    setLabel(o.getElement(), text);
  }

  /**
   * Set the text displayed on hover.
   *
   * @param e element whose hover text is being set.
   * @param text message to display on hover.
   */
  public static void setLabel(Element e, String text) {
    e.setAttribute("aria-label", text != null ? text : "");
  }

  private Tooltip() {}
}
