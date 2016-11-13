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

import com.google.gwt.resources.client.CssResource;
import java.util.ArrayList;
import java.util.List;
import net.codemirror.lib.CodeMirror;
import net.codemirror.lib.Pos;

/** Displays overview of all edits and comments in this file. */
class Scrollbar {
  static {
    Resources.I.scrollbarStyle().ensureInjected();
  }

  interface Style extends CssResource {
    String comment();

    String draft();

    String insert();

    String delete();

    String edit();
  }

  private final List<ScrollbarAnnotation> diff = new ArrayList<>();
  private final DiffTable parent;

  Scrollbar(DiffTable d) {
    parent = d;
  }

  ScrollbarAnnotation comment(CodeMirror cm, int line) {
    ScrollbarAnnotation a = new ScrollbarAnnotation(cm);
    a.setStyleName(Resources.I.scrollbarStyle().comment());
    a.at(line);
    a.getElement().setInnerText("\u2736"); // Six pointed black star
    parent.add(a);
    return a;
  }

  ScrollbarAnnotation draft(CodeMirror cm, int line) {
    ScrollbarAnnotation a = new ScrollbarAnnotation(cm);
    a.setStyleName(Resources.I.scrollbarStyle().draft());
    a.at(line);
    a.getElement().setInnerText("\u270D"); // Writing hand
    parent.add(a);
    return a;
  }

  ScrollbarAnnotation insert(CodeMirror cm, int line, int len) {
    ScrollbarAnnotation a = diff(cm, line, len);
    a.setStyleName(Resources.I.scrollbarStyle().insert());
    parent.add(a);
    return a;
  }

  ScrollbarAnnotation delete(CodeMirror cmA, CodeMirror cmB, int line, int len) {
    ScrollbarAnnotation a = diff(cmA, line, len);
    a.setStyleName(Resources.I.scrollbarStyle().delete());
    a.renderOn(cmB);
    parent.add(a);
    return a;
  }

  ScrollbarAnnotation edit(CodeMirror cm, int line, int len) {
    ScrollbarAnnotation a = diff(cm, line, len);
    a.setStyleName(Resources.I.scrollbarStyle().edit());
    parent.add(a);
    return a;
  }

  private ScrollbarAnnotation diff(CodeMirror cm, int s, int n) {
    ScrollbarAnnotation a = new ScrollbarAnnotation(cm);
    a.at(Pos.create(s), Pos.create(s + n));
    diff.add(a);
    return a;
  }

  void removeDiffAnnotations() {
    for (ScrollbarAnnotation a : diff) {
      a.remove();
    }
    diff.clear();
  }
}
