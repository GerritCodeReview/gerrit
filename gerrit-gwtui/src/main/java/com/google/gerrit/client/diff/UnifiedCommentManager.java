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

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.changes.CommentInfo;
import com.google.gerrit.client.diff.UnifiedChunkManager.LineSidePair;
import com.google.gerrit.client.patches.SkippedLine;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.ui.CommentLinkProcessor;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.JsArray;

import net.codemirror.lib.CodeMirror;
import net.codemirror.lib.CodeMirror.LineHandle;
import net.codemirror.lib.Pos;
import net.codemirror.lib.TextMarker.FromTo;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

/** Tracks comment widgets for {@link Unified}. */
class UnifiedCommentManager extends CommentManager {
  private final Unified host;
  private final SortedMap<Integer, UnifiedCommentGroup> sideA;
  private final SortedMap<Integer, UnifiedCommentGroup> sideB;

  UnifiedCommentManager(Unified host,
      PatchSet.Id base, PatchSet.Id revision,
      String path,
      CommentLinkProcessor clp,
      boolean open) {
    super(base, revision, path, clp, open);

    this.host = host;
    sideA = new TreeMap<>();
    sideB = new TreeMap<>();
  }

  @Override
  Unified getDiffScreen() {
    return host;
  }

  @Override
  void setExpandAllComments(boolean b) {
    setExpandAll(b);
    for (UnifiedCommentGroup g : sideA.values()) {
      g.setOpenAll(b);
    }
    for (UnifiedCommentGroup g : sideB.values()) {
      g.setOpenAll(b);
    }
  }

