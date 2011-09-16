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

import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.Patch;
import com.google.gerrit.reviewdb.PatchLineComment;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.patch.PatchFile;
import com.google.gerrit.server.patch.PatchList;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Send comments, after the author of them hit used Publish Comments in the UI. */
public class CommentSender extends ReplyToChangeSender {
  public static interface Factory {
    public CommentSender create(Change change);
  }

  private List<PatchLineComment> inlineComments = Collections.emptyList();

  @Inject
  public CommentSender(EmailArguments ea,
      @AnonymousCowardName String anonymousCowardName, @Assisted Change c) {
    super(ea, anonymousCowardName, c, "comment");
  }

  public void setPatchLineComments(final List<PatchLineComment> plc) {
    inlineComments = plc;

    Set<String> paths = new HashSet<String>();
    for (PatchLineComment c : plc) {
      Patch.Key p = c.getKey().getParentKey();
      if (!Patch.COMMIT_MSG.equals(p.getFileName())) {
        paths.add(p.getFileName());
      }
    }
    String[] names = paths.toArray(new String[paths.size()]);
    Arrays.sort(names);
    changeData.setCurrentFilePaths(names);
  }

  @Override
  protected void init() throws EmailException {
    super.init();

    ccAllApprovals();
    bccStarredBy();
    bccWatchesNotifyAllComments();
  }

  @Override
  public void formatChange() throws EmailException {
    appendText(velocifyFile("Comment.vm"));
  }

  public String getInlineComments() {
    StringBuilder  cmts = new StringBuilder();

    final Repository repo = getRepository();
    try {
      final PatchList patchList = repo != null ? getPatchList() : null;

      Patch.Key currentFileKey = null;
      PatchFile currentFileData = null;
      for (final PatchLineComment c : inlineComments) {
        final Patch.Key pk = c.getKey().getParentKey();
        final int lineNbr = c.getLine();
        final short side = c.getSide();

        if (!pk.equals(currentFileKey)) {
          cmts.append("....................................................\n");
          if (Patch.COMMIT_MSG.equals(pk.get())) {
            cmts.append("Commit Message\n");
          } else {
            cmts.append("File ");
            cmts.append(pk.get());
            cmts.append("\n");
          }
          currentFileKey = pk;

          if (patchList != null) {
            try {
              currentFileData =
                  new PatchFile(repo, patchList, pk.getFileName());
            } catch (IOException e) {
              // Don't quote the line if we can't load it.
            }
          } else {
            currentFileData = null;
          }
        }

        cmts.append("Line " + lineNbr);
        if (currentFileData != null) {
          try {
            final String lineStr = currentFileData.getLine(side, lineNbr);
            cmts.append(": ");
            cmts.append(lineStr);
          } catch (Throwable cce) {
            // Don't quote the line if we can't safely convert it.
          }
        }
        cmts.append("\n");

        cmts.append(c.getMessage().trim());
        cmts.append("\n\n");
      }
    } finally {
      if (repo != null) {
        repo.close();
      }
    }
    return cmts.toString();
  }

  private Repository getRepository() {
    try {
      return args.server.openRepository(projectState.getProject().getNameKey());
    } catch (RepositoryNotFoundException e) {
      return null;
    }
  }
}
