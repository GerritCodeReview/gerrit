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
import com.google.gerrit.client.patches.SkippedLine;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.ui.CommentLinkProcessor;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.JsArray;

import net.codemirror.lib.CodeMirror;
import net.codemirror.lib.CodeMirror.LineHandle;
import net.codemirror.lib.LineCharacter;
import net.codemirror.lib.TextMarker.FromTo;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/** Tracks comment widgets for {@link SideBySide2}. */
class SideBySideCommentManager extends CommentManager {
  private final SortedMap<Integer, SideBySideCommentGroup> sideA;
  private final SortedMap<Integer, SideBySideCommentGroup> sideB;

  SideBySideCommentManager(SideBySide2 host,
      PatchSet.Id base, PatchSet.Id revision,
      String path,
      CommentLinkProcessor clp) {
    super(host, base, revision, path, clp);

    sideA = new TreeMap<>();
    sideB = new TreeMap<>();
  }

  void setExpandAllComments(boolean b) {
    expandAll = b;
    for (SideBySideCommentGroup g : sideA.values()) {
      g.setOpenAll(b);
    }
    for (SideBySideCommentGroup g : sideB.values()) {
      g.setOpenAll(b);
    }
  }

  Runnable commentNav(final CodeMirror src, final Direction dir) {
    return new Runnable() {
      @Override
      public void run() {
        // Every comment appears in both side maps as a linked pair.
        // It is only necessary to search one side to find a comment
        // on either side of the editor pair.
        SortedMap<Integer, SideBySideCommentGroup> map = map(src.side());
        int line = src.hasActiveLine()
            ? src.getLineNumber(src.getActiveLine()) + 1
            : 0;
        if (dir == Direction.NEXT) {
          map = map.tailMap(line + 1);
          if (map.isEmpty()) {
            return;
          }
          line = map.firstKey();
        } else {
          map = map.headMap(line);
          if (map.isEmpty()) {
            return;
          }
          line = map.lastKey();
        }

        SideBySideCommentGroup g = map.get(line);
        if (g.getBoxCount() == 0) {
          g = g.getPeer();
        }

        CodeMirror cm = g.getCm();
        double y = cm.heightAtLine(g.getLine() - 1, "local");
        cm.setCursor(LineCharacter.create(g.getLine() - 1));
        cm.scrollToY(y - 0.5 * cm.getScrollbarV().getClientHeight());
        cm.focus();
      }
    };
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
    for (SideBySideCommentGroup g : sideA.values()) {
      g.attachPair(getDiffScreen().getDiffTable());
    }
    for (SideBySideCommentGroup g : sideB.values()) {
      g.attachPair(getDiffScreen().getDiffTable());
      g.handleRedraw();
    }
    attached = true;
  }

  private void renderPublished(DisplaySide forSide, JsArray<CommentInfo> in) {
    for (CommentInfo info : Natives.asList(in)) {
      DisplaySide side = displaySide(info, forSide);
      if (side != null) {
        SideBySideCommentGroup group = group(side, info.line());
        PublishedBox box = new PublishedBox(
            group,
            getCommentLinkProcessor(),
            getPatchSetIdFromSide(side),
            info);
        group.add(box);
        box.setMark(getDiffScreen().getDiffTable().overview.add(
           ((SideBySide2) getDiffScreen()).getCmFromSide(side),
            Math.max(0, info.line() - 1), 1,
            OverviewBar.MarkType.COMMENT));
        published.put(info.id(), box);
      }
    }
  }

  private void renderDrafts(DisplaySide forSide, JsArray<CommentInfo> in) {
    for (CommentInfo info : Natives.asList(in)) {
      DisplaySide side = displaySide(info, forSide);
      if (side != null) {
        addDraftBox(side, info);
      }
    }
  }

  /**
   * Create a new {@link DraftBox} at the specified line and focus it.
   *
   * @param side which side the draft will appear on.
   * @param line the line the draft will be at. Lines are 1-based. Line 0 is a
   *        special case creating a file level comment.
   */
  void insertNewDraft(DisplaySide side, int line) {
    if (line == 0) {
      getDiffScreen().getSkipManager().ensureFirstLineIsVisible();
    }

    SideBySideCommentGroup group = group(side, line);
    if (0 < group.getBoxCount()) {
      CommentBox last = group.getCommentBox(group.getBoxCount() - 1);
      if (last instanceof DraftBox) {
        ((DraftBox)last).setEdit(true);
      } else {
        ((PublishedBox)last).doReply();
      }
    } else {
      addDraftBox(side, CommentInfo.create(
          getPath(),
          getStoredSideFromDisplaySide(side),
          line,
          null)).setEdit(true);
    }
  }

