// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.mail;

import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.Patch;
import com.google.gerrit.client.reviewdb.PatchLineComment;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.git.InvalidRepositoryException;
import com.google.gerrit.server.GerritServer;
import com.google.gerrit.server.patch.PatchFile;
import com.google.gwtorm.client.OrmException;

import org.spearce.jgit.lib.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.mail.MessagingException;

/** Send comments, after the author of them hit used Publish Comments in the UI. */
public class CommentSender extends ReplyToChangeSender {
  private List<PatchLineComment> inlineComments = Collections.emptyList();

  public CommentSender(GerritServer gs, Change c) {
    super(gs, c, "comment");
  }

  public void setPatchLineComments(final List<PatchLineComment> plc) {
    inlineComments = plc;
  }

  @Override
  protected void init() throws MessagingException {
    super.init();

    ccAllApprovals();
    bccStarredBy();
    bccWatchesNotifyAllComments();
  }

  @Override
  protected void format() {
    if (!"".equals(getCoverLetter()) || !inlineComments.isEmpty()) {
      appendText("Comments on Patch Set " + patchSet.getPatchSetId() + ":\n");
      appendText("\n");
      formatCoverLetter();
      formatInlineComments();
      if (getChangeUrl() != null) {
        appendText("To respond, visit " + getChangeUrl() + "\n");
        appendText("\n");
      }
    }
  }

  private void formatInlineComments() {
    final Map<Patch.Key, Patch> patches = getPatchMap();
    final Repository repo = getRepository();

    Patch.Key currentFileKey = null;
    PatchFile currentFileData = null;
    for (final PatchLineComment c : inlineComments) {
      final Patch.Key pk = c.getKey().getParentKey();
      final int lineNbr = c.getLine();
      final short side = c.getSide();

      if (!pk.equals(currentFileKey)) {
        appendText("....................................................\n");
        appendText("File ");
        appendText(pk.get());
        appendText("\n");
        currentFileKey = pk;

        final Patch p = patches.get(pk);
        if (p != null && repo != null) {
          try {
            currentFileData = new PatchFile(repo, patchSet.getRevision(), p);
          } catch (Throwable e) {
            // Don't quote the line if we can't load it.
          }
        } else {
          currentFileData = null;
        }
      }

      appendText("Line " + lineNbr);
      if (currentFileData != null) {
        try {
          final String lineStr = currentFileData.getLine(side, lineNbr);
          appendText(": ");
          appendText(lineStr);
        } catch (Throwable cce) {
          // Don't quote the line if we can't safely convert it.
        }
      }
      appendText("\n");

      appendText(c.getMessage().trim());
      appendText("\n\n");
    }
  }

  private Map<Patch.Key, Patch> getPatchMap() {
    try {
      final PatchSet.Id psId = patchSet.getId();
      return db.patches().toMap(db.patches().byPatchSet(psId));
    } catch (OrmException e) {
      // Can't read the patch table? Don't quote file lines.
      //
      return Collections.emptyMap();
    }
  }

  private Repository getRepository() {
    try {
      return server.getRepositoryCache().get(projectName);
    } catch (InvalidRepositoryException e) {
      return null;
    }
  }
}
