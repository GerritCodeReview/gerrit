// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.mail.send;

import com.google.common.base.Strings;
import com.google.common.collect.Ordering;
import com.google.gerrit.common.data.FilenameComparator;
import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RobotComment;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.account.WatchConfig.NotifyType;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.mail.MailUtil;
import com.google.gerrit.server.mail.receive.Protocol;
import com.google.gerrit.server.patch.PatchFile;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.util.LabelVote;
import com.google.gwtorm.client.KeyUtil;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Send comments, after the author of them hit used Publish Comments in the UI. */
public class CommentSender extends ReplyToChangeSender {
  private static final Logger log = LoggerFactory.getLogger(CommentSender.class);

  public interface Factory {
    CommentSender create(Project.NameKey project, Change.Id id);
  }

  private class FileCommentGroup {
    public String filename;
    public int patchSetId;
    public PatchFile fileData;
    public List<Comment> comments = new ArrayList<>();

    /** @return a web link to the given patch set and file. */
    public String getLink() {
      String url = getGerritUrl();
      if (url == null) {
        return null;
      }

      return new StringBuilder()
          .append(url)
          .append("#/c/")
          .append(change.getId())
          .append('/')
          .append(patchSetId)
          .append('/')
          .append(KeyUtil.encode(filename))
          .toString();
    }

    /**
     * @return A title for the group, i.e. "Commit Message", "Merge List", or "File [[filename]]".
     */
    public String getTitle() {
      if (Patch.COMMIT_MSG.equals(filename)) {
        return "Commit Message";
      } else if (Patch.MERGE_LIST.equals(filename)) {
        return "Merge List";
      } else {
        return "File " + filename;
      }
    }
  }

  private List<Comment> inlineComments = Collections.emptyList();
  private String patchSetComment;
  private List<LabelVote> labels = Collections.emptyList();
  private final CommentsUtil commentsUtil;
  private final boolean incomingEmailEnabled;
  private final String inboundEmailAddress;

