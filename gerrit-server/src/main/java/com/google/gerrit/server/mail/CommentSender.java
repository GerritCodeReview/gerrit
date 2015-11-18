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

import static com.google.gerrit.server.PatchLineCommentsUtil.getCommentPsId;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.Ordering;
import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.extensions.api.changes.ReviewInput.NotifyHandling;
import com.google.gerrit.reviewdb.client.AccountProjectWatch.NotifyType;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.CommentRange;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.PatchLineCommentsUtil;
import com.google.gerrit.server.patch.PatchFile;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gwtorm.client.KeyUtil;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
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
    CommentSender create(NotifyHandling notify, Change.Id id);
  }

  private final NotifyHandling notify;
  private List<PatchLineComment> inlineComments = Collections.emptyList();
  private final PatchLineCommentsUtil plcUtil;

  @Inject
  public CommentSender(EmailArguments ea,
      PatchLineCommentsUtil plcUtil,
      @Assisted NotifyHandling notify,
      @Assisted Change.Id id) throws OrmException {
    super(ea, "comment", newChangeData(ea, id));
    this.notify = notify;
    this.plcUtil = plcUtil;
  }

  public void setPatchLineComments(final List<PatchLineComment> plc)
      throws OrmException {
    inlineComments = plc;

    Set<String> paths = new HashSet<>();
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

  public String getInlineComments(int lines) {
    StringBuilder cmts = new StringBuilder();
    try (Repository repo = getRepository()) {
      PatchList patchList = null;
      if (repo != null) {
        try {
          patchList = getPatchList();
        } catch (PatchListNotAvailableException e) {
          log.error("Failed to get patch list", e);
        }
      }

      Patch.Key currentFileKey = null;
      PatchFile currentFileData = null;
      for (final PatchLineComment c : inlineComments) {
        final Patch.Key pk = c.getKey().getParentKey();

        if (!pk.equals(currentFileKey)) {
          String link = makeLink(pk);
          if (link != null) {
            cmts.append(link).append('\n');
          }
          if (Patch.COMMIT_MSG.equals(pk.get())) {
            cmts.append("Commit Message:\n\n");
          } else {
            cmts.append("File ").append(pk.get()).append(":\n\n");
          }
          currentFileKey = pk;

          if (patchList != null) {
            try {
              currentFileData =
                  new PatchFile(repo, patchList, pk.get());
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
    }
    return cmts.toString();
  }

  private void appendComment(StringBuilder out, int contextLines,
      PatchFile currentFileData, PatchLineComment comment) {
    short side = comment.getSide();
    CommentRange range = comment.getRange();
    if (range != null) {
      String prefix = "PS" + getCommentPsId(comment).get()
        + ", Line " + range.getStartLine() + ": ";
      for (int n = range.getStartLine(); n <= range.getEndLine(); n++) {
        out.append(n == range.getStartLine()
            ? prefix
            : Strings.padStart(": ", prefix.length(), ' '));
        try {
          String s = currentFileData.getLine(side, n);
          if (n == range.getStartLine() && n == range.getEndLine()) {
            s = s.substring(
                Math.min(range.getStartCharacter(), s.length()),
                Math.min(range.getEndCharacter(), s.length()));
          } else if (n == range.getStartLine()) {
            s = s.substring(Math.min(range.getStartCharacter(), s.length()));
          } else if (n == range.getEndLine()) {
            s = s.substring(0, Math.min(range.getEndCharacter(), s.length()));
          }
          out.append(s);
        } catch (Throwable e) {
          // Don't quote the line if we can't safely convert it.
        }
        out.append('\n');
      }
      appendQuotedParent(out, comment);
      out.append(comment.getMessage().trim()).append('\n');
    } else {
      int lineNbr = comment.getLine();
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
      appendQuotedParent(out, comment);
      out.append(comment.getMessage().trim()).append('\n');

      for (int line = lineNbr + 1; line < stopLine; ++line) {
        appendFileLine(out, currentFileData, side, line);
      }
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

  private void appendQuotedParent(StringBuilder out, PatchLineComment child) {
    if (child.getParentUuid() != null) {
      Optional<PatchLineComment> parent;
      PatchLineComment.Key key = new PatchLineComment.Key(
          child.getKey().getParentKey(),
          child.getParentUuid());
      try {
        parent = plcUtil.get(args.db.get(), changeData.notes(), key);
      } catch (OrmException e) {
        log.warn("Could not find the parent of this comment: "
            + child.toString());
        parent = Optional.absent();
      }
      if (parent.isPresent()) {
        String msg = parent.get().getMessage().trim();
        if (msg.length() > 75) {
          msg = msg.substring(0, 75);
        }
        int lf = msg.indexOf('\n');
        if (lf > 0) {
          msg = msg.substring(0, lf);
        }
        out.append("> ").append(msg).append('\n');
      }
    }
  }

  // Makes a link back to the given patch set and file.
  private String makeLink(Patch.Key patch) {
    String url = getGerritUrl();
    if (url == null) {
      return null;
    }

    PatchSet.Id ps = patch.getParentKey();
    Change.Id c = ps.getParentKey();
    return new StringBuilder()
      .append(url)
      .append("#/c/").append(c)
      .append('/').append(ps.get())
      .append('/').append(KeyUtil.encode(patch.get()))
      .toString();
  }

  private Repository getRepository() {
    try {
      return args.server.openRepository(projectState.getProject().getNameKey());
    } catch (IOException e) {
      return null;
    }
  }
}
