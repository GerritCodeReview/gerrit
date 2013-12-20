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

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.changes.CommentInfo;
import com.google.gerrit.client.diff.PaddingManager.PaddingWidgetWrapper;
import com.google.gerrit.client.patches.SkippedLine;
import com.google.gerrit.client.rpc.CallbackGroup;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.ui.CommentLinkProcessor;
import com.google.gerrit.common.changes.Side;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.dom.client.Style.Unit;

import net.codemirror.lib.CodeMirror;
import net.codemirror.lib.Configuration;
import net.codemirror.lib.LineWidget;
import net.codemirror.lib.CodeMirror.LineHandle;
import net.codemirror.lib.TextMarker.FromTo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Tracks comment widgets for {@link SideBySide2}. */
class CommentManager {
  private final SideBySide2 host;
  private final PatchSet.Id base;
  private final PatchSet.Id revision;
  private final String path;
  private final CommentLinkProcessor commentLinkProcessor;

  private final Map<String, PublishedBox> published;
  private final Map<LineHandle, CommentBox> lineActiveBox;
  private final Map<LineHandle, List<PublishedBox>> linePublishedBoxes;
  private final Map<LineHandle, PaddingManager> linePaddingManager;
  private final Set<DraftBox> unsavedDrafts;

  CommentManager(SideBySide2 host,
      PatchSet.Id base, PatchSet.Id revision,
      String path,
      CommentLinkProcessor clp) {
    this.host = host;
    this.base = base;
    this.revision = revision;
    this.path = path;
    this.commentLinkProcessor = clp;

    published = new HashMap<String, PublishedBox>();
    lineActiveBox = new HashMap<LineHandle, CommentBox>();
    linePublishedBoxes = new HashMap<LineHandle, List<PublishedBox>>();
    linePaddingManager = new HashMap<LineHandle, PaddingManager>();
    unsavedDrafts = new HashSet<DraftBox>();
  }

  SideBySide2 getSideBySide2() {
    return host;
  }

  void setExpandAllComments(boolean b) {
    for (PublishedBox box : published.values()) {
      box.setOpen(b);
    }
  }

  void render(CommentsCollections in) {
    if (in.publishedBase != null) {
      renderPublished(DisplaySide.A, in.publishedBase);
    }
    if (in.publishedRevision != null) {
      renderPublished(DisplaySide.B, in.publishedRevision);
    }
    if (in.draftsBase != null) {
      renderDrafts(DisplaySide.A, in.draftsBase);
    }
    if (in.draftsRevision != null) {
      renderDrafts(DisplaySide.B, in.draftsRevision);
    }
  }

  private void renderPublished(DisplaySide forSide, JsArray<CommentInfo> in) {
    for (CommentInfo info : Natives.asList(in)) {
      DisplaySide side = displaySide(info, forSide);
      if (side == null) {
        continue;
      }

      CodeMirror cm = host.getCmFromSide(side);
      PublishedBox box = new PublishedBox(this, cm, commentLinkProcessor,
          getPatchSetIdFromSide(side), info);
      published.put(info.id(), box);
      if (!info.has_line()) {
        host.diffTable.addFileCommentBox(box);
        continue;
      }

      int line = info.line() - 1;
      LineHandle handle = cm.getLineHandle(line);
      if (linePublishedBoxes.containsKey(handle)) {
        linePublishedBoxes.get(handle).add(box);
      } else {
        List<PublishedBox> list = new ArrayList<PublishedBox>(4);
        list.add(box);
        linePublishedBoxes.put(handle, list);
      }
      lineActiveBox.put(handle, box);
      addCommentBox(info, box);
    }
  }

  private void renderDrafts(DisplaySide forSide, JsArray<CommentInfo> in) {
    for (CommentInfo info : Natives.asList(in)) {
      DisplaySide side = displaySide(info, forSide);
      if (side == null) {
        continue;
      }

      CodeMirror cm = host.getCmFromSide(side);
      DraftBox box = new DraftBox(this, cm, commentLinkProcessor,
          getPatchSetIdFromSide(side), info);
      if (info.in_reply_to() != null) {
        PublishedBox r = published.get(info.in_reply_to());
        if (r != null) {
          r.registerReplyBox(box);
        }
      }
      if (!info.has_line()) {
        host.diffTable.addFileCommentBox(box);
        continue;
      }

      lineActiveBox.put(cm.getLineHandle(info.line() - 1), box);
      addCommentBox(info, box);
    }
  }

  private DisplaySide displaySide(CommentInfo info, DisplaySide forSide) {
    if (info.side() == Side.PARENT) {
      return base == null ? DisplaySide.A : null;
    }
    return forSide;
  }

