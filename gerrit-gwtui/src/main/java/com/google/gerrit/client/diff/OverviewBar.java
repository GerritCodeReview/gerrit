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
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.event.dom.client.MouseMoveEvent;
import com.google.gwt.event.dom.client.MouseUpEvent;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

import net.codemirror.lib.CodeMirror;
import net.codemirror.lib.LineCharacter;
import net.codemirror.lib.ScrollInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Displays overview of all edits and comments in this file. */
class OverviewBar extends Composite implements ClickHandler {
  interface Binder extends UiBinder<HTMLPanel, OverviewBar> {}
  private static final Binder uiBinder = GWT.create(Binder.class);

  interface Style extends CssResource {
    String gutter();
    String halfGutter();
    String comment();
    String draft();
    String insert();
    String delete();
    String viewportDrag();
  }

  enum MarkType {
    COMMENT, DRAFT, INSERT, DELETE, EDIT
  }

  @UiField Style style;
  @UiField Label viewport;

  private final List<MarkHandle> diff;
  private final Set<MarkHandle> comments;
  private CodeMirror cmB;

  private boolean dragging;
  private int startY;
  private double ratio;

  OverviewBar() {
    initWidget(uiBinder.createAndBindUi(this));
    diff = new ArrayList<MarkHandle>();
    comments = new HashSet<MarkHandle>();
    addDomHandler(this, ClickEvent.getType());
  }

  void init(CodeMirror cmB) {
    this.cmB = cmB;
  }

  void refresh() {
    update(cmB.getScrollInfo());
  }

  void update(ScrollInfo si) {
    double viewHeight = si.getClientHeight();
    double r = ratio(si);

    com.google.gwt.dom.client.Style style = viewport.getElement().getStyle();
    style.setTop(si.getTop() * r, Unit.PX);
    style.setHeight(Math.max(10, viewHeight * r), Unit.PX);
    getElement().getStyle().setHeight(viewHeight, Unit.PX);

    for (MarkHandle info : diff) {
      info.position(r);
    }
    for (MarkHandle info : comments) {
      info.position(r);
    }
  }

  @Override
  protected void onUnload() {
    super.onUnload();
    if (dragging) {
      DOM.releaseCapture(viewport.getElement());
    }
  }

  @Override
  public void onClick(ClickEvent e) {
    if (e.getY() < viewport.getElement().getOffsetTop()) {
      CodeMirror.handleVimKey(cmB, "<PageUp>");
    } else {
      CodeMirror.handleVimKey(cmB, "<PageDown>");
    }
    cmB.focus();
  }

  @UiHandler("viewport")
  void onMouseDown(MouseDownEvent e) {
    if (cmB != null) {
      dragging = true;
      ratio = ratio(cmB.getScrollInfo());
      startY = e.getY();
      viewport.addStyleName(style.viewportDrag());
      DOM.setCapture(viewport.getElement());
      e.preventDefault();
      e.stopPropagation();
    }
  }

  @UiHandler("viewport")
  void onMouseMove(MouseMoveEvent e) {
    if (dragging) {
      int y = e.getRelativeY(getElement()) - startY;
      cmB.scrollToY(Math.max(0, y / ratio));
      e.preventDefault();
      e.stopPropagation();
    }
  }

  @UiHandler("viewport")
  void onMouseUp(MouseUpEvent e) {
    if (dragging) {
      dragging = false;
      DOM.releaseCapture(viewport.getElement());
      viewport.removeStyleName(style.viewportDrag());
      e.preventDefault();
      e.stopPropagation();
    }
  }

  private double ratio(ScrollInfo si) {
    double barHeight = si.getClientHeight();
    double contentHeight = si.getHeight();
    return barHeight / contentHeight;
  }

  MarkHandle add(CodeMirror cm, int line, int height, MarkType type) {
    MarkHandle mark = new MarkHandle(cm, line, height);
    switch (type) {
      case COMMENT:
        mark.addStyleName(style.comment());
        comments.add(mark);
        break;
      case DRAFT:
        mark.addStyleName(style.draft());
        mark.getElement().setInnerText("*");
        comments.add(mark);
        break;
      case INSERT:
        mark.addStyleName(style.insert());
        diff.add(mark);
        break;
      case DELETE:
        mark.addStyleName(style.delete());
        diff.add(mark);
        break;
      case EDIT:
        mark.edit = DOM.createDiv();
        mark.edit.setClassName(style.halfGutter());
        mark.getElement().appendChild(mark.edit);
        mark.addStyleName(style.insert());
        diff.add(mark);
        break;
    }
    if (cmB != null) {
      mark.position(ratio(cmB.getScrollInfo()));
    }
    ((HTMLPanel) getWidget()).add(mark);
    return mark;
  }

  void clearDiffMarkers() {
    for (MarkHandle mark : diff) {
      mark.removeFromParent();
    }
    diff.clear();
  }

  class MarkHandle extends Widget implements ClickHandler {
    private static final int MIN_HEIGHT = 3;

    private final CodeMirror cm;
    private final int line;
    private final int height;
    private Element edit;

    MarkHandle(CodeMirror cm, int line, int height) {
      this.cm = cm;
      this.line = line;
      this.height = height;

      setElement(DOM.createDiv());
      setStyleName(style.gutter());
      addDomHandler(this, ClickEvent.getType());
    }

    void position(double ratio) {
      double y = cm.heightAtLine(line, "local");
      getElement().getStyle().setTop(y * ratio, Unit.PX);
      if (height > 1) {
        double e = cm.heightAtLine(line + height, "local");
        double h = Math.max(MIN_HEIGHT, (e - y) * ratio);
        getElement().getStyle().setHeight(h, Unit.PX);
        if (edit != null) {
          edit.getStyle().setHeight(h, Unit.PX);
        }
      }
    }

    @Override
    public void onClick(ClickEvent event) {
      double y = cm.heightAtLine(line, "local");
      double viewport = cm.getScrollInfo().getClientHeight();
      cm.setCursor(LineCharacter.create(line));
      cm.scrollToY(y - 0.5 * viewport);
      cm.focus();
    }

    void remove() {
      removeFromParent();
      comments.remove(this);
    }
  }
}
