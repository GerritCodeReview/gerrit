//Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.client.diff;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.Label;

import net.codemirror.lib.CodeMirror;
import net.codemirror.lib.LineCharacter;

import java.util.ArrayList;
import java.util.List;

/** The Widget that handles the scrollbar gutters */
class SidePanel extends Composite {
  interface Binder extends UiBinder<HTMLPanel, SidePanel> {}
  private static final Binder uiBinder = GWT.create(Binder.class);

  interface SidePanelStyle extends CssResource {
    String gutter();
    String halfGutter();
    String comment();
    String draft();
    String insert();
    String delete();
  }

  enum GutterType {
    COMMENT, DRAFT, INSERT, DELETE, EDIT;
  }

  @UiField
  SidePanelStyle style;

  private List<GutterWrapper> gutters;
  private CodeMirror cmB;

  SidePanel() {
    initWidget(uiBinder.createAndBindUi(this));
    this.gutters = new ArrayList<GutterWrapper>();
  }

  GutterWrapper addGutter(CodeMirror cm, int line, GutterType type) {
    Label gutter = new Label();
    GutterWrapper info = new GutterWrapper(this, gutter, cm, line, type);
    adjustGutter(info);
    gutter.addStyleName(style.gutter());
    switch (type) {
      case COMMENT:
        gutter.addStyleName(style.comment());
        break;
      case DRAFT:
        gutter.addStyleName(style.draft());
        gutter.setText("*");
        break;
      case INSERT:
        gutter.addStyleName(style.insert());
        break;
      case DELETE:
        gutter.addStyleName(style.delete());
        break;
      case EDIT:
        gutter.addStyleName(style.insert());
        Label labelLeft = new Label();
        labelLeft.addStyleName(style.halfGutter());
        gutter.getElement().appendChild(labelLeft.getElement());
    }
    ((HTMLPanel) getWidget()).add(gutter);
    gutters.add(info);
    return info;
  }

  void adjustGutters(CodeMirror cmB) {
    this.cmB = cmB;
    Scheduler.get().scheduleDeferred(new ScheduledCommand() {
      @Override
      public void execute() {
        for (GutterWrapper info : gutters) {
          adjustGutter(info);
        }
      }
    });
  }

  private void adjustGutter(GutterWrapper wrapper) {
    if (cmB == null) {
      return;
    }
    final CodeMirror cm = wrapper.cm;
    final int line = wrapper.line;
    Label gutter = wrapper.gutter;
    final double height = cm.heightAtLine(line, "local");
    final double scrollbarHeight = cmB.getScrollbarV().getClientHeight();
    double top = height / (double) cmB.getSizer().getClientHeight() *
        scrollbarHeight +
        cmB.getScrollbarV().getAbsoluteTop();
    if (top == 0) {
      top = -10;
    }
    gutter.getElement().getStyle().setTop(top, Unit.PX);
    wrapper.replaceClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        cm.setCursor(LineCharacter.create(line));
        cm.scrollToY(height - 0.5 * scrollbarHeight);
        cm.focus();
      }
    });
  }

  void removeGutter(GutterWrapper wrapper) {
    gutters.remove(wrapper);
  }

  static class GutterWrapper {
    private SidePanel host;
    private Label gutter;
    private CodeMirror cm;
    private int line;
    private HandlerRegistration regClick;

    GutterWrapper(SidePanel host, Label anchor, CodeMirror cm, int line,
        GutterType type) {
      this.host = host;
      this.gutter = anchor;
      this.cm = cm;
      this.line = line;
    }

    private void replaceClickHandler(ClickHandler newHandler) {
      if (regClick != null) {
        regClick.removeHandler();
      }
      regClick = gutter.addClickHandler(newHandler);
    }

    void remove() {
      gutter.removeFromParent();
      host.removeGutter(this);
    }
  }
}
