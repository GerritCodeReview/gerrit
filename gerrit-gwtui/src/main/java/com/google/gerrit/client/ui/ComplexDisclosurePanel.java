// Copyright (C) 2008 The Android Open Source Project
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
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.event.logical.shared.HasCloseHandlers;
import com.google.gwt.event.logical.shared.HasOpenHandlers;
import com.google.gwt.event.logical.shared.OpenHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.ComplexPanel;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.DisclosurePanel;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.Widget;

public class ComplexDisclosurePanel extends Composite
    implements HasOpenHandlers<DisclosurePanel>, HasCloseHandlers<DisclosurePanel> {
  private final DisclosurePanel main;
  private final Panel header;

  public ComplexDisclosurePanel(String text, boolean isOpen) {
    // Ick. GWT's DisclosurePanel won't let us subclass it, or do any
    // other modification of its header. We're stuck with injecting
    // into the DOM directly.
    //
    main = new DisclosurePanel(text);
    main.setOpen(isOpen);
    final Element headerParent;
    {
      final Element table = main.getElement();
      final Element tbody = DOM.getFirstChild(table);
      final Element tr1 = DOM.getChild(tbody, 0);
      final Element tr2 = DOM.getChild(tbody, 1);

      DOM.getChild(tr1, 0).setPropertyString("width", "20px");
      DOM.getChild(tr2, 0).setPropertyInt("colSpan", 2);
      headerParent = tr1;
    }

    header =
        new ComplexPanel() {
          {
            setElement((Element) (DOM.createTD()));
            getElement().setInnerHTML("&nbsp;");
          }

          @Override
          public void add(Widget w) {
            add(w, (Element) getElement());
          }
        };

    initWidget(
        new ComplexPanel() {
          {
            final DisclosurePanel main = ComplexDisclosurePanel.this.main;
            setElement((Element) (main.getElement()));
            getChildren().add(main);
            adopt(main);

            add(ComplexDisclosurePanel.this.header, headerParent);
          }
        });
  }

  public Panel getHeader() {
    return header;
  }

  public void setContent(Widget w) {
    main.setContent(w);
  }

  public Widget getContent() {
    return main.getContent();
  }

  @Override
  public HandlerRegistration addOpenHandler(OpenHandler<DisclosurePanel> h) {
    return main.addOpenHandler(h);
  }

  @Override
  public HandlerRegistration addCloseHandler(CloseHandler<DisclosurePanel> h) {
    return main.addCloseHandler(h);
  }

  /** @return true if the panel's content is visible. */
  public boolean isOpen() {
    return main.isOpen();
  }

  /**
   * Changes the visible state of this panel's content.
   *
   * @param isOpen {@code true} to open, {@code false} to close
   */
  public void setOpen(boolean isOpen) {
    main.setOpen(isOpen);
  }
}
