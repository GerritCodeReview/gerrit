// Copyright 2008 Google Inc.
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

import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.ui.ComplexPanel;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.DisclosureHandler;
import com.google.gwt.user.client.ui.DisclosurePanel;
import com.google.gwt.user.client.ui.FiresDisclosureEvents;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.Widget;

public class ComplexDisclosurePanel extends Composite implements
    FiresDisclosureEvents {
  private final DisclosurePanel main;
  private final Panel header;

  public ComplexDisclosurePanel(final String text, final boolean isOpen) {
    main = new DisclosurePanel(text, isOpen);
    final Element cell2;
    {
      // Ick. GWT's DisclosurePanel won't let us subclass it, or do any
      // other modification of its header. We're stuck with injecting
      // into the DOM directly.
      //
      final Element table = main.getElement();
      final Element tbody = DOM.getFirstChild(table);
      final Element tr1 = DOM.getChild(tbody, 0);
      final Element tr2 = DOM.getChild(tbody, 1);
      cell2 = DOM.createTD();

      DOM.setElementPropertyInt(DOM.getChild(tr2, 0), "colSpan", 2);
      DOM.appendChild(tr1, cell2);
    }
    header = new ComplexPanel() {
      {
        setElement(cell2);
      }

      @Override
      public void add(Widget w) {
        add(w, getElement());
      }
    };
    initWidget(main);
  }

  public Panel getHeader() {
    return header;
  }

  public void setContent(final Widget w) {
    main.setContent(w);
  }

  public void addEventHandler(final DisclosureHandler handler) {
    main.addEventHandler(handler);
  }

  public void removeEventHandler(final DisclosureHandler handler) {
    main.removeEventHandler(handler);
  }
}
