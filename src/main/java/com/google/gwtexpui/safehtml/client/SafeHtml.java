// Copyright 2009 Google Inc.
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

package com.google.gwtexpui.safehtml.client;

import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.ui.HasHTML;

/** Immutable string safely placed as HTML without further escaping. */
public abstract class SafeHtml {
  /** Set the HTML property of a widget. */
  public static <T extends HasHTML> T set(final T e, final SafeHtml str) {
    e.setHTML(str.asString());
    return e;
  }

  /** Set the inner HTML of any element. */
  public static Element set(final Element e, final SafeHtml str) {
    DOM.setInnerHTML(e, str.asString());
    return e;
  }

  /** Parse an HTML block and return the first (typically root) element. */
  public static Element parse(final SafeHtml str) {
    return DOM.getFirstChild(set(DOM.createDiv(), str));
  }

  /** @return a clean HTML string safe for inclusion in any context. */
  public abstract String asString();
}