  List<SkippedLine> splitSkips(int context, List<SkippedLine> skips) {
    // TODO: This is not optimal, but shouldn't be too costly in most cases.
    // Maybe rewrite after done keeping track of diff chunk positions.
    for (CommentBox box : lineActiveBox.values()) {
      int boxLine = box.getCommentInfo().line();
      boolean sideA = host.getSideFromCm(box.getCm()) == DisplaySide.A;

      List<SkippedLine> temp = new ArrayList<SkippedLine>(skips.size() + 2);
      for (SkippedLine skip : skips) {
        int startLine = sideA ? skip.getStartA() : skip.getStartB();
        int deltaBefore = boxLine - startLine;
        int deltaAfter = startLine + skip.getSize() - boxLine;
        if (deltaBefore < -context || deltaAfter < -context) {
          temp.add(skip); // Size guaranteed to be greater than 1
        } else if (deltaBefore > context && deltaAfter > context) {
          SkippedLine before = new SkippedLine(
              skip.getStartA(), skip.getStartB(),
              skip.getSize() - deltaAfter - context);
          skip.incrementStart(deltaBefore + context);
          checkAndAddSkip(temp, before);
          checkAndAddSkip(temp, skip);
        } else if (deltaAfter > context) {
          skip.incrementStart(deltaBefore + context);
          checkAndAddSkip(temp, skip);
        } else if (deltaBefore > context) {
          skip.reduceSize(deltaAfter + context);
          checkAndAddSkip(temp, skip);
        }
      }
      if (temp.isEmpty()) {
        return temp;
      }
      skips = temp;
    }
    return skips;
  }

  private static void checkAndAddSkip(List<SkippedLine> out, SkippedLine s) {
    if (s.getSize() > 1) {
      out.add(s);
    }
  }

  Runnable toggleOpenBox(final CodeMirror cm) {
    return new Runnable() {
      public void run() {
        CommentBox box = lineActiveBox.get(cm.getActiveLine());
        if (box != null) {
          box.setOpen(!box.isOpen());
        }
      }
    };
  }

  Runnable openClosePublished(final CodeMirror cm) {
    return new Runnable() {
      @Override
      public void run() {
        if (cm.hasActiveLine()) {
          List<PublishedBox> list =
              linePublishedBoxes.get(cm.getActiveLine());
          if (list == null) {
            return;
          }
          boolean open = false;
          for (PublishedBox box : list) {
            if (!box.isOpen()) {
              open = true;
              break;
            }
          }
          for (PublishedBox box : list) {
            box.setOpen(open);
          }
        }
      }
    };
  }

  Runnable insertNewDraft(final CodeMirror cm) {
    if (!Gerrit.isSignedIn()) {
      return new Runnable() {
        @Override
        public void run() {
          Gerrit.doSignIn(host.getToken());
        }
     };
    }

    return new Runnable() {
      public void run() {
        LineHandle handle = cm.getActiveLine();
        int line = cm.getLineNumber(handle);
        CommentBox box = lineActiveBox.get(handle);
        FromTo fromTo = cm.getSelectedRange();
        if (cm.somethingSelected()) {
          lineActiveBox.put(handle,
              newRangeDraft(cm, line, fromTo.getTo().getLine() == line ? fromTo : null));
          cm.setSelection(cm.getCursor());
        } else if (box == null) {
          lineActiveBox.put(handle, newRangeDraft(cm, line, null));
        } else if (box instanceof DraftBox) {
          ((DraftBox) box).setEdit(true);
        } else {
          ((PublishedBox) box).doReply();
        }
      }
    };
  }

  private DraftBox newRangeDraft(CodeMirror cm, int line, FromTo fromTo) {
    DisplaySide side = host.getSideFromCm(cm);
    return addDraftBox(CommentInfo.createRange(
        path,
        getStoredSideFromDisplaySide(side),
        line + 1,
        null,
        null,
        CommentRange.create(fromTo)), side);
  }

  DraftBox newFileDraft(DisplaySide side) {
    return addDraftBox(CommentInfo.createFile(
      path,
      getStoredSideFromDisplaySide(side),
      null, null), side);
  }

  CommentInfo createReply(CommentInfo replyTo) {
    if (!replyTo.has_line() && replyTo.range() == null) {
      return CommentInfo.createFile(path, replyTo.side(), replyTo.id(), null);
    } else {
      return CommentInfo.createRange(path, replyTo.side(), replyTo.line(),
          replyTo.id(), null, replyTo.range());
    }
  }

