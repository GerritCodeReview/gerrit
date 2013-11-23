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

import com.google.gerrit.client.changes.CommentInfo;
import com.google.gerrit.client.diff.PaddingManager.PaddingWidgetWrapper;
import com.google.gerrit.client.diff.SidePanel.GutterWrapper;
import com.google.gwt.event.dom.client.MouseOutEvent;
import com.google.gwt.event.dom.client.MouseOutHandler;
import com.google.gwt.event.dom.client.MouseOverEvent;
import com.google.gwt.event.dom.client.MouseOverHandler;
import com.google.gwt.user.client.ui.Composite;

import net.codemirror.lib.CodeMirror;
import net.codemirror.lib.Configuration;
import net.codemirror.lib.TextMarker;
import net.codemirror.lib.TextMarker.FromTo;

/** An HtmlPanel for displaying a comment */
abstract class CommentBox extends Composite {
  static {
    Resources.I.style().ensureInjected();
  }

  private PaddingManager widgetManager;
  private PaddingWidgetWrapper selfWidgetWrapper;
  private SideBySide2 parent;
  private CodeMirror cm;
  private DisplaySide side;
  private DiffChunkInfo diffChunkInfo;
  private GutterWrapper gutterWrapper;
  private FromTo fromTo;
  private TextMarker rangeMarker;
  private TextMarker rangeHighlightMarker;

  CommentBox(CodeMirror cm, CommentInfo info, DisplaySide side) {
    this.cm = cm;
    this.side = side;
    CommentRange range = info.range();
    if (range != null) {
      fromTo = FromTo.create(range);
      rangeMarker = cm.markText(
          fromTo.getFrom(),
          fromTo.getTo(),
          Configuration.create()
              .set("className", DiffTable.style.range()));
    }
    addDomHandler(new MouseOverHandler() {
      @Override
      public void onMouseOver(MouseOverEvent event) {
        setRangeHighlight(true);
      }
    }, MouseOverEvent.getType());
    addDomHandler(new MouseOutHandler() {
      @Override
      public void onMouseOut(MouseOutEvent event) {
        setRangeHighlight(isOpen());
      }
    }, MouseOutEvent.getType());
  }

  @Override
  protected void onLoad() {
    resizePaddingWidget();
  }

  void resizePaddingWidget() {
    if (!getCommentInfo().has_line()) {
      return;
    }
    parent.defer(new Runnable() {
      @Override
      public void run() {
        assert selfWidgetWrapper != null;
        selfWidgetWrapper.getWidget().changed();
        if (diffChunkInfo != null) {
          parent.resizePaddingOnOtherSide(side, diffChunkInfo.getEnd());
        } else {
          assert widgetManager != null;
          widgetManager.resizePaddingWidget();
        }
      }
    });
  }

  abstract CommentInfo getCommentInfo();
  abstract boolean isOpen();

  void setOpen(boolean open) {
    resizePaddingWidget();
    setRangeHighlight(open);
    getCm().focus();
  }

  PaddingManager getPaddingManager() {
    return widgetManager;
  }

  void setPaddingManager(PaddingManager manager) {
    widgetManager = manager;
  }

  void setSelfWidgetWrapper(PaddingWidgetWrapper wrapper) {
    selfWidgetWrapper = wrapper;
  }

  PaddingWidgetWrapper getSelfWidgetWrapper() {
    return selfWidgetWrapper;
  }

  void setDiffChunkInfo(DiffChunkInfo info) {
    this.diffChunkInfo = info;
  }

  void setParent(SideBySide2 parent) {
    this.parent = parent;
  }

  void setGutterWrapper(GutterWrapper wrapper) {
    gutterWrapper = wrapper;
  }

  void setRangeHighlight(boolean highlight) {
    if (fromTo != null) {
      if (highlight && rangeHighlightMarker == null) {
        rangeHighlightMarker = cm.markText(
            fromTo.getFrom(),
            fromTo.getTo(),
            Configuration.create()
                .set("className", DiffTable.style.rangeHighlight()));
      } else if (!highlight && rangeHighlightMarker != null) {
        rangeHighlightMarker.clear();
        rangeHighlightMarker = null;
      }
    }
  }

  void clearRange() {
    if (rangeMarker != null) {
      rangeMarker.clear();
    }
  }

  GutterWrapper getGutterWrapper() {
    return gutterWrapper;
  }

  DisplaySide getSide() {
    return side;
  }

  CodeMirror getCm() {
    return cm;
  }
}
