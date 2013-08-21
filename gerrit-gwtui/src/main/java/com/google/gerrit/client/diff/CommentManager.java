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
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.ui.CommentLinkProcessor;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.JsArray;

import net.codemirror.lib.CodeMirror;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Tracks comment widgets for {@link DiffScreen}. */
abstract class CommentManager {
  private final PatchSet.Id base;
  private final PatchSet.Id revision;
  private final String path;
  private final CommentLinkProcessor commentLinkProcessor;

  private final Map<String, PublishedBox> published;
  private final Set<DraftBox> unsavedDrafts;
  private boolean attached;
  private boolean expandAll;
  private boolean open;

  CommentManager(
      PatchSet.Id base,
      PatchSet.Id revision,
      String path,
      CommentLinkProcessor clp,
      boolean open) {
    this.base = base;
    this.revision = revision;
    this.path = path;
    this.commentLinkProcessor = clp;
    this.open = open;

    published = new HashMap<>();
    unsavedDrafts = new HashSet<>();
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

  abstract void insertNewDraft(DisplaySide side, int line);

  abstract Runnable newDraftCallback(final CodeMirror cm);

  abstract DraftBox addDraftBox(DisplaySide side, CommentInfo info);

  abstract void setExpandAllComments(boolean b);

  abstract Runnable commentNav(CodeMirror src, Direction dir);

  abstract void clearLine(DisplaySide side, int line, CommentGroup group);

  abstract void renderPublished(DisplaySide forSide, JsArray<CommentInfo> in);

  abstract List<SkippedLine> splitSkips(int context, List<SkippedLine> skips);

  abstract void newDraftOnGutterClick(CodeMirror cm, String gutterClass, int line);

  abstract Runnable toggleOpenBox(final CodeMirror cm);

  abstract Runnable openCloseAll(final CodeMirror cm);

  abstract DiffScreen getDiffScreen();
}
