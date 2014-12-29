package com.google.gerrit.client.diff;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.Widget;

import net.codemirror.lib.CodeMirror;
import net.codemirror.lib.CodeMirror.RegisteredHandler;
import net.codemirror.lib.LineCharacter;

class ScrollbarAnnotation extends Widget implements ClickHandler {
  private final CodeMirror cm;
  private CodeMirror cmB;
  private RegisteredHandler refresh;
  private LineCharacter from;
  private LineCharacter to;
  private double scale;

  ScrollbarAnnotation(CodeMirror cm) {
    setElement((Element) DOM.createDiv());
    getElement().setAttribute("not-content", "true");
    addDomHandler(this, ClickEvent.getType());
    this.cm = cm;
    this.cmB = cm;
  }

  void clear() {
    removeFromParent();
  }

  void at(LineCharacter from, LineCharacter to) {
    this.from = from;
    this.to = to;
  }

  void renderOn(CodeMirror cm) {
    this.cmB = cm;
  }

  @Override
  protected void onLoad() {
    cmB.getWrapperElement().appendChild(getElement());
    refresh = cmB.on("refresh", new Runnable() {
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

    int line = from.getLine();
    int h = to.getLine() - line;
    if (h > 5) {
      // Map click inside of the annotation to the relative position
      // within the region covered by the annotation.
      double s = ((double) event.getY()) / getElement().getOffsetHeight();
      line += (int) (s * h);
    }

    double y = cm.heightAtLine(line, "local");
    double viewport = cm.getScrollInfo().getClientHeight();
    cm.setCursor(from);
    cm.scrollTo(0, y - 0.5 * viewport);
    cm.focus();
  }
}
