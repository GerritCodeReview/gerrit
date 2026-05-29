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

package com.google.gerrit.server.change;

import static com.google.gerrit.entities.Patch.PATCHSET_LEVEL;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.client.Comment.Range;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.FixReplacementInfo;
import com.google.gerrit.extensions.common.FixSuggestionInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.patch.DiffSummary;
import com.google.gerrit.server.patch.DiffSummaryKey;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListKey;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jgit.lib.ObjectId;

@Singleton
public class CommentsValidator {

  private final CommentsUtil commentsUtil;
  private final PatchListCache patchListCache;

  @Inject
  CommentsValidator(CommentsUtil commentsUtil, PatchListCache patchListCache) {
    this.commentsUtil = commentsUtil;
    this.patchListCache = patchListCache;
  }

  public static void ensureFixSuggestionsAreAddable(
      List<FixSuggestionInfo> fixSuggestionInfos, String commentPath) throws BadRequestException {
    if (fixSuggestionInfos == null) {
      return;
    }

    for (FixSuggestionInfo fixSuggestionInfo : fixSuggestionInfos) {
      ensureDescriptionIsSet(commentPath, fixSuggestionInfo.description);
      ensureFixReplacementsAreAddable(commentPath, fixSuggestionInfo.replacements);
    }
  }

  public <T extends com.google.gerrit.extensions.client.Comment> void checkComments(
      RevisionResource revision, Map<String, List<T>> commentsPerPath)
      throws BadRequestException, PatchListNotAvailableException {
    Set<String> revisionFilePaths = getAffectedFilePaths(revision);
    for (Map.Entry<String, List<T>> entry : commentsPerPath.entrySet()) {
      String path = entry.getKey();
      PatchSet.Id patchSetId = revision.getPatchSet().id();
      ensurePathRefersToAvailableOrMagicFile(path, revisionFilePaths, patchSetId);

      List<T> comments = entry.getValue();
      for (T comment : comments) {
        ensureLineIsNonNegative(comment.line, path);
        ensureCommentNotOnMagicFilesOfAutoMerge(path, comment);
        ensureRangeIsValid(path, comment.range);
        ensureValidPatchsetLevelComment(path, comment);
        ensureValidInReplyTo(revision.getNotes(), comment.inReplyTo);
        ensureFixSuggestionsAreAddable(comment.fixSuggestions, path);
      }
    }
  }

  private void ensureValidInReplyTo(ChangeNotes changeNotes, String inReplyTo)
      throws BadRequestException {
    if (inReplyTo != null
        && !commentsUtil.getPublishedHumanComment(changeNotes, inReplyTo).isPresent()) {
      throw new BadRequestException(
          String.format("Invalid inReplyTo, comment %s not found", inReplyTo));
    }
  }

  private Set<String> getAffectedFilePaths(RevisionResource revision)
      throws PatchListNotAvailableException {
    ObjectId newId = revision.getPatchSet().commitId();
    DiffSummaryKey key =
        DiffSummaryKey.fromPatchListKey(
            PatchListKey.againstDefaultBase(newId, Whitespace.IGNORE_NONE));
    DiffSummary ds = patchListCache.getDiffSummary(key, revision.getProject());
    return new HashSet<>(ds.getPaths());
  }

  private static void ensurePathRefersToAvailableOrMagicFile(
      String path, Set<String> availableFilePaths, PatchSet.Id patchSetId)
      throws BadRequestException {
    if (!availableFilePaths.contains(path) && !Patch.isMagic(path)) {
      throw new BadRequestException(
          String.format("file %s not found in revision %s", path, patchSetId));
    }
  }

  private static void ensureLineIsNonNegative(Integer line, String path)
      throws BadRequestException {
    if (line != null && line < 0) {
      throw new BadRequestException(
          String.format("negative line number %d not allowed on %s", line, path));
    }
  }

  private static <T extends com.google.gerrit.extensions.client.Comment>
      void ensureCommentNotOnMagicFilesOfAutoMerge(String path, T comment)
          throws BadRequestException {
    if (Patch.isMagic(path) && comment.side == Side.PARENT && comment.parent == null) {
      throw new BadRequestException(String.format("cannot comment on %s on auto-merge", path));
    }
  }

