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

package com.google.gerrit.client.admin;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.FocusPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;

public class ProjectInfoDescWarning extends PopupPanel {

  private final FocusPanel focus;

  public ProjectInfoDescWarning() {
    super(true/* autohide */, true/* modal */);
    setStyleName(AdminResources.I.css(). descWarning());

    final Anchor closer = new Anchor(Util.C.noDescClose());
    closer.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        hide();
      }
    });
    final Grid header = new Grid(1, 3);
    header.setStyleName(AdminResources.I.css().descWarningHeader());
    header.setText(0, 1, Util.C.noDescHeader());
    header.setWidget(0, 2, closer);

    final CellFormatter fmt = header.getCellFormatter();
    fmt.addStyleName(0, 1, AdminResources.I.css().descWarningGlue());
    fmt.setHorizontalAlignment(0, 2, HasHorizontalAlignment.ALIGN_RIGHT);
    fmt.setHorizontalAlignment(0, 1, HasHorizontalAlignment.ALIGN_CENTER);

    final FlowPanel body = new FlowPanel();
    body.add(header);
    body.getElement().appendChild(DOM.createElement("hr"));

    final Label noticeLabel = new Label (Util.C.noDescNotice());
    noticeLabel.addStyleName(AdminResources.I.css().descWarningNotice());
    final Grid content = new Grid(2, 2);
    content.setStyleName(AdminResources.I.css().descWarningContent());
    final CellFormatter contentFmt = content.getCellFormatter();
    contentFmt.addStyleName(0, 0, AdminResources.I.css().descWarningContentGlue());
    content.setText(0, 0, Util.C.noDescContent());
    body.add(content);
    final Grid notice = new Grid(1, 2);
    notice.setWidget(0, 0,noticeLabel);
    final CellFormatter noticeFmt = notice.getCellFormatter();
    noticeFmt.setHorizontalAlignment(0, 0, HasHorizontalAlignment.ALIGN_CENTER);
    body.add(notice);

    focus = new FocusPanel(body);
    focus.getElement().getStyle().setProperty("outline", "0px");
    focus.getElement().setAttribute("hideFocus", "true");
    add(focus);
  }
}
