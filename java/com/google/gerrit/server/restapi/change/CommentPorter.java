// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.server.restapi.change;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.gerrit.server.patch.GitPositionTransformer.Range.create;
import static java.util.stream.Collectors.groupingBy;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.Comment.Range;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.patch.DiffMappings;
import com.google.gerrit.server.patch.GitPositionTransformer;
import com.google.gerrit.server.patch.GitPositionTransformer.BestPositionOnConflict;
import com.google.gerrit.server.patch.GitPositionTransformer.Mapping;
import com.google.gerrit.server.patch.GitPositionTransformer.Position;
import com.google.gerrit.server.patch.GitPositionTransformer.PositionedEntity;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListKey;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Container for all logic necessary to port comments to a target patchset.
 *
 * <p>A ported comment is a comment which was left on an earlier patchset and is shown on a later
 * patchset. If a comment eligible for porting (e.g. before target patchset) can't be matched to its
 * exact position in the target patchset, we'll map it to its next best location. This can also
 * include a transformation of a line comment into a file comment.
 */
@Singleton
public class CommentPorter {

  private final GitPositionTransformer positionTransformer =
      new GitPositionTransformer(BestPositionOnConflict.INSTANCE);
  private final PatchListCache patchListCache;

  @Inject
  public CommentPorter(PatchListCache patchListCache) {
    this.patchListCache = patchListCache;
  }

  /**
   * Ports the given comments to the target patchset.
   *
   * <p>Not all given comments are ported. Only those fulfilling some criteria (e.g. before target
   * patchset) are considered eligible for porting.
   *
   * <p>The returned comments represent the ported version. They don't bear any indication to which
   * patchset they were ported. This is intentional as the target patchset should be obvious from
   * the API or the used REST resources. The returned comments still have the patchset field filled.
   * It contains the reference to the patchset on which the comment was originally left. That
   * patchset number can vary among the returned comments as all comments before the target patchset
   * are potentially eligible for porting.
   *
   * <p>The number of returned comments can be smaller (-> only eligible ones are ported!) or larger
   * compared to the provided comments. The latter happens when files appear as copied in the target
   * patchset. In such a situation, the same comment UUID will occur more than once in the returned
   * comments.
   *
   * @param changeNotes the {@link ChangeNotes} of the change to which the comments belong
   * @param targetPatchset the patchset to which the comments should be ported
   * @param comments the original comments
   * @return the ported comments, in no particular order
   */
  public ImmutableList<HumanComment> portComments(
      ChangeNotes changeNotes, PatchSet targetPatchset, List<HumanComment> comments)
      throws PatchListNotAvailableException {

    ImmutableList<HumanComment> relevantComments = filterToRelevant(comments, targetPatchset);
    return portToTargetPatchset(changeNotes, targetPatchset, relevantComments);
  }

  private ImmutableList<HumanComment> filterToRelevant(
      List<HumanComment> allComments, PatchSet targetPatchset) {
    return allComments.stream()
        .filter(comment -> comment.key.patchSetId < targetPatchset.number())
        // TODO(aliceks): Also support comments on parent revisions.
        .filter(comment -> comment.side > 0)
        .collect(toImmutableList());
  }

  private ImmutableList<HumanComment> portToTargetPatchset(
      ChangeNotes notes, PatchSet targetPatchset, List<HumanComment> comments)
      throws PatchListNotAvailableException {
    Map<Integer, ImmutableList<HumanComment>> commentsPerPatchset =
        comments.stream().collect(groupingBy(comment -> comment.key.patchSetId, toImmutableList()));

    ImmutableList.Builder<HumanComment> portedComments =
        ImmutableList.builderWithExpectedSize(comments.size());
    for (Integer originalPatchsetId : commentsPerPatchset.keySet()) {
      ImmutableList<HumanComment> patchsetComments = commentsPerPatchset.get(originalPatchsetId);
      PatchSet originalPatchset =
          notes.getPatchSets().get(PatchSet.id(notes.getChangeId(), originalPatchsetId));
      portedComments.addAll(
          portToTargetPatchset(
              notes.getProjectName(), originalPatchset, targetPatchset, patchsetComments));
    }
    return portedComments.build();
  }

