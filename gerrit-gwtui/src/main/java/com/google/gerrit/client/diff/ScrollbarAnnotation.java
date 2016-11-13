// Copyright (C) 2014 The Android Open Source Project
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

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.Widget;
import net.codemirror.lib.CodeMirror;
import net.codemirror.lib.CodeMirror.RegisteredHandler;
import net.codemirror.lib.Pos;

/** Displayed on the vertical scrollbar to place a chunk or comment. */
class ScrollbarAnnotation extends Widget implements ClickHandler {
  private final CodeMirror cm;
  private CodeMirror cmB;
  private RegisteredHandler refresh;
  private Pos from;
  private Pos to;
  private double scale;

  ScrollbarAnnotation(CodeMirror cm) {
    setElement((Element) DOM.createDiv());
    getElement().setAttribute("not-content", "true");
    addDomHandler(this, ClickEvent.getType());
    this.cm = cm;
    this.cmB = cm;
  }

  void remove() {
    removeFromParent();
  }

  void at(int line) {
    at(Pos.create(line), Pos.create(line + 1));
  }

  void at(Pos from, Pos to) {
    this.from = from;
    this.to = to;
  }

  void renderOn(CodeMirror cm) {
    this.cmB = cm;
  }

  @Override
  protected void onLoad() {
    cmB.getWrapperElement().appendChild(getElement());
    refresh =
        cmB.on(
            "refresh",
            new Runnable() {
              @Override
              public void run() {
                if (updateScale()) {
                  updatePosition();
                }
              }
            });
    updateScale();
    updatePosition();
  }

  @Override
  protected void onUnload() {
    cmB.off("refresh", refresh);
  }

  private boolean updateScale() {
    double old = scale;
    double docHeight = cmB.getWrapperElement().getClientHeight();
    double lineHeight = cmB.heightAtLine(cmB.lastLine() + 1, "local");
    scale = (docHeight - cmB.barHeight()) / lineHeight;
    return old != scale;
  }

  private void updatePosition() {
    double top = cm.charCoords(from, "local").top() * scale;
    double bottom = cm.charCoords(to, "local").bottom() * scale;

    Element e = getElement();
    e.getStyle().setTop(top, Unit.PX);
    e.getStyle().setWidth(Math.max(2, cm.barWidth() - 1), Unit.PX);
    e.getStyle().setHeight(Math.max(3, bottom - top), Unit.PX);
  }

  @Override
  public void onClick(ClickEvent event) {
    event.stopPropagation();

    int line = from.line();
    int h = to.line() - line;
    if (h > 5) {
      // Map click inside of the annotation to the relative position
      // within the region covered by the annotation.
      double s = ((double) event.getY()) / getElement().getOffsetHeight();
      line += (int) (s * h);
    }

    double y = cm.heightAtLine(line, "local");
    double viewport = cm.getScrollInfo().clientHeight();
    cm.setCursor(from);
    cm.scrollTo(0, y - 0.5 * viewport);
    cm.focus();
  }
}