  DraftBox addDraftBox(DisplaySide side, CommentInfo info) {
    SideBySideCommentGroup group = group(side, info.line());
    DraftBox box = new DraftBox(
        group,
        getCommentLinkProcessor(),
        getPatchSetIdFromSide(side),
        info,
        expandAll);

    if (info.in_reply_to() != null) {
      PublishedBox r = published.get(info.in_reply_to());
      if (r != null) {
        r.setReplyBox(box);
      }
    }

    group.add(box);
    box.setMark(getDiffScreen().getDiffTable().overview.add(
        ((SideBySide2) getDiffScreen()).getCmFromSide(side),
        Math.max(0, info.line() - 1), 1,
        OverviewBar.MarkType.DRAFT));
    return box;
  }

  List<SkippedLine> splitSkips(int context, List<SkippedLine> skips) {
    if (sideB.containsKey(0)) {
      // Special case of file comment; cannot skip first line.
      for (SkippedLine skip : skips) {
        if (skip.getStartB() == 0) {
          skip.incrementStart(1);
        }
      }
    }

    // TODO: This is not optimal, but shouldn't be too costly in most cases.
    // Maybe rewrite after done keeping track of diff chunk positions.
    for (int boxLine : sideB.tailMap(1).keySet()) {
      List<SkippedLine> temp = new ArrayList<>(skips.size() + 2);
      for (SkippedLine skip : skips) {
        int startLine = skip.getStartB();
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

  void clearLine(DisplaySide side, int line, CommentGroup group) {
    SortedMap<Integer, SideBySideCommentGroup> map = map(side);
    if (map.get(line) == group) {
      map.remove(line);
    }
  }

  Runnable toggleOpenBox(final CodeMirror cm) {
    return new Runnable() {
      public void run() {
        if (cm.hasActiveLine()) {
          SideBySideCommentGroup w = map(cm.side()).get(
              cm.getLineNumber(cm.getActiveLine()) + 1);
          if (w != null) {
            w.openCloseLast();
          }
        }
      }
    };
  }

  Runnable openCloseAll(final CodeMirror cm) {
    return new Runnable() {
      @Override
      public void run() {
        if (cm.hasActiveLine()) {
          SideBySideCommentGroup w = map(cm.side()).get(
              cm.getLineNumber(cm.getActiveLine()) + 1);
          if (w != null) {
            w.openCloseAll();
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
          String token = getDiffScreen().getToken();
          if (cm.hasActiveLine()) {
            LineHandle handle = cm.getActiveLine();
            int line = cm.getLineNumber(handle) + 1;
            token += "@" + (cm.side() == DisplaySide.A ? "a" : "") + line;
          }
          Gerrit.doSignIn(token);
        }
     };
    }

    return new Runnable() {
      public void run() {
        if (cm.hasActiveLine()) {
          newDraft(cm);
        }
      }
    };
  }

  private void newDraft(CodeMirror cm) {
    int line = cm.getLineNumber(cm.getActiveLine()) + 1;
    if (cm.somethingSelected()) {
      FromTo fromTo = cm.getSelectedRange();
      LineCharacter end = fromTo.getTo();
      if (end.getCh() == 0) {
        end.setLine(end.getLine() - 1);
        end.setCh(cm.getLine(end.getLine()).length());
      }

      addDraftBox(cm.side(), CommentInfo.create(
              getPath(),
              getStoredSideFromDisplaySide(cm.side()),
              line,
              CommentRange.create(fromTo))).setEdit(true);
      cm.setSelection(cm.getCursor());
    } else {
      insertNewDraft(cm.side(), line);
    }
  }

  private SideBySideCommentGroup group(DisplaySide side, int line) {
    SideBySideCommentGroup w = map(side).get(line);
    if (w != null) {
      return w;
    }

    int lineA, lineB;
    if (line == 0) {
      lineA = lineB = 0;
    } else if (side == DisplaySide.A) {
      lineA = line;
      lineB = ((SideBySide2) getDiffScreen()).lineOnOther(side, line - 1).getLine() + 1;
    } else {
      lineA = ((SideBySide2) getDiffScreen()).lineOnOther(side, line - 1).getLine() + 1;
      lineB = line;
    }

    SideBySideCommentGroup a = newGroup(DisplaySide.A, lineA);
    SideBySideCommentGroup b = newGroup(DisplaySide.B, lineB);
    SideBySideCommentGroup.pair(a, b);

    sideA.put(lineA, a);
    sideB.put(lineB, b);

    if (attached) {
      a.attachPair(getDiffScreen().getDiffTable());
      b.handleRedraw();
    }

    return side == DisplaySide.A ? a : b;
  }

  private SideBySideCommentGroup newGroup(DisplaySide side, int line) {
    return new SideBySideCommentGroup(this, ((SideBySide2) getDiffScreen()).getCmFromSide(side), line);
  }

  private SortedMap<Integer, SideBySideCommentGroup> map(DisplaySide side) {
    return side == DisplaySide.A ? sideA : sideB;
  }
}