  DraftBox addDraftBox(CommentInfo info, DisplaySide side) {
    CodeMirror cm = host.getCmFromSide(side);
    final DraftBox box = new DraftBox(this, cm, commentLinkProcessor,
        getPatchSetIdFromSide(side), info);
    if (info.id() == null) {
      Scheduler.get().scheduleDeferred(new ScheduledCommand() {
        @Override
        public void execute() {
          box.setOpen(true);
          box.setEdit(true);
        }
      });
    }
    if (!info.has_line()) {
      return box;
    }
    addCommentBox(info, box);
    box.setVisible(true);
    LineHandle handle = cm.getLineHandle(info.line() - 1);
    lineActiveBox.put(handle, box);
    return box;
  }

  private CommentBox addCommentBox(CommentInfo info, final CommentBox box) {
    host.diffTable.add(box);
    CodeMirror cm = box.getCm();
    CodeMirror other = host.otherCm(cm);
    int line = info.line() - 1; // CommentInfo is 1-based, but CM is 0-based
    LineHandle handle = cm.getLineHandle(line);
    PaddingManager manager;
    if (linePaddingManager.containsKey(handle)) {
      manager = linePaddingManager.get(handle);
    } else {
      // Estimated height at 28px, fixed by deferring after display
      manager = new PaddingManager(host.addPaddingWidget(cm, line, 0, Unit.PX, 0));
      linePaddingManager.put(handle, manager);
    }

    int lineToPad = host.lineOnOther(host.getSideFromCm(cm), line).getLine();
    LineHandle otherHandle = other.getLineHandle(lineToPad);
    ColoringManager diffMgr = host.getColoringManager();
    DiffChunkInfo myChunk = diffMgr.getDiffChunk(host.getSideFromCm(cm), line);
    DiffChunkInfo otherChunk = diffMgr.getDiffChunk(host.getSideFromCm(other), lineToPad);
    PaddingManager otherManager;
    if (linePaddingManager.containsKey(otherHandle)) {
      otherManager = linePaddingManager.get(otherHandle);
    } else {
      otherManager =
          new PaddingManager(host.addPaddingWidget(other, lineToPad, 0, Unit.PX, 0));
      linePaddingManager.put(otherHandle, otherManager);
    }
    if ((myChunk == null && otherChunk == null) || (myChunk != null && otherChunk != null)) {
      PaddingManager.link(manager, otherManager);
    }

    int index = manager.getCurrentCount();
    manager.insert(box, index);
    Configuration config = Configuration.create()
      .set("coverGutter", true)
      .set("insertAt", index)
      .set("noHScroll", true);
    LineWidget boxWidget = host.addLineWidget(cm, line, box, config);
    box.setPaddingManager(manager);
    box.setSelfWidgetWrapper(new PaddingWidgetWrapper(boxWidget, box.getElement()));
    if (otherChunk == null) {
      box.setDiffChunkInfo(myChunk);
    }
    box.setGutterWrapper(host.diffTable.sidePanel.addGutter(cm, info.line() - 1,
        box instanceof DraftBox
          ? SidePanel.GutterType.DRAFT
          : SidePanel.GutterType.COMMENT));
    if (box instanceof DraftBox) {
      boxWidget.onRedraw(new Runnable() {
        @Override
        public void run() {
          DraftBox draftBox = (DraftBox) box;
          if (draftBox.isEdit()) {
            draftBox.editArea.setFocus(true);
          }
        }
      });
    }
    return box;
  }

  void removeDraft(DraftBox box) {
    int line = box.getCommentInfo().line() - 1;
    LineHandle handle = box.getCm().getLineHandle(line);
    lineActiveBox.remove(handle);
    if (linePublishedBoxes.containsKey(handle)) {
      List<PublishedBox> list = linePublishedBoxes.get(handle);
      lineActiveBox.put(handle, list.get(list.size() - 1));
    }
    unsavedDrafts.remove(box);
  }

  void addFileCommentBox(CommentBox box) {
    host.diffTable.addFileCommentBox(box);
  }

  void removeFileCommentBox(DraftBox box) {
    host.diffTable.onRemoveDraftBox(box);
  }

  void resizePadding(LineHandle handle) {
    CommentBox box = lineActiveBox.get(handle);
    if (box != null) {
      box.resizePaddingWidget();
    }
  }

  void setUnsaved(DraftBox box, boolean isUnsaved) {
    if (isUnsaved) {
      unsavedDrafts.add(box);
    } else {
      unsavedDrafts.remove(box);
    }
  }

  void saveAllDrafts(CallbackGroup cb) {
    for (DraftBox box : unsavedDrafts) {
      box.save(cb);
    }
  }

  private Side getStoredSideFromDisplaySide(DisplaySide side) {
    return side == DisplaySide.A && base == null ? Side.PARENT : Side.REVISION;
  }

  private PatchSet.Id getPatchSetIdFromSide(DisplaySide side) {
    return side == DisplaySide.A && base != null ? base : revision;
  }
}
