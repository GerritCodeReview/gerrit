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

import com.google.gerrit.client.DiffObject;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.changes.CommentInfo;
import com.google.gerrit.client.patches.SkippedLine;
import com.google.gerrit.client.rpc.CallbackGroup;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.ui.CommentLinkProcessor;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.core.client.JsArray;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import net.codemirror.lib.CodeMirror;
import net.codemirror.lib.Pos;
import net.codemirror.lib.TextMarker.FromTo;

/** Tracks comment widgets for {@link DiffScreen}. */
abstract class CommentManager {
  private final Project.NameKey project;
  private final DiffObject base;
  private final PatchSet.Id revision;
  private final String path;
  private final CommentLinkProcessor commentLinkProcessor;
  final SortedMap<Integer, CommentGroup> sideA;
  final SortedMap<Integer, CommentGroup> sideB;
  private final Map<String, PublishedBox> published;
  private final Set<DraftBox> unsavedDrafts;
  final DiffScreen host;
  private boolean attached;
  private boolean expandAll;
  private boolean open;

  CommentManager(
      DiffScreen host,
      @Nullable Project.NameKey project,
      DiffObject base,
      PatchSet.Id revision,
      String path,
      CommentLinkProcessor clp,
      boolean open) {
    this.host = host;
    this.project = project;
    this.base = base;
    this.revision = revision;
    this.path = path;
    this.commentLinkProcessor = clp;
    this.open = open;

    published = new HashMap<>();
    unsavedDrafts = new HashSet<>();
    sideA = new TreeMap<>();
    sideB = new TreeMap<>();
  }

  void setAttached(boolean attached) {
    this.attached = attached;
  }

  boolean isAttached() {
    return attached;
  }

  void setExpandAll(boolean expandAll) {
    this.expandAll = expandAll;
  }

  boolean isExpandAll() {
    return expandAll;
  }

  boolean isOpen() {
    return open;
  }

  String getPath() {
    return path;
  }

  Map<String, PublishedBox> getPublished() {
    return published;
  }

  CommentLinkProcessor getCommentLinkProcessor() {
    return commentLinkProcessor;
  }

