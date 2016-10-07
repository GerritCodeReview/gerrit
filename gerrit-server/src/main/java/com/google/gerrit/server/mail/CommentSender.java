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

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.Ordering;
import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.reviewdb.client.AccountProjectWatch.NotifyType;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RobotComment;
import com.google.gerrit.server.CommentsUtil;
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

/** Send comments, after the author of them hit used Publish Comments in the UI.
 */
public class CommentSender extends ReplyToChangeSender {
  private static final Logger log = LoggerFactory
      .getLogger(CommentSender.class);

  public interface Factory {
    CommentSender create(Project.NameKey project, Change.Id id);
  }

  private List<Comment> inlineComments = Collections.emptyList();
  private final CommentsUtil commentsUtil;

  @Inject
  public CommentSender(EmailArguments ea,
      CommentsUtil commentsUtil,
      @Assisted Project.NameKey project,
      @Assisted Change.Id id) throws OrmException {
    super(ea, "comment", newChangeData(ea, project, id));
    this.commentsUtil = commentsUtil;
  }

  public void setComments(List<Comment> comments) throws OrmException {
    inlineComments = comments;

    Set<String> paths = new HashSet<>();
    for (Comment c : comments) {
      if (!Patch.isMagic(c.key.filename)) {
        paths.add(c.key.filename);
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
    appendText(textTemplate("Comment"));
  }

  @Override
  public void formatFooter() throws EmailException {
    appendText(textTemplate("CommentFooter"));
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

      String currentFileName = null;
      int currentPatchSetId = -1;
      PatchFile currentFileData = null;
      for (Comment c : inlineComments) {
        if (!c.key.filename.equals(currentFileName)
            || c.key.patchSetId != currentPatchSetId) {
          String link = makeLink(change.getId(), c.key);
          if (link != null) {
            cmts.append(link).append('\n');
          }
          if (Patch.COMMIT_MSG.equals(c.key.filename)) {
            cmts.append("Commit Message:\n\n");
          } else if (Patch.MERGE_LIST.equals(c.key.filename)) {
            cmts.append("Merge List:\n\n");
          } else {
            cmts.append("File ").append(c.key.filename).append(":\n\n");
          }
          currentFileName = c.key.filename;
          currentPatchSetId = c.key.patchSetId;

          if (patchList != null) {
            try {
              currentFileData =
                  new PatchFile(repo, patchList, c.key.filename);
            } catch (IOException e) {
              log.warn(String.format(
                  "Cannot load %s from %s in %s",
                  c.key.filename,
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
      PatchFile currentFileData, Comment comment) {
    if (comment instanceof RobotComment) {
      RobotComment robotComment = (RobotComment) comment;
      out.append("Robot Comment from ")
         .append(robotComment.robotId)
         .append(" (run ID ")
         .append(robotComment.robotRunId)
         .append("):\n");
    }
    short side = comment.side;
    Comment.Range range = comment.range;
    if (range != null) {
      String prefix = "PS" + comment.key.patchSetId
        + ", Line " + range.startLine + ": ";
      for (int n = range.startLine; n <= range.endLine; n++) {
        out.append(n == range.startLine
            ? prefix
            : Strings.padStart(": ", prefix.length(), ' '));
        String s = getLine(currentFileData, side, n);
        if (n == range.startLine && n == range.endLine) {
          s = s.substring(
              Math.min(range.startChar, s.length()),
              Math.min(range.endChar, s.length()));
        } else if (n == range.startLine) {
          s = s.substring(Math.min(range.startChar, s.length()));
        } else if (n == range.endLine) {
          s = s.substring(0, Math.min(range.endChar, s.length()));
        }
        out.append(s).append('\n');
      }
      appendQuotedParent(out, comment);
      out.append(comment.message.trim()).append('\n');
    } else {
      int lineNbr = comment.lineNbr;

      // Initialize maxLines to the known line number.
      int maxLines = lineNbr;

      try {
        maxLines = currentFileData.getLineCount(side);
      } catch (IOException err) {
        // The file could not be read, leave the max as is.
        log.warn(String.format("Failed to read file %s on side %d",
            comment.key.filename, side), err);
      } catch (NoSuchEntityException err) {
        // The file could not be read, leave the max as is.
        log.warn(String.format("Side %d of file %s didn't exist",
             side, comment.key.filename), err);
      }

      final int startLine = Math.max(1, lineNbr - contextLines + 1);
      final int stopLine = Math.min(maxLines, lineNbr + contextLines);

      for (int line = startLine; line <= lineNbr; ++line) {
        appendFileLine(out, currentFileData, side, line);
      }
      appendQuotedParent(out, comment);
      out.append(comment.message.trim()).append('\n');

      for (int line = lineNbr + 1; line < stopLine; ++line) {
        appendFileLine(out, currentFileData, side, line);
      }
    }
  }

  private void appendFileLine(StringBuilder cmts, PatchFile fileData,
      short side, int line) {
    String lineStr = getLine(fileData, side, line);
    cmts.append("Line ")
        .append(line)
        .append(": ")
        .append(lineStr)
        .append("\n");
  }

  private void appendQuotedParent(StringBuilder out, Comment child) {
    if (child.parentUuid != null) {
      Optional<Comment> parent;
      Comment.Key key = new Comment.Key(child.parentUuid, child.key.filename,
          child.key.patchSetId);
      try {
        parent = commentsUtil.get(args.db.get(), changeData.notes(), key);
      } catch (OrmException e) {
        log.warn("Could not find the parent of this comment: "
            + child.toString());
        parent = Optional.absent();
      }
      if (parent.isPresent()) {
        String msg = parent.get().message.trim();
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
  private String makeLink(Change.Id changeId, Comment.Key key) {
    String url = getGerritUrl();
    if (url == null) {
      return null;
    }

    return new StringBuilder()
      .append(url)
      .append("#/c/").append(changeId)
      .append('/').append(key.patchSetId)
      .append('/').append(KeyUtil.encode(key.filename))
      .toString();
  }

  private Repository getRepository() {
    try {
      return args.server.openRepository(projectState.getProject().getNameKey());
    } catch (IOException e) {
      return null;
    }
  }

  @Override
  protected void setupSoyContext() {
    super.setupSoyContext();
    soyContextEmailData.put("inlineComments", getInlineComments());
    soyContextEmailData.put("hasInlineComments", hasInlineComments());
  }

  private String getLine(PatchFile fileInfo, short side, int lineNbr) {
    try {
      return fileInfo.getLine(side, lineNbr);
    } catch (IOException err) {
      // Default to the empty string if the file cannot be safely read.
      log.warn(String.format("Failed to read file on side %d", side), err);
      return "";
    } catch (IndexOutOfBoundsException err) {
      // Default to the empty string if the given line number does not appear
      // in the file.
      log.warn(String.format("Failed to get line number of file on side %d",
          side), err);
      return "";
    } catch (NoSuchEntityException err) {
      // Default to the empty string if the side cannot be found.
      log.warn(String.format("Side %d of file didn't exist", side), err);
      return "";
    }
  }
}
