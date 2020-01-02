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

package com.google.gerrit.server.git.validators;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.validators.CommentForValidation;
import com.google.gerrit.extensions.validators.CommentValidationContext;
import com.google.gerrit.extensions.validators.CommentValidationFailure;
import com.google.gerrit.extensions.validators.CommentValidator;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.inject.Inject;
import org.eclipse.jgit.lib.Config;

/** Limits the size and number of comments to prevent large comments from causing issues. */
public class CommentLimitsValidator implements CommentValidator {
  private final int commentSizeLimit;
  private final int robotCommentSizeLimit;
  private final int maxComments;
  private final ChangeNotes.Factory notesFactory;

  @Inject
  CommentLimitsValidator(
      @GerritServerConfig Config serverConfig, ChangeNotes.Factory notesFactory) {
    this.notesFactory = notesFactory;
    commentSizeLimit = serverConfig.getInt("change", "commentSizeLimit", 16 << 10);
    robotCommentSizeLimit = serverConfig.getInt("change", "robotCommentSizeLimit", 1 << 20);
    maxComments = serverConfig.getInt("change", "maxComments", 5_000);
  }

  @Override
  public ImmutableList<CommentValidationFailure> validateComments(
      CommentValidationContext ctx, ImmutableList<CommentForValidation> comments) {
    ImmutableList.Builder<CommentValidationFailure> failures = ImmutableList.builder();
    failures.addAll(
        comments.stream()
            .filter(this::exceedsSizeLimit)
            .map(c -> c.failValidation(buildErrorMessage(c)))
            .iterator());
    ChangeNotes notes =
        notesFactory.createChecked(Project.nameKey(ctx.getProject()), Change.id(ctx.getChangeId()));
    int numExistingComments = notes.getComments().size() + notes.getRobotComments().size();
    if (!comments.isEmpty() && numExistingComments + comments.size() > maxComments) {
      // This warning really applies to the set of all comments, but we need to pick one to attach
      // the message to.
      CommentForValidation commentForFailureMessage = Iterables.getLast(comments);

      failures.add(
          commentForFailureMessage.failValidation(
              String.format(
                  "Exceeding maximum number of comments: %d (existing) + %d (new) > %d",
                  numExistingComments, comments.size(), maxComments)));
    }
    return failures.build();
  }

  private boolean exceedsSizeLimit(CommentForValidation comment) {
    switch (comment.getSource()) {
      case HUMAN:
        return comment.getApproximateSize() > commentSizeLimit;
      case ROBOT:
        return robotCommentSizeLimit > 0 && comment.getApproximateSize() > robotCommentSizeLimit;
    }
    throw new RuntimeException(
        "Unknown comment source (should not have compiled): " + comment.getSource());
  }

  private String buildErrorMessage(CommentForValidation comment) {
    switch (comment.getSource()) {
      case HUMAN:
        return String.format(
            "Comment size exceeds limit (%d > %d)", comment.getApproximateSize(), commentSizeLimit);

      case ROBOT:
        return String.format(
            "Size %d (bytes) of robot comment is greater than limit %d (bytes)",
            comment.getApproximateSize(), robotCommentSizeLimit);
    }
    throw new RuntimeException(
        "Unknown comment source (should not have compiled): " + comment.getSource());
  }
}