  private ImmutableList<HumanComment> portToTargetPatchset(
      Project.NameKey project,
      PatchSet originalPatchset,
      PatchSet targetPatchset,
      ImmutableList<HumanComment> comments)
      throws PatchListNotAvailableException {
    ImmutableSet<Mapping> mappings =
        loadPatchsetMappings(project, originalPatchset, targetPatchset);
    ImmutableList<PositionedEntity<HumanComment>> positionedComments =
        comments.stream().map(this::toPositionedEntity).collect(toImmutableList());
    return positionTransformer.transform(positionedComments, mappings).stream()
        .map(PositionedEntity::getEntityAtUpdatedPosition)
        .collect(toImmutableList());
  }

  private ImmutableSet<Mapping> loadPatchsetMappings(
      Project.NameKey project, PatchSet originalPatchset, PatchSet targetPatchset)
      throws PatchListNotAvailableException {
    PatchList patchList =
        patchListCache.get(
            PatchListKey.againstCommit(
                originalPatchset.commitId(), targetPatchset.commitId(), Whitespace.IGNORE_NONE),
            project);
    return patchList.getPatches().stream().map(DiffMappings::toMapping).collect(toImmutableSet());
  }

  private PositionedEntity<HumanComment> toPositionedEntity(HumanComment comment) {
    return PositionedEntity.create(
        comment, CommentPorter::extractPosition, CommentPorter::createCommentAtNewPosition);
  }

  private static Position extractPosition(HumanComment comment) {
    Position.Builder positionBuilder = Position.builder();
    // Patchset-level comments don't have a file path. The transformation logic still works when
    // using the magic file path but it doesn't hurt to use the actual representation for "no file"
    // internally.
    if (!Patch.PATCHSET_LEVEL.equals(comment.key.filename)) {
      positionBuilder.filePath(comment.key.filename);
    }
    return positionBuilder.lineRange(extractLineRange(comment)).build();
  }

  private static Optional<GitPositionTransformer.Range> extractLineRange(HumanComment comment) {
    // Line specifications in comment are 1-based. Line specifications in Position are 0-based.
    if (comment.range != null) {
      // The combination of (line, charOffset) is exclusive and must be mapped to an exclusive line.
      int exclusiveEndLine =
          comment.range.endChar > 0 ? comment.range.endLine : comment.range.endLine - 1;
      return Optional.of(create(comment.range.startLine - 1, exclusiveEndLine));
    }
    if (comment.lineNbr > 0) {
      return Optional.of(create(comment.lineNbr - 1, comment.lineNbr));
    }
    // File comment -> no range.
    return Optional.empty();
  }

  private static HumanComment createCommentAtNewPosition(
      HumanComment originalComment, Position newPosition) {
    HumanComment portedComment = new HumanComment(originalComment);
    portedComment.key.filename = newPosition.filePath().orElse(Patch.PATCHSET_LEVEL);
    if (portedComment.range != null && newPosition.lineRange().isPresent()) {
      // Comment was a range comment and also stayed one.
      portedComment.range =
          toRange(
              newPosition.lineRange().get(),
              portedComment.range.startChar,
              portedComment.range.endChar);
      portedComment.lineNbr = portedComment.range.endLine;
    } else {
      portedComment.range = null;
      portedComment.lineNbr = newPosition.lineRange().map(range -> range.start() + 1).orElse(0);
    }
    return portedComment;
  }

  private static Range toRange(
      GitPositionTransformer.Range lineRange, int originalStartChar, int originalEndChar) {
    int adjustedEndLine = originalEndChar > 0 ? lineRange.end() : lineRange.end() + 1;
    return new Range(lineRange.start() + 1, originalStartChar, adjustedEndLine, originalEndChar);
  }
}
