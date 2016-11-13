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
import com.google.gerrit.client.diff.LineMapper.LineOnOtherInfo;
import com.google.gerrit.client.diff.UnifiedChunkManager.LineRegionInfo;
import com.google.gerrit.client.diff.UnifiedChunkManager.RegionType;
import com.google.gerrit.client.ui.CommentLinkProcessor;
import com.google.gerrit.reviewdb.client.PatchSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import net.codemirror.lib.CodeMirror;
import net.codemirror.lib.Pos;
import net.codemirror.lib.TextMarker.FromTo;

/** Tracks comment widgets for {@link Unified}. */
class UnifiedCommentManager extends CommentManager {

  private final SortedMap<Integer, CommentGroup> mergedMap;

  // In Unified, a CodeMirror line can have up to two CommentGroups - one for
  // the base side and one for the revision, so we need to keep track of the
  // duplicates and replace the entries in mergedMap on draft removal.
  private final Map<Integer, CommentGroup> duplicates;

  UnifiedCommentManager(
      Unified host,
      DiffObject base,
      PatchSet.Id revision,
      String path,
      CommentLinkProcessor clp,
      boolean open) {
    super(host, base, revision, path, clp, open);
    mergedMap = new TreeMap<>();
    duplicates = new HashMap<>();
  }

  @Override
  SortedMap<Integer, CommentGroup> getMapForNav(DisplaySide side) {
    return mergedMap;
  }

  @Override
  void clearLine(DisplaySide side, int line, CommentGroup group) {
    super.clearLine(side, line, group);

    if (mergedMap.get(line) == group) {
      mergedMap.remove(line);
      if (duplicates.containsKey(line)) {
        mergedMap.put(line, duplicates.remove(line));
      }
    }
  }

  @Override
  void newDraftOnGutterClick(CodeMirror cm, String gutterClass, int cmLinePlusOne) {
    if (!Gerrit.isSignedIn()) {
      signInCallback(cm).run();
    } else {
      LineRegionInfo info = ((Unified) host).getLineRegionInfoFromCmLine(cmLinePlusOne - 1);
      DisplaySide side =
          gutterClass.equals(UnifiedTable.style.lineNumbersLeft()) ? DisplaySide.A : DisplaySide.B;
      int line = info.line;
      if (info.getSide() != side) {
        line = host.lineOnOther(info.getSide(), line).getLine();
      }
      insertNewDraft(side, line + 1);
    }
  }

  @Override
  CommentGroup getCommentGroupOnActiveLine(CodeMirror cm) {
    CommentGroup group = null;
    if (cm.extras().hasActiveLine()) {
      int cmLinePlusOne = cm.getLineNumber(cm.extras().activeLine()) + 1;
      LineRegionInfo info = ((Unified) host).getLineRegionInfoFromCmLine(cmLinePlusOne - 1);
      CommentGroup forSide = map(info.getSide()).get(cmLinePlusOne);
      group = forSide == null ? map(info.getSide().otherSide()).get(cmLinePlusOne) : forSide;
    }
    return group;
  }

  @Override
  Collection<Integer> getLinesWithCommentGroups() {
    return mergedMap.tailMap(1).keySet();
  }

  @Override
  String getTokenSuffixForActiveLine(CodeMirror cm) {
    int cmLinePlusOne = cm.getLineNumber(cm.extras().activeLine()) + 1;
    LineRegionInfo info = ((Unified) host).getLineRegionInfoFromCmLine(cmLinePlusOne - 1);
    return (info.getSide() == DisplaySide.A ? "a" : "") + cmLinePlusOne;
  }

  @Override
  void newDraft(CodeMirror cm) {
    if (cm.somethingSelected()) {
      FromTo fromTo = adjustSelection(cm);
      Pos from = fromTo.from();
      Pos to = fromTo.to();
      Unified unified = (Unified) host;
      UnifiedChunkManager manager = unified.getChunkManager();
      LineRegionInfo fromInfo = unified.getLineRegionInfoFromCmLine(from.line());
      LineRegionInfo toInfo = unified.getLineRegionInfoFromCmLine(to.line());
      DisplaySide side = toInfo.getSide();

      // Handle special cases in selections that span multiple regions. Force
      // start line to be on the same side as the end line.
      if ((fromInfo.type == RegionType.INSERT || fromInfo.type == RegionType.COMMON)
          && toInfo.type == RegionType.DELETE) {
        LineOnOtherInfo infoOnSideA = manager.lineMapper.lineOnOther(DisplaySide.B, fromInfo.line);
        int startLineOnSideA = infoOnSideA.getLine();
        if (infoOnSideA.isAligned()) {
          from.line(startLineOnSideA);
        } else {
          from.line(startLineOnSideA + 1);
        }
        from.ch(0);
        to.line(toInfo.line);
      } else if (fromInfo.type == RegionType.DELETE && toInfo.type == RegionType.INSERT) {
        LineOnOtherInfo infoOnSideB = manager.lineMapper.lineOnOther(DisplaySide.A, fromInfo.line);
        int startLineOnSideB = infoOnSideB.getLine();
        if (infoOnSideB.isAligned()) {
          from.line(startLineOnSideB);
        } else {
          from.line(startLineOnSideB + 1);
        }
        from.ch(0);
        to.line(toInfo.line);
      } else if (fromInfo.type == RegionType.DELETE && toInfo.type == RegionType.COMMON) {
        int toLineOnSideA = manager.lineMapper.lineOnOther(DisplaySide.B, toInfo.line).getLine();
        from.line(fromInfo.line);
        // Force the end line to be on the same side as the start line.
        to.line(toLineOnSideA);
        side = DisplaySide.A;
      } else { // Common case
        from.line(fromInfo.line);
        to.line(toInfo.line);
      }

      addDraftBox(
              side,
              CommentInfo.create(
                  getPath(),
                  getStoredSideFromDisplaySide(side),
                  to.line() + 1,
                  CommentRange.create(fromTo)))
          .setEdit(true);
      cm.setCursor(Pos.create(host.getCmLine(to.line(), side), to.ch()));
      cm.setSelection(cm.getCursor());
    } else {
      int cmLine = cm.getLineNumber(cm.extras().activeLine());
      LineRegionInfo info = ((Unified) host).getLineRegionInfoFromCmLine(cmLine);
      insertNewDraft(info.getSide(), cmLine + 1);
    }
  }

  @Override
  CommentGroup group(DisplaySide side, int cmLinePlusOne) {
    Map<Integer, CommentGroup> map = map(side);
    CommentGroup existing = map.get(cmLinePlusOne);
    if (existing != null) {
      return existing;
    }

    UnifiedCommentGroup g =
        new UnifiedCommentGroup(this, host.getCmFromSide(side), side, cmLinePlusOne);
    map.put(cmLinePlusOne, g);
    if (mergedMap.containsKey(cmLinePlusOne)) {
      duplicates.put(cmLinePlusOne, mergedMap.remove(cmLinePlusOne));
    }
    mergedMap.put(cmLinePlusOne, g);

    if (isAttached()) {
      g.init(host.getDiffTable());
      g.handleRedraw();
    }

    return g;
  }
}
