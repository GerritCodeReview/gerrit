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

import com.google.gwt.resources.client.CssResource;

import net.codemirror.lib.CodeMirror;
import net.codemirror.lib.LineCharacter;

import java.util.ArrayList;
import java.util.List;

/** Displays overview of all edits and comments in this file. */
class OverviewBar {
  static {
    Resources.I.overviewBarStyle().ensureInjected();
  }

  interface Style extends CssResource {
    String comment();
    String draft();
    String insert();
    String delete();
    String edit();
  }

  private final DiffTable parent;
  private final List<ScrollbarAnnotation> diff;

  OverviewBar(DiffTable d) {
    parent = d;
    diff = new ArrayList<>();
  }

  ScrollbarAnnotation comment(CodeMirror cm, LineCharacter s, LineCharacter e) {
    ScrollbarAnnotation a = new ScrollbarAnnotation(cm);
    a.setStyleName(Resources.I.overviewBarStyle().comment());
    a.at(s, e);
    parent.add(a);
    return a;
  }

  ScrollbarAnnotation draft(CodeMirror cm, LineCharacter s, LineCharacter e) {
    ScrollbarAnnotation a = new ScrollbarAnnotation(cm);
    a.setStyleName(Resources.I.overviewBarStyle().draft());
    a.at(s, e);
    parent.add(a);
    return a;
  }

  ScrollbarAnnotation insert(CodeMirror cm, int line, int len) {
    ScrollbarAnnotation a = diff(cm, line, len);
    a.setStyleName(Resources.I.overviewBarStyle().insert());
    parent.add(a);
    return a;
  }

  ScrollbarAnnotation delete(CodeMirror cmA, CodeMirror cmB, int line, int len) {
    ScrollbarAnnotation a = diff(cmA, line, len);
    a.setStyleName(Resources.I.overviewBarStyle().delete());
    a.renderOn(cmB);
    parent.add(a);
    return a;
  }

  ScrollbarAnnotation edit(CodeMirror cm, int line, int len) {
    ScrollbarAnnotation a = diff(cm, line, len);
    a.setStyleName(Resources.I.overviewBarStyle().edit());
    parent.add(a);
    return a;
  }

  private ScrollbarAnnotation diff(CodeMirror cm, int s, int n) {
    ScrollbarAnnotation a = new ScrollbarAnnotation(cm);
    a.at(CodeMirror.pos(s), CodeMirror.pos(s + n));
    diff.add(a);
    return a;
  }

  void clearDiffMarkers() {
    for (ScrollbarAnnotation mark : diff) {
      mark.clear();
    }
    diff.clear();
  }
}
