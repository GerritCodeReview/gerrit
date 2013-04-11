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

package com.google.gwtexpui.user.client;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.user.client.ui.UIObject;

import java.util.ArrayList;

class PluginSafeDialogBoxImplAutoHide extends PluginSafeDialogBoxImpl {
  private boolean hidden;
  private ArrayList<HiddenElement> hiddenElements =
      new ArrayList<HiddenElement>();

  @Override
  void visible(final boolean dialogVisible) {
    if (dialogVisible) {
      hideAll();
    } else {
      showAll();
    }
  }

  private void hideAll() {
    if (!hidden) {
      hideSet(Document.get().getElementsByTagName("object"));
      hideSet(Document.get().getElementsByTagName("embed"));
      hideSet(Document.get().getElementsByTagName("applet"));
      hidden = true;
    }
  }

  private void hideSet(final NodeList<Element> all) {
    for (int i = 0; i < all.getLength(); i++) {
      final Element e = all.getItem(i);
      if (UIObject.isVisible(e)) {
        hiddenElements.add(new HiddenElement(e));
      }
    }
  }

  private void showAll() {
    if (hidden) {
      for (final HiddenElement e : hiddenElements) {
        e.restore();
      }
      hiddenElements.clear();
      hidden = false;
    }
  }

  private static class HiddenElement {
    private final Element element;
    private final String visibility;

    HiddenElement(final Element element) {
      this.element = element;
      this.visibility = getVisibility(element);
      setVisibility(element, "hidden");
    }

    void restore() {
      setVisibility(element, visibility);
    }

    private static native String getVisibility(Element elem)
    /*-{ return elem.style.visibility; }-*/;

    private static native void setVisibility(Element elem, String disp)
    /*-{ elem.style.visibility = disp; }-*/;
  }
}