  @Override
  Runnable commentNav(final CodeMirror src, final Direction dir) {
    return new Runnable() {
      @Override
      public void run() {
        SortedMap<Integer, UnifiedCommentGroup> map = map(src.side());
        int line = src.extras().hasActiveLine()
            ? src.getLineNumber(src.extras().activeLine()) + 1
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

        UnifiedCommentGroup g = map.get(line);
        CodeMirror cm = g.getCm();
        double y = cm.heightAtLine(g.getLine() - 1, "local");
        cm.setCursor(Pos.create(g.getLine() - 1));
        cm.scrollToY(y - 0.5 * cm.scrollbarV().getClientHeight());
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
    for (CommentGroup g : sideA.values()) {
      g.init(host.getDiffTable());
    }
    for (CommentGroup g : sideB.values()) {
      g.init(host.getDiffTable());
      g.handleRedraw();
    }
    setAttached(true);
  }

  @Override
  void renderPublished(DisplaySide forSide, JsArray<CommentInfo> in) {
    for (CommentInfo info : Natives.asList(in)) {
      DisplaySide side = displaySide(info, forSide);
      if (side != null) {
        int cmLinePlusOne = host.getCmLine(info.line() - 1, side);
        UnifiedCommentGroup group = group(side, cmLinePlusOne);
        PublishedBox box = new PublishedBox(
            group,
            getCommentLinkProcessor(),
            getPatchSetIdFromSide(side),
            info,
            isOpen());
        group.add(box);
        box.setAnnotation(getDiffScreen().getDiffTable().scrollbar.comment(
            host.getCm(),
            cmLinePlusOne));
        getPublished().put(info.id(), box);
      }
    }
  }

  @Override
  void newDraftOnGutterClick(CodeMirror cm, String gutterClass, int cmLinePlusOne) {
    DisplaySide side = gutterClass.equals(UnifiedTable.style.lineNumbersLeft())
        ? DisplaySide.A
        : DisplaySide.B;
    if (cm.somethingSelected()) {
      FromTo fromTo = cm.getSelectedRange();
      Pos end = fromTo.to();
      if (end.ch() == 0) {
        end.line(end.line() - 1);
        end.ch(cm.getLine(end.line()).length());
      }

      LineSidePair pair = host.getLineSidePairFromCmLine(cmLinePlusOne - 1);
      int line = pair.getLine();
      if (pair.getSide() != side) {
        line = host.lineOnOther(pair.getSide(), line).getLine();
      }

      addDraftBox(side, CommentInfo.create(
              getPath(),
              getStoredSideFromDisplaySide(side),
              line + 1,
              CommentRange.create(fromTo))).setEdit(true);
      cm.setSelection(cm.getCursor());
    } else {
      insertNewDraft(side, cmLinePlusOne);
    }
  }

  /**
   * Create a new {@link DraftBox} at the specified line and focus it.
   *
   * @param side which side the draft will appear on.
   * @param cmLinePlusOne the line the draft will be at, plus one.
   *        Lines are 1-based. Line 0 is a special case creating a file level comment.
   */
  @Override
  void insertNewDraft(DisplaySide side, int cmLinePlusOne) {
    if (cmLinePlusOne == 0) {
      getDiffScreen().getSkipManager().ensureFirstLineIsVisible();
    }

    CommentGroup group = group(side, cmLinePlusOne);
    if (0 < group.getBoxCount()) {
      CommentBox last = group.getCommentBox(group.getBoxCount() - 1);
      if (last instanceof DraftBox) {
        ((DraftBox)last).setEdit(true);
      } else {
        ((PublishedBox)last).doReply();
      }
    } else {
      LineSidePair pair = host.getLineSidePairFromCmLine(cmLinePlusOne - 1);
      int line = pair.getLine();
      if (pair.getSide() != side) {
        line = host.lineOnOther(pair.getSide(), line).getLine();
      }
      addDraftBox(side, CommentInfo.create(
          getPath(),
          getStoredSideFromDisplaySide(side),
          line + 1,
          null)).setEdit(true);
    }
  }

  @Override
  DraftBox addDraftBox(DisplaySide side, CommentInfo info) {
    int cmLinePlusOne = host.getCmLine(info.line() - 1, side) + 1;
    UnifiedCommentGroup group = group(side, cmLinePlusOne);
    DraftBox box = new DraftBox(
        group,
        getCommentLinkProcessor(),
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
    box.setAnnotation(getDiffScreen().getDiffTable().scrollbar.draft(
        host.getCm(),
        cmLinePlusOne));
    return box;
  }

  @Override
  List<SkippedLine> splitSkips(int context, List<SkippedLine> skips) {
    if (sideA.containsKey(0) || sideB.containsKey(0)) {
      // Special case of file comment; cannot skip first line.
      for (SkippedLine skip : skips) {
        if (skip.getStartA() == 0) {
          skip.incrementStart(1);
        }
      }
    }

    TreeSet<Integer> allBoxLines = new TreeSet<>(sideA.tailMap(1).keySet());
    allBoxLines.addAll(sideB.tailMap(1).keySet());
    for (int boxLine : allBoxLines) {
      List<SkippedLine> temp = new ArrayList<>(skips.size() + 2);
      for (SkippedLine skip : skips) {
        int startLine = host.getCmLine(skip.getStartA(), DisplaySide.A);
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

  @Override
  void clearLine(DisplaySide side, int cmLinePlusOne, CommentGroup group) {
    SortedMap<Integer, UnifiedCommentGroup> map = map(side);
    if (map.get(cmLinePlusOne) == group) {
      map.remove(cmLinePlusOne);
    }
  }

  @Override
  Runnable toggleOpenBox(final CodeMirror cm) {
    return new Runnable() {
      @Override
      public void run() {
        if (cm.extras().hasActiveLine()) {
          UnifiedCommentGroup w = map(cm.side()).get(
              cm.getLineNumber(cm.extras().activeLine()) + 1);
          if (w != null) {
            w.openCloseLast();
          }
        }
      }
    };
  }

  @Override
  Runnable openCloseAll(final CodeMirror cm) {
    return new Runnable() {
      @Override
      public void run() {
        if (cm.extras().hasActiveLine()) {
          CommentGroup w = map(cm.side()).get(
              cm.getLineNumber(cm.extras().activeLine()) + 1);
          if (w != null) {
            w.openCloseAll();
          }
        }
      }
    };
  }

  @Override
  Runnable newDraftCallback(final CodeMirror cm) {
    if (!Gerrit.isSignedIn()) {
      return new Runnable() {
        @Override
        public void run() {
          String token = host.getToken();
          if (cm.extras().hasActiveLine()) {
            LineHandle handle = cm.extras().activeLine();
            int line = cm.getLineNumber(handle) + 1;
            token += "@" + line;
          }
          Gerrit.doSignIn(token);
        }
     };
    }

    return new Runnable() {
      @Override
      public void run() {
        if (cm.extras().hasActiveLine()) {
          newDraft(cm);
        }
      }
    };
  }

  private void newDraft(CodeMirror cm) {
    int cmLine = cm.getLineNumber(cm.extras().activeLine());
    LineSidePair pair = host.getLineSidePairFromCmLine(cmLine);
    DisplaySide side = pair.getSide();
    if (cm.somethingSelected()) {
      // TODO: Handle range comment
    } else {
      insertNewDraft(side, cmLine);
    }
  }

  private UnifiedCommentGroup group(DisplaySide side, int cmLinePlusOne) {
    UnifiedCommentGroup w = map(side).get(cmLinePlusOne);
    if (w != null) {
      return w;
    }

    UnifiedCommentGroup g = new UnifiedCommentGroup(this, host.getCm(), side, cmLinePlusOne);
    if (side == DisplaySide.A) {
      sideA.put(cmLinePlusOne, g);
    } else {
      sideB.put(cmLinePlusOne, g);
    }

    if (isAttached()) {
      g.init(getDiffScreen().getDiffTable());
      g.handleRedraw();
    }

    return g;
  }

  private SortedMap<Integer, UnifiedCommentGroup> map(DisplaySide side) {
    return side == DisplaySide.A ? sideA : sideB;
  }
}