  @Inject
  public CommentSender(
      EmailArguments ea,
      CommentsUtil commentsUtil,
      @GerritServerConfig Config cfg,
      @Assisted Project.NameKey project,
      @Assisted Change.Id id)
      throws OrmException {
    super(ea, "comment", newChangeData(ea, project, id));
    this.commentsUtil = commentsUtil;
    this.incomingEmailEnabled =
        cfg.getEnum("receiveemail", null, "protocol", Protocol.NONE).ordinal()
            > Protocol.NONE.ordinal();
    this.inboundEmailAddress = cfg.getString("receiveemail", null, "inboundAddress");
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

  public void setPatchSetComment(String comment) {
    this.patchSetComment = comment;
  }

  public void setLabels(List<LabelVote> labels) {
    this.labels = labels;
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

    // Add header that enables identifying comments on parsed email.
    // Grouping is currently done by timestamp.
    setHeader("X-Gerrit-Comment-Date", timestamp);

    if (incomingEmailEnabled) {
      if (inboundEmailAddress == null) {
        // Remove Reply-To and use outbound SMTP (default) instead.
        removeHeader("Reply-To");
      } else {
        setHeader("Reply-To", inboundEmailAddress);
      }
    }
  }

  @Override
  public void formatChange() throws EmailException {
    appendText(textTemplate("Comment"));
    if (useHtml()) {
      appendHtml(soyHtmlTemplate("CommentHtml"));
    }
  }

  @Override
  public void formatFooter() throws EmailException {
    appendText(textTemplate("CommentFooter"));
    if (useHtml()) {
      appendHtml(soyHtmlTemplate("CommentFooterHtml"));
    }
  }

  /** No longer used outside Velocity. Remove this method when VTL support is removed. */
  @Deprecated
  public boolean hasInlineComments() {
    return !inlineComments.isEmpty();
  }

  /** No longer used outside Velocity. Remove this method when VTL support is removed. */
  @Deprecated
  public String getInlineComments() {
    return getInlineComments(1);
  }

  /** No longer used outside Velocity. Remove this method when VTL support is removed. */
  @Deprecated
  public String getInlineComments(int lines) {
    try (Repository repo = getRepository()) {
      StringBuilder cmts = new StringBuilder();
      for (FileCommentGroup group : getGroupedInlineComments(repo)) {
        String link = group.getLink();
        if (link != null) {
          cmts.append(link).append('\n');
        }
        cmts.append(group.getTitle()).append(":\n\n");
        for (Comment c : group.comments) {
          appendComment(cmts, lines, group.fileData, c);
        }
        cmts.append("\n\n");
      }
      return cmts.toString();
    }
  }

  /**
   * @return a list of FileCommentGroup objects representing the inline comments grouped by the
   *     file.
   */
  private List<CommentSender.FileCommentGroup> getGroupedInlineComments(Repository repo) {
    List<CommentSender.FileCommentGroup> groups = new ArrayList<>();
    // Get the patch list:
    PatchList patchList = null;
    if (repo != null) {
      try {
        patchList = getPatchList();
      } catch (PatchListNotAvailableException e) {
        log.error("Failed to get patch list", e);
      }
    }

    // Loop over the comments and collect them into groups based on the file
    // location of the comment.
    FileCommentGroup currentGroup = null;
    for (Comment c : inlineComments) {
      // If it's a new group:
      if (currentGroup == null
          || !c.key.filename.equals(currentGroup.filename)
          || c.key.patchSetId != currentGroup.patchSetId) {
        currentGroup = new FileCommentGroup();
        currentGroup.filename = c.key.filename;
        currentGroup.patchSetId = c.key.patchSetId;
        groups.add(currentGroup);
        if (patchList != null) {
          try {
            currentGroup.fileData = new PatchFile(repo, patchList, c.key.filename);
          } catch (IOException e) {
            log.warn(
                String.format(
                    "Cannot load %s from %s in %s",
                    c.key.filename,
                    patchList.getNewId().name(),
                    projectState.getProject().getName()),
                e);
            currentGroup.fileData = null;
          }
        }
      }

      if (currentGroup.fileData != null) {
        currentGroup.comments.add(c);
      }
    }

    Collections.sort(groups, Comparator.comparing(g -> g.filename, FilenameComparator.INSTANCE));
    return groups;
  }

  /** No longer used except for Velocity. Remove this method when VTL support is removed. */
  @Deprecated
  private void appendComment(
      StringBuilder out, int contextLines, PatchFile currentFileData, Comment comment) {
    if (comment instanceof RobotComment) {
      RobotComment robotComment = (RobotComment) comment;
      out.append("Robot Comment from ")
          .append(robotComment.robotId)
          .append(" (run ID ")
          .append(robotComment.robotRunId)
          .append("):\n");
    }
    if (comment.range != null) {
      appendRangedComment(out, currentFileData, comment);
    } else {
      appendLineComment(out, contextLines, currentFileData, comment);
    }
  }

  /** No longer used except for Velocity. Remove this method when VTL support is removed. */
  @Deprecated
  private void appendRangedComment(StringBuilder out, PatchFile fileData, Comment comment) {
    String prefix = getCommentLinePrefix(comment);
    String emptyPrefix = Strings.padStart(": ", prefix.length(), ' ');
    boolean firstLine = true;
    for (String line : getLinesByRange(comment.range, fileData, comment.side)) {
      out.append(firstLine ? prefix : emptyPrefix).append(line).append('\n');
      firstLine = false;
    }
    appendQuotedParent(out, comment);
    out.append(comment.message.trim()).append('\n');
  }

  private String getCommentLinePrefix(Comment comment) {
    int lineNbr = comment.range == null ? comment.lineNbr : comment.range.startLine;
    StringBuilder sb = new StringBuilder();
    sb.append("PS").append(comment.key.patchSetId);
    if (lineNbr != 0) {
      sb.append(", Line ").append(lineNbr);
    }
    sb.append(": ");
    return sb.toString();
  }

  /**
   * @return the lines of file content in fileData that are encompassed by range on the given side.
   */
  private List<String> getLinesByRange(Comment.Range range, PatchFile fileData, short side) {
    List<String> lines = new ArrayList<>();

    for (int n = range.startLine; n <= range.endLine; n++) {
      String s = getLine(fileData, side, n);
      if (n == range.startLine && n == range.endLine) {
        s = s.substring(Math.min(range.startChar, s.length()), Math.min(range.endChar, s.length()));
      } else if (n == range.startLine) {
        s = s.substring(Math.min(range.startChar, s.length()));
      } else if (n == range.endLine) {
        s = s.substring(0, Math.min(range.endChar, s.length()));
      }
      lines.add(s);
    }
    return lines;
  }

  /** No longer used except for Velocity. Remove this method when VTL support is removed. */
  @Deprecated
  private void appendLineComment(
      StringBuilder out, int contextLines, PatchFile currentFileData, Comment comment) {
    short side = comment.side;
    int lineNbr = comment.lineNbr;

    // Initialize maxLines to the known line number.
    int maxLines = lineNbr;

    try {
      maxLines = currentFileData.getLineCount(side);
    } catch (IOException err) {
      // The file could not be read, leave the max as is.
      log.warn(String.format("Failed to read file %s on side %d", comment.key.filename, side), err);
    } catch (NoSuchEntityException err) {
      // The file could not be read, leave the max as is.
      log.warn(String.format("Side %d of file %s didn't exist", side, comment.key.filename), err);
    }

    int startLine = Math.max(1, lineNbr - contextLines + 1);
    int stopLine = Math.min(maxLines, lineNbr + contextLines);

    for (int line = startLine; line <= lineNbr; ++line) {
      appendFileLine(out, currentFileData, side, line);
    }
    appendQuotedParent(out, comment);
    out.append(comment.message.trim()).append('\n');

    for (int line = lineNbr + 1; line < stopLine; ++line) {
      appendFileLine(out, currentFileData, side, line);
    }
  }

  /** No longer used except for Velocity. Remove this method when VTL support is removed. */
  @Deprecated
  private void appendFileLine(StringBuilder cmts, PatchFile fileData, short side, int line) {
    String lineStr = getLine(fileData, side, line);
    cmts.append("Line ").append(line).append(": ").append(lineStr).append("\n");
  }

  /** No longer used except for Velocity. Remove this method when VTL support is removed. */
  @Deprecated
  private void appendQuotedParent(StringBuilder out, Comment child) {
    Optional<Comment> parent = getParent(child);
    if (parent.isPresent()) {
      out.append("> ").append(getShortenedCommentMessage(parent.get())).append('\n');
    }
  }

  /**
   * Get the parent comment of a given comment.
   *
   * @param child the comment with a potential parent comment.
   * @return an optional comment that will be present if the given comment has a parent, and is
   *     empty if it does not.
   */
  private Optional<Comment> getParent(Comment child) {
    if (child.parentUuid == null) {
      return Optional.empty();
    }

    Comment.Key key = new Comment.Key(child.parentUuid, child.key.filename, child.key.patchSetId);
    try {
      return commentsUtil.get(args.db.get(), changeData.notes(), key);
    } catch (OrmException e) {
      log.warn("Could not find the parent of this comment: " + child.toString());
      return Optional.empty();
    }
  }

  /**
   * Retrieve the file lines referred to by a comment.
   *
   * @param comment The comment that refers to some file contents. The comment may be a line comment
   *     or a ranged comment.
   * @param fileData The file on which the comment appears.
   * @return file contents referred to by the comment. If the comment is a line comment, the result
   *     will be a list of one string. Otherwise it will be a list of one or more strings.
   */
  private List<String> getLinesOfComment(Comment comment, PatchFile fileData) {
    List<String> lines = new ArrayList<>();
    if (comment.lineNbr == 0) {
      // file level comment has no line
      return lines;
    }
    if (comment.range == null) {
      lines.add(getLine(fileData, comment.side, comment.lineNbr));
    } else {
      lines.addAll(getLinesByRange(comment.range, fileData, comment.side));
    }
    return lines;
  }

  /**
   * @return a shortened version of the given comment's message. Will be shortened to 75 characters
   *     or the first line, whichever is shorter.
   */
  private String getShortenedCommentMessage(Comment comment) {
    String msg = comment.message.trim();
    if (msg.length() > 75) {
      msg = msg.substring(0, 75);
    }
    int lf = msg.indexOf('\n');
    if (lf > 0) {
      msg = msg.substring(0, lf);
    }
    return msg;
  }

  /**
   * @return grouped inline comment data mapped to data structures that are suitable for passing
   *     into Soy.
   */
  private List<Map<String, Object>> getCommentGroupsTemplateData(Repository repo) {
    List<Map<String, Object>> commentGroups = new ArrayList<>();

    for (CommentSender.FileCommentGroup group : getGroupedInlineComments(repo)) {
      Map<String, Object> groupData = new HashMap<>();
      groupData.put("link", group.getLink());
      groupData.put("title", group.getTitle());
      groupData.put("patchSetId", group.patchSetId);

      List<Map<String, Object>> commentsList = new ArrayList<>();
      for (Comment comment : group.comments) {
        Map<String, Object> commentData = new HashMap<>();
        commentData.put("lines", getLinesOfComment(comment, group.fileData));
        commentData.put("message", comment.message.trim());
        List<CommentFormatter.Block> blocks = CommentFormatter.parse(comment.message);
        commentData.put("messageBlocks", commentBlocksToSoyData(blocks));

        // Set the prefix.
        String prefix = getCommentLinePrefix(comment);
        commentData.put("linePrefix", prefix);
        commentData.put("linePrefixEmpty", Strings.padStart(": ", prefix.length(), ' '));

        // Set line numbers.
        int startLine;
        if (comment.range == null) {
          startLine = comment.lineNbr;
        } else {
          startLine = comment.range.startLine;
          commentData.put("endLine", comment.range.endLine);
        }
        commentData.put("startLine", startLine);

        // Set the comment link.
        if (comment.lineNbr == 0) {
          commentData.put("link", group.getLink());
        } else if (comment.side == 0) {
          commentData.put("link", group.getLink() + "@a" + startLine);
        } else {
          commentData.put("link", group.getLink() + '@' + startLine);
        }

        // Set robot comment data.
        if (comment instanceof RobotComment) {
          RobotComment robotComment = (RobotComment) comment;
          commentData.put("isRobotComment", true);
          commentData.put("robotId", robotComment.robotId);
          commentData.put("robotRunId", robotComment.robotRunId);
          commentData.put("robotUrl", robotComment.url);
        } else {
          commentData.put("isRobotComment", false);
        }

        // If the comment has a quote, don't bother loading the parent message.
        if (!hasQuote(blocks)) {
          // Set parent comment info.
          Optional<Comment> parent = getParent(comment);
          if (parent.isPresent()) {
            commentData.put("parentMessage", getShortenedCommentMessage(parent.get()));
          }
        }

        commentsList.add(commentData);
      }
      groupData.put("comments", commentsList);

      commentGroups.add(groupData);
    }
    return commentGroups;
  }

  private List<Map<String, Object>> commentBlocksToSoyData(List<CommentFormatter.Block> blocks) {
    return blocks
        .stream()
        .map(
            b -> {
              Map<String, Object> map = new HashMap<>();
              switch (b.type) {
                case PARAGRAPH:
                  map.put("type", "paragraph");
                  map.put("text", b.text);
                  break;
                case PRE_FORMATTED:
                  map.put("type", "pre");
                  map.put("text", b.text);
                  break;
                case QUOTE:
                  map.put("type", "quote");
                  map.put("quotedBlocks", commentBlocksToSoyData(b.quotedBlocks));
                  break;
                case LIST:
                  map.put("type", "list");
                  map.put("items", b.items);
                  break;
              }
              return map;
            })
        .collect(Collectors.toList());
  }

  private boolean hasQuote(List<CommentFormatter.Block> blocks) {
    for (CommentFormatter.Block block : blocks) {
      if (block.type == CommentFormatter.BlockType.QUOTE) {
        return true;
      }
    }
    return false;
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
    boolean hasComments = false;
    try (Repository repo = getRepository()) {
      List<Map<String, Object>> files = getCommentGroupsTemplateData(repo);
      soyContext.put("commentFiles", files);
      hasComments = !files.isEmpty();
    }

    soyContext.put(
        "patchSetCommentBlocks", commentBlocksToSoyData(CommentFormatter.parse(patchSetComment)));
    soyContext.put("labels", getLabelVoteSoyData(labels));
    soyContext.put("commentCount", inlineComments.size());
    soyContext.put("commentTimestamp", getCommentTimestamp());
    soyContext.put(
        "coverLetterBlocks", commentBlocksToSoyData(CommentFormatter.parse(getCoverLetter())));

    footers.add("Gerrit-Comment-Date: " + getCommentTimestamp());
    footers.add("Gerrit-HasComments: " + (hasComments ? "Yes" : "No"));
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
      log.debug(String.format("Failed to get line number of file on side %d", side), err);
      return "";
    } catch (NoSuchEntityException err) {
      // Default to the empty string if the side cannot be found.
      log.warn(String.format("Side %d of file didn't exist", side), err);
      return "";
    }
  }

  private List<Map<String, Object>> getLabelVoteSoyData(List<LabelVote> votes) {
    List<Map<String, Object>> result = new ArrayList<>();
    for (LabelVote vote : votes) {
      Map<String, Object> data = new HashMap<>();
      data.put("label", vote.label());

      // Soy needs the short to be cast as an int for it to get converted to the
      // correct tamplate type.
      data.put("value", (int) vote.value());
      result.add(data);
    }
    return result;
  }

  private String getCommentTimestamp() {
    // Grouping is currently done by timestamp.
    return MailUtil.rfcDateformatter.format(
        ZonedDateTime.ofInstant(timestamp.toInstant(), ZoneId.of("UTC")));
  }

  @Override
  protected boolean supportsHtml() {
    return true;
  }
}
