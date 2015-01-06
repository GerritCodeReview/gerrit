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

package net.codemirror.lib;

import static com.google.gwt.dom.client.Style.Display.INLINE_BLOCK;
import static com.google.gwt.dom.client.Style.Unit.PX;
import static net.codemirror.lib.CodeMirror.style;
import static net.codemirror.lib.CodeMirror.LineClassWhere.WRAP;

import com.google.gerrit.client.diff.DisplaySide;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.DOM;

import net.codemirror.lib.CodeMirror.LineHandle;

import java.util.Objects;

/** Additional features added to CodeMirror by Gerrit Code Review. */
public class Extras {
  static final native Extras get(CodeMirror c) /*-{ return c.gerritExtras }-*/;
  private static final native void set(CodeMirror c, Extras e)
  /*-{ c.gerritExtras = e }-*/;

  static void attach(CodeMirror c) {
    set(c, new Extras(c));
  }

  private final CodeMirror cm;
  private Element margin;
  private DisplaySide side;
  private double charWidthPx;
  private double lineHeightPx;
  private LineHandle activeLine;

  private Extras(CodeMirror cm) {
    this.cm = cm;
  }

  public DisplaySide side() {
    return side;
  }

  public void side(DisplaySide s) {
    side = s;
  }

  public double charWidthPx() {
    if (charWidthPx <= 1) {
      int len = 100;
      StringBuilder s = new StringBuilder();
      for (int i = 0; i < len; i++) {
        s.append('m');
      }

      Element e = DOM.createSpan();
      e.getStyle().setDisplay(INLINE_BLOCK);
      e.setInnerText(s.toString());

      cm.measure().appendChild(e);
      charWidthPx = ((double) e.getOffsetWidth()) / len;
      e.removeFromParent();
    }
    return charWidthPx;
  }

  public double lineHeightPx() {
    if (lineHeightPx <= 1) {
      Element p = DOM.createDiv();
      int lines = 1;
      for (int i = 0; i < lines; i++) {
        Element e = DOM.createDiv();
        p.appendChild(e);

        Element pre = DOM.createElement("pre");
        pre.setInnerText("gqyŚŻŹŃ");
        e.appendChild(pre);
      }

      cm.measure().appendChild(p);
      lineHeightPx = ((double) p.getOffsetHeight()) / lines;
      p.removeFromParent();
    }
    return lineHeightPx;
  }

  public void lineLength(int columns) {
    if (margin == null) {
      margin = DOM.createDiv();
      margin.setClassName(style().margin());
      cm.mover().appendChild(margin);
    }
    margin.getStyle().setMarginLeft(columns * charWidthPx(), PX);
  }

  public void showTabs(boolean show) {
    Element e = cm.getWrapperElement();
    if (show) {
      e.addClassName(style().showTabs());
    } else {
      e.removeClassName(style().showTabs());
    }
  }

  public final boolean hasActiveLine() {
    return activeLine != null;
  }

  public final LineHandle activeLine() {
    return activeLine;
  }

  public final boolean activeLine(LineHandle line) {
    if (Objects.equals(activeLine, line)) {
      return false;
    }

    if (activeLine != null) {
      cm.removeLineClass(activeLine, WRAP, style().activeLine());
    }
    activeLine = line;
    cm.addLineClass(activeLine, WRAP, style().activeLine());
    return true;
  }

  public final void clearActiveLine() {
    if (activeLine != null) {
      cm.removeLineClass(activeLine, WRAP, style().activeLine());
      activeLine = null;
    }
  }
}
