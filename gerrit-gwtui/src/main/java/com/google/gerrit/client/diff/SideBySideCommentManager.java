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

import com.google.gerrit.client.DiffObject;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.changes.CommentInfo;
import com.google.gerrit.client.ui.CommentLinkProcessor;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import java.util.Collection;
import java.util.Map;
import java.util.SortedMap;
import net.codemirror.lib.CodeMirror;
import net.codemirror.lib.TextMarker.FromTo;

/** Tracks comment widgets for {@link SideBySide}. */
class SideBySideCommentManager extends CommentManager {
  SideBySideCommentManager(
      SideBySide host,
      @Nullable Project.NameKey project,
      DiffObject base,
      PatchSet.Id revision,
      String path,
      CommentLinkProcessor clp,
      boolean open) {
    super(host, project, base, revision, path, clp, open);
  }

  @Override
  SortedMap<Integer, CommentGroup> getMapForNav(DisplaySide side) {
    return map(side);
  }

  @Override
  void clearLine(DisplaySide side, int line, CommentGroup group) {
    super.clearLine(side, line, group);
  }

  @Override
  void newDraftOnGutterClick(CodeMirror cm, String gutterClass, int line) {
    if (!Gerrit.isSignedIn()) {
      signInCallback(cm).run();
    } else {
      insertNewDraft(cm.side(), line);
    }
  }

  @Override
  CommentGroup getCommentGroupOnActiveLine(CodeMirror cm) {
    CommentGroup group = null;
    if (cm.extras().hasActiveLine()) {
      group = map(cm.side()).get(cm.getLineNumber(cm.extras().activeLine()) + 1);
    }
    return group;
  }

  @Override
  Collection<Integer> getLinesWithCommentGroups() {
    return sideB.tailMap(1).keySet();
  }

  @Override
  String getTokenSuffixForActiveLine(CodeMirror cm) {
    return (cm.side() == DisplaySide.A ? "a" : "")
        + (cm.getLineNumber(cm.extras().activeLine()) + 1);
  }

  @Override
  void newDraft(CodeMirror cm) {
    int line = cm.getLineNumber(cm.extras().activeLine()) + 1;
    if (cm.somethingSelected()) {
      FromTo fromTo = adjustSelection(cm);
      addDraftBox(
              cm.side(),
              CommentInfo.create(
                  getPath(),
                  getStoredSideFromDisplaySide(cm.side()),
                  getParentNumFromDisplaySide(cm.side()),
                  line,
                  CommentRange.create(fromTo),
                  false))
          .setEdit(true);
      cm.setCursor(fromTo.to());
      cm.setSelection(cm.getCursor());
    } else {
      insertNewDraft(cm.side(), line);
    }
  }

  @Override
  CommentGroup group(DisplaySide side, int line) {
    CommentGroup existing = map(side).get(line);
    if (existing != null) {
      return existing;
    }

    SideBySideCommentGroup newGroup = newGroup(side, line);
    Map<Integer, CommentGroup> map = side == DisplaySide.A ? sideA : sideB;
    Map<Integer, CommentGroup> otherMap = side == DisplaySide.A ? sideB : sideA;
    map.put(line, newGroup);
    int otherLine = host.lineOnOther(side, line - 1).getLine() + 1;
    existing = map(side.otherSide()).get(otherLine);
    CommentGroup otherGroup;
    if (existing != null) {
      otherGroup = existing;
    } else {
      otherGroup = newGroup(side.otherSide(), otherLine);
      otherMap.put(otherLine, otherGroup);
    }
    SideBySideCommentGroup.pair(newGroup, (SideBySideCommentGroup) otherGroup);

    if (isAttached()) {
      newGroup.init(host.getDiffTable());
      otherGroup.handleRedraw();
    }
    return newGroup;
  }

  private SideBySideCommentGroup newGroup(DisplaySide side, int line) {
    return new SideBySideCommentGroup(this, host.getCmFromSide(side), side, line);
  }
}