  void renderDrafts(DisplaySide forSide, JsArray<CommentInfo> in) {
    for (CommentInfo info : Natives.asList(in)) {
      DisplaySide side = displaySide(info, forSide);
      if (side != null) {
        addDraftBox(side, info);
      }
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

  Side getStoredSideFromDisplaySide(DisplaySide side) {
    if (side == DisplaySide.A && (base.isBaseOrAutoMerge() || base.isParent())) {
      return Side.PARENT;
    }
    return Side.REVISION;
  }

  int getParentNumFromDisplaySide(DisplaySide side) {
    if (side == DisplaySide.A) {
      return base.getParentNum();
    }
    return 0;
  }

  PatchSet.Id getPatchSetIdFromSide(DisplaySide side) {
    if (side == DisplaySide.A && (base.isPatchSet() || base.isEdit())) {
      return base.asPatchSetId();
    }
    return revision;
  }

  DisplaySide displaySide(CommentInfo info, DisplaySide forSide) {
    if (info.side() == Side.PARENT) {
      return (base.isBaseOrAutoMerge() || base.isParent()) ? DisplaySide.A : null;
    }
    return forSide;
  }

  static FromTo adjustSelection(CodeMirror cm) {
    FromTo fromTo = cm.getSelectedRange();
    Pos to = fromTo.to();
    if (to.ch() == 0) {
      to.line(to.line() - 1);
      to.ch(cm.getLine(to.line()).length());
    }
    return fromTo;
  }

  abstract CommentGroup group(DisplaySide side, int cmLinePlusOne);

  /**
   * Create a new {@link DraftBox} at the specified line and focus it.
   *
   * @param side which side the draft will appear on.
   * @param line the line the draft will be at. Lines are 1-based. Line 0 is a special case creating
   *     a file level comment.
   */
  void insertNewDraft(DisplaySide side, int line) {
    if (line == 0) {
      host.skipManager.ensureFirstLineIsVisible();
    }

    CommentGroup group = group(side, line);
    if (0 < group.getBoxCount()) {
      CommentBox last = group.getCommentBox(group.getBoxCount() - 1);
      if (last instanceof DraftBox) {
        ((DraftBox) last).setEdit(true);
      } else {
        ((PublishedBox) last).doReply();
      }
    } else {
      addDraftBox(
              side,
              CommentInfo.create(
                  getPath(),
                  getStoredSideFromDisplaySide(side),
                  getParentNumFromDisplaySide(side),
                  line,
                  null,
                  false))
          .setEdit(true);
    }
  }

  abstract String getTokenSuffixForActiveLine(CodeMirror cm);

  Runnable signInCallback(CodeMirror cm) {
    return () -> {
      String token = host.getToken();
      if (cm.extras().hasActiveLine()) {
        token += "@" + getTokenSuffixForActiveLine(cm);
      }
      Gerrit.doSignIn(token);
    };
  }

  abstract void newDraft(CodeMirror cm);

  Runnable newDraftCallback(CodeMirror cm) {
    if (!Gerrit.isSignedIn()) {
      return signInCallback(cm);
    }

    return () -> {
      if (cm.extras().hasActiveLine()) {
        newDraft(cm);
      }
    };
  }

  DraftBox addDraftBox(DisplaySide side, CommentInfo info) {
    int cmLinePlusOne = host.getCmLine(info.line() - 1, side) + 1;
    CommentGroup group = group(side, cmLinePlusOne);
    DraftBox box =
        new DraftBox(
            group,
            getCommentLinkProcessor(),
            project,
            getPatchSetIdFromSide(side),
            info,
            isExpandAll());

    if (info.inReplyTo() != null) {
      PublishedBox r = getPublished().get(info.inReplyTo());
      if (r != null) {
        r.setReplyBox(box);
      }
    }

    group.add(box);
    box.setAnnotation(
        host.getDiffTable()
            .scrollbar
            .draft(host.getCmFromSide(side), Math.max(0, cmLinePlusOne - 1)));
    return box;
  }

  void setExpandAllComments(boolean b) {
    setExpandAll(b);
    for (CommentGroup g : sideA.values()) {
      g.setOpenAll(b);
    }
    for (CommentGroup g : sideB.values()) {
      g.setOpenAll(b);
    }
  }

  abstract SortedMap<Integer, CommentGroup> getMapForNav(DisplaySide side);

  Runnable commentNav(CodeMirror src, Direction dir) {
    return () -> {
      // Every comment appears in both side maps as a linked pair.
      // It is only necessary to search one side to find a comment
      // on either side of the editor pair.
      SortedMap<Integer, CommentGroup> map = getMapForNav(src.side());
      int line =
          src.extras().hasActiveLine() ? src.getLineNumber(src.extras().activeLine()) + 1 : 0;

      CommentGroup g;
      if (dir == Direction.NEXT) {
        map = map.tailMap(line + 1);
        if (map.isEmpty()) {
          return;
        }
        g = map.get(map.firstKey());
        while (g.getBoxCount() == 0) {
          map = map.tailMap(map.firstKey() + 1);
          if (map.isEmpty()) {
            return;
          }
          g = map.get(map.firstKey());
        }
      } else {
        map = map.headMap(line);
        if (map.isEmpty()) {
          return;
        }
        g = map.get(map.lastKey());
        while (g.getBoxCount() == 0) {
          map = map.headMap(map.lastKey());
          if (map.isEmpty()) {
            return;
          }
          g = map.get(map.lastKey());
        }
      }

      CodeMirror cm = g.getCm();
      double y = cm.heightAtLine(g.getLine() - 1, "local");
      cm.setCursor(Pos.create(g.getLine() - 1));
      cm.scrollToY(y - 0.5 * cm.scrollbarV().getClientHeight());
      cm.focus();
    };
  }

  void clearLine(DisplaySide side, int line, CommentGroup group) {
    SortedMap<Integer, CommentGroup> map = map(side);
    if (map.get(line) == group) {
      map.remove(line);
    }
  }

  void render(CommentsCollections in, boolean expandAll) {
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
    if (expandAll) {
      setExpandAllComments(true);
    }
    for (CommentGroup g : sideA.values()) {
      g.init(host.getDiffTable());
    }
    for (CommentGroup g : sideB.values()) {
      g.init(host.getDiffTable());
      g.handleRedraw();
    }
    setAttached(true);
  }

  void renderPublished(DisplaySide forSide, JsArray<CommentInfo> in) {
    for (CommentInfo info : Natives.asList(in)) {
      DisplaySide side = displaySide(info, forSide);
      if (side != null) {
        int cmLinePlusOne = host.getCmLine(info.line() - 1, side) + 1;
        CommentGroup group = group(side, cmLinePlusOne);
        PublishedBox box =
            new PublishedBox(
                group,
                getCommentLinkProcessor(),
                project,
                getPatchSetIdFromSide(side),
                info,
                side,
                isOpen());
        group.add(box);
        box.setAnnotation(
            host.getDiffTable().scrollbar.comment(host.getCmFromSide(side), cmLinePlusOne - 1));
        getPublished().put(info.id(), box);
      }
    }
  }

  abstract Collection<Integer> getLinesWithCommentGroups();

  private static void checkAndAddSkip(List<SkippedLine> out, SkippedLine s) {
    if (s.getSize() > 1) {
      out.add(s);
    }
  }

  List<SkippedLine> splitSkips(int context, List<SkippedLine> skips) {
    if (sideA.containsKey(0) || sideB.containsKey(0)) {
      // Special case of file comment; cannot skip first line.
      for (SkippedLine skip : skips) {
        if (skip.getStartA() == 0) {
          skip.incrementStart(1);
          break;
        }
      }
    }

    for (int boxLine : getLinesWithCommentGroups()) {
      List<SkippedLine> temp = new ArrayList<>(skips.size() + 2);
      for (SkippedLine skip : skips) {
        int startLine = host.getCmLine(skip.getStartB(), DisplaySide.B);
        int deltaBefore = boxLine - startLine;
        int deltaAfter = startLine + skip.getSize() - boxLine;
        if (deltaBefore < -context || deltaAfter < -context) {
          temp.add(skip); // Size guaranteed to be greater than 1
        } else if (deltaBefore > context && deltaAfter > context) {
          SkippedLine before =
              new SkippedLine(
                  skip.getStartA(), skip.getStartB(), skip.getSize() - deltaAfter - context);
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

  abstract void newDraftOnGutterClick(CodeMirror cm, String gutterClass, int line);

  abstract CommentGroup getCommentGroupOnActiveLine(CodeMirror cm);

  Runnable toggleOpenBox(CodeMirror cm) {
    return () -> {
      CommentGroup group = getCommentGroupOnActiveLine(cm);
      if (group != null) {
        group.openCloseLast();
      }
    };
  }

  Runnable openCloseAll(CodeMirror cm) {
    return () -> {
      CommentGroup group = getCommentGroupOnActiveLine(cm);
      if (group != null) {
        group.openCloseAll();
      }
    };
  }

  SortedMap<Integer, CommentGroup> map(DisplaySide side) {
    return side == DisplaySide.A ? sideA : sideB;
  }
}