  private static <T extends com.google.gerrit.extensions.client.Comment>
      void ensureValidPatchsetLevelComment(String path, T comment) throws BadRequestException {
    if (path.equals(PATCHSET_LEVEL)
        && (comment.side != null || comment.range != null || comment.line != null)) {
      throw new BadRequestException("Patchset-level comments can't have side, range, or line");
    }
  }

  private static void ensureFixReplacementsAreAddable(
      String commentPath, List<FixReplacementInfo> fixReplacementInfos) throws BadRequestException {
    ensureReplacementsArePresent(commentPath, fixReplacementInfos);

    for (FixReplacementInfo fixReplacementInfo : fixReplacementInfos) {
      ensureReplacementPathIsSetAndNotPatchsetLevel(commentPath, fixReplacementInfo.path);
      ensureRangeIsSet(commentPath, fixReplacementInfo.range);
      ensureRangeIsValid(commentPath, fixReplacementInfo.range);
      ensureReplacementStringIsSet(commentPath, fixReplacementInfo.replacement);
    }

    Map<String, List<FixReplacementInfo>> replacementsPerFilePath =
        fixReplacementInfos.stream().collect(groupingBy(fixReplacement -> fixReplacement.path));
    for (List<FixReplacementInfo> sameFileReplacements : replacementsPerFilePath.values()) {
      ensureRangesDoNotOverlap(commentPath, sameFileReplacements);
    }
  }

  private static void ensureReplacementsArePresent(
      String commentPath, List<FixReplacementInfo> fixReplacementInfos) throws BadRequestException {
    if (fixReplacementInfos == null || fixReplacementInfos.isEmpty()) {
      throw new BadRequestException(
          String.format(
              "At least one replacement is "
                  + "required for the suggested fix of the comment on %s",
              commentPath));
    }
  }

  private static void ensureReplacementPathIsSetAndNotPatchsetLevel(
      String commentPath, String replacementPath) throws BadRequestException {
    if (replacementPath == null) {
      throw new BadRequestException(
          String.format(
              "A file path must be given for the replacement of the comment on %s", commentPath));
    }
    if (replacementPath.equals(PATCHSET_LEVEL)) {
      throw new BadRequestException(
          String.format(
              "A file path must not be %s for the replacement of the comment on %s",
              PATCHSET_LEVEL, commentPath));
    }
  }

  private static void ensureRangeIsSet(String commentPath, Range range) throws BadRequestException {
    if (range == null) {
      throw new BadRequestException(
          String.format(
              "A range must be given for the replacement of the comment on %s", commentPath));
    }
  }

  private static void ensureRangeIsValid(String commentPath, Range range)
      throws BadRequestException {
    if (range == null) {
      return;
    }
    if (!range.isValid()) {
      throw new BadRequestException(
          String.format(
              "Range (%s:%s - %s:%s) is not valid for the comment on %s",
              range.startLine,
              range.startCharacter,
              range.endLine,
              range.endCharacter,
              commentPath));
    }
  }

  private static void ensureReplacementStringIsSet(String commentPath, String replacement)
      throws BadRequestException {
    if (replacement == null) {
      throw new BadRequestException(
          String.format(
              "A content for replacement "
                  + "must be indicated for the replacement of the comment on %s",
              commentPath));
    }
  }

  private static void ensureRangesDoNotOverlap(
      String commentPath, List<FixReplacementInfo> fixReplacementInfos) throws BadRequestException {
    List<Range> sortedRanges =
        fixReplacementInfos.stream()
            .map(fixReplacementInfo -> fixReplacementInfo.range)
            .sorted()
            .collect(toList());

    int previousEndLine = 0;
    int previousOffset = -1;
    for (Range range : sortedRanges) {
      if (range.startLine < previousEndLine
          || (range.startLine == previousEndLine && range.startCharacter < previousOffset)) {
        throw new BadRequestException(
            String.format("Replacements overlap for the comment on %s", commentPath));
      }
      previousEndLine = range.endLine;
      previousOffset = range.endCharacter;
    }
  }

  private static void ensureDescriptionIsSet(String commentPath, String description)
      throws BadRequestException {
    if (description == null) {
      throw new BadRequestException(
          String.format(
              "A description is required for the suggested fix of the comment on %s", commentPath));
    }
  }
}
