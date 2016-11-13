// Copyright (C) 2013 The Android Open Source Project
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

import com.google.gerrit.client.changes.CommentInfo;
import com.google.gwt.event.dom.client.MouseOutEvent;
import com.google.gwt.event.dom.client.MouseOutHandler;
import com.google.gwt.event.dom.client.MouseOverEvent;
import com.google.gwt.event.dom.client.MouseOverHandler;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.user.client.ui.Composite;
import net.codemirror.lib.CodeMirror;
import net.codemirror.lib.Configuration;
import net.codemirror.lib.Pos;
import net.codemirror.lib.TextMarker;
import net.codemirror.lib.TextMarker.FromTo;

/** An HtmlPanel for displaying a comment */
abstract class CommentBox extends Composite {
  static {
    Resources.I.style().ensureInjected();
  }

  interface Style extends CssResource {
    String commentWidgets();

    String commentBox();

    String contents();

    String message();

    String header();

    String summary();

    String date();

    String goPrev();

    String goNext();

    String goUp();
  }

  private final CommentGroup group;
  private ScrollbarAnnotation annotation;
  private FromTo fromTo;
  private TextMarker rangeMarker;
  private TextMarker rangeHighlightMarker;

  CommentBox(CommentGroup group, CommentRange range) {
    this.group = group;
    if (range != null) {
      DiffScreen screen = group.getManager().host;
      int startCmLine = screen.getCmLine(range.startLine() - 1, group.getSide());
      int endCmLine = screen.getCmLine(range.endLine() - 1, group.getSide());
      fromTo =
          FromTo.create(
              Pos.create(startCmLine, range.startCharacter()),
              Pos.create(endCmLine, range.endCharacter()));
      rangeMarker =
          group
              .getCm()
              .markText(
                  fromTo.from(),
                  fromTo.to(),
                  Configuration.create().set("className", Resources.I.diffTableStyle().range()));
    }
    addDomHandler(
        new MouseOverHandler() {
          @Override
          public void onMouseOver(MouseOverEvent event) {
            setRangeHighlight(true);
          }
        },
        MouseOverEvent.getType());
    addDomHandler(
        new MouseOutHandler() {
          @Override
          public void onMouseOut(MouseOutEvent event) {
            setRangeHighlight(isOpen());
          }
        },
        MouseOutEvent.getType());
  }

  abstract CommentInfo getCommentInfo();

  abstract boolean isOpen();

  void setOpen(boolean open) {
    group.resize();
    setRangeHighlight(open);
    getCm().focus();
  }

  CommentGroup getCommentGroup() {
    return group;
  }

  CommentManager getCommentManager() {
    return group.getCommentManager();
  }

  ScrollbarAnnotation getAnnotation() {
    return annotation;
  }

  void setAnnotation(ScrollbarAnnotation mh) {
    annotation = mh;
  }

  void setRangeHighlight(boolean highlight) {
    if (fromTo != null) {
      if (highlight && rangeHighlightMarker == null) {
        rangeHighlightMarker =
            group
                .getCm()
                .markText(
                    fromTo.from(),
                    fromTo.to(),
                    Configuration.create()
                        .set("className", Resources.I.diffTableStyle().rangeHighlight()));
      } else if (!highlight && rangeHighlightMarker != null) {
        rangeHighlightMarker.clear();
        rangeHighlightMarker = null;
      }
    }
  }

  void clearRange() {
    if (rangeMarker != null) {
      rangeMarker.clear();
      rangeMarker = null;
    }
  }

  CodeMirror getCm() {
    return group.getCm();
  }

  FromTo getFromTo() {
    return fromTo;
  }
}
