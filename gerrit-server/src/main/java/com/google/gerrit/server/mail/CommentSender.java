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

import com.google.common.collect.Ordering;
import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.reviewdb.client.AccountProjectWatch.NotifyType;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.server.PatchLineCommentAccess;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.change.PostReview.NotifyHandling;
import com.google.gerrit.server.patch.PatchFile;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Send comments, after the author of them hit used Publish Comments in the UI. */
public class CommentSender extends ReplyToChangeSender {
  private static final Logger log = LoggerFactory
      .getLogger(CommentSender.class);

  public static interface Factory {
    public CommentSender create(NotifyHandling notify, Change change);
  }

  private Provider<ReviewDb> db;
  private final NotifyHandling notify;
  private List<PatchLineComment> inlineComments = Collections.emptyList();

  @Inject
  public CommentSender(EmailArguments ea,
      Provider<ReviewDb> db,
      @Assisted NotifyHandling notify,
      @Assisted Change c) {
    super(ea, c, "comment");
    this.db = db;
    this.notify = notify;
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
    changeData.setCurrentFilePaths(Ordering.natural().sortedCopy(paths));
  }

  @Override
  protected void init() throws EmailException {
    super.init();

    if (notify.compareTo(NotifyHandling.OWNER_REVIEWERS) >= 0) {
      ccAllApprovals();
    }
    if (notify.compareTo(NotifyHandling.ALL) >= 0) {
      bccStarredBy();
      includeWatchers(NotifyType.ALL_COMMENTS);
    }
  }

  @Override
  public void formatChange() throws EmailException {
    appendText(velocifyFile("Comment.vm"));
  }

  @Override
  public void formatFooter() throws EmailException {
    appendText(velocifyFile("CommentFooter.vm"));
  }

  public boolean hasInlineComments() {
    return !inlineComments.isEmpty();
  }

  public String getInlineComments() {
    return getInlineComments(1);
  }

  // Makes a link back to the given patch set and file.
  private String makeChangeLink(Patch.Key patch) {
    final StringBuilder sb = new StringBuilder();
    sb.append(getGerritUrl());
    sb.append("#/c/");
    sb.append(patch.getParentKey().getParentKey().get());
    sb.append("/");
    sb.append(patch.getParentKey().get());
    sb.append("/");
    sb.append(patch.getFileName());
    return sb.toString();
  }

  public String getInlineComments(int lines) {
    StringBuilder cmts = new StringBuilder();
    final Repository repo = getRepository();
    try {
      PatchList patchList = null;
      if (repo != null) {
        try {
          patchList = getPatchList();
        } catch (PatchListNotAvailableException e) {
          patchList = null;
        }
      }

      Patch.Key currentFileKey = null;
      PatchFile currentFileData = null;
      for (final PatchLineComment c : inlineComments) {
        final Patch.Key pk = c.getKey().getParentKey();

        if (!pk.equals(currentFileKey)) {
          if (Patch.COMMIT_MSG.equals(pk.get())) {
            cmts.append("Commit Message\n");
          } else {
            cmts.append(makeChangeLink(pk));
            cmts.append("\n");
            cmts.append("File ");
            cmts.append(pk.getFileName());
            cmts.append(":\n\n");
          }
          currentFileKey = pk;

          if (patchList != null) {
            try {
              currentFileData = new PatchFile(repo, patchList, pk.get());
            } catch (IOException e) {
              log.warn(String.format(
                  "Cannot load %s from %s in %s",
                  pk.getFileName(),
                  patchList.getNewId().name(),
                  projectState.getProject().getName()), e);
              currentFileData = null;
            }
          }
        }

        if (currentFileData != null) {
          appendComment(cmts, lines, currentFileData, c);
        }
        cmts.append("\n\n");
      }
    } finally {
      if (repo != null) {
        repo.close();
      }
    }
    return cmts.toString();
  }

  private void appendComment(StringBuilder out, int contextLines,
      PatchFile currentFileData, PatchLineComment comment) {
    int lineNbr = comment.getLine();
    short side = comment.getSide();
    int maxLines;
    try {
      maxLines = currentFileData.getLineCount(side);
    } catch (Throwable e) {
      maxLines = lineNbr;
    }

    final int startLine = Math.max(1, lineNbr - contextLines + 1);
    final int stopLine = Math.min(maxLines, lineNbr + contextLines);

    for (int line = startLine; line <= lineNbr; ++line) {
      appendFileLine(out, currentFileData, side, line);
    }

    appendParentSnippet(out, comment, db.get().patchComments());
    out.append(comment.getMessage().trim()).append('\n');

    for (int line = lineNbr + 1; line < stopLine; ++line) {
      appendFileLine(out, currentFileData, side, line);
    }
  }

  private void appendFileLine(StringBuilder cmts, PatchFile fileData, short side, int line) {
    cmts.append("Line " + line);
    try {
      final String lineStr = fileData.getLine(side, line);
      cmts.append(": ");
      cmts.append(lineStr);
    } catch (Throwable e) {
      // Don't quote the line if we can't safely convert it.
    }
    cmts.append("\n");
  }

  private void appendParentSnippet(StringBuilder cmts, PatchLineComment childComment,
      PatchLineCommentAccess commentAccess) {
    PatchLineComment.Key parentKey = new PatchLineComment.Key(
        childComment.getKey().getParentKey(), childComment.getParentUuid());
    PatchLineComment parentComment = null;
    try {
      parentComment = commentAccess.get(parentKey);
    } catch (OrmException e) {
      // ignore, just don't show comment
    }

    if (parentComment != null) {
      String parentCommentStr = parentComment.getMessage().trim();
      int endIndex = Math.min(75, parentCommentStr.length());
      if (parentCommentStr.contains("\n")) {
        endIndex = Math.min(endIndex, parentCommentStr.indexOf("\n"));
      }
      cmts.append("> ").append(parentCommentStr.substring(0, endIndex)).append('\n');
    }
  }

  private Repository getRepository() {
    try {
      return args.server.openRepository(projectState.getProject().getNameKey());
    } catch (IOException e) {
      return null;
    }
  }
}
