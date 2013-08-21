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

import com.google.gerrit.client.changes.CommentInfo;
import com.google.gerrit.client.patches.SkippedLine;
import com.google.gerrit.client.rpc.CallbackGroup;
import com.google.gerrit.client.ui.CommentLinkProcessor;
import com.google.gerrit.common.changes.Side;
import com.google.gerrit.reviewdb.client.PatchSet;

import net.codemirror.lib.CodeMirror;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

abstract class CommentManager {

  private final DiffScreen host;
  private final PatchSet.Id base;
  private final PatchSet.Id revision;
  private final String path;
  private final CommentLinkProcessor commentLinkProcessor;

  final Map<String, PublishedBox> published;
  boolean attached;
  boolean expandAll;
  final Set<DraftBox> unsavedDrafts;

  CommentManager(DiffScreen host,
      PatchSet.Id base, PatchSet.Id revision,
      String path,
      CommentLinkProcessor clp) {
    this.host = host;
    this.base = base;
    this.revision = revision;
    this.path = path;
    this.commentLinkProcessor = clp;

    published = new HashMap<>();
    unsavedDrafts = new HashSet<>();
  }

  abstract void insertNewDraft(DisplaySide side, int line);

  abstract Runnable insertNewDraft(final CodeMirror cm);

  abstract DraftBox addDraftBox(DisplaySide side, CommentInfo info);

  abstract void setExpandAllComments(boolean b);

  abstract Runnable commentNav(CodeMirror src, Direction dir);

  abstract void clearLine(DisplaySide side, int line, CommentGroup group);

  abstract List<SkippedLine> splitSkips(int context, List<SkippedLine> skips);

  DiffScreen getDiffScreen() {
    return host;
  }

  PatchSet.Id getBase() {
    return base;
  }

  PatchSet.Id getRevision() {
    return revision;
  }

  String getPath() {
    return path;
  }

  CommentLinkProcessor getCommentLinkProcessor() {
    return commentLinkProcessor;
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
    return side == DisplaySide.A && base == null ? Side.PARENT : Side.REVISION;
  }

  PatchSet.Id getPatchSetIdFromSide(DisplaySide side) {
    return side == DisplaySide.A && base != null ? base : revision;
  }

  DisplaySide displaySide(CommentInfo info, DisplaySide forSide) {
    if (info.side() == Side.PARENT) {
      return base == null ? DisplaySide.A : null;
    }
    return forSide;
  }
}
