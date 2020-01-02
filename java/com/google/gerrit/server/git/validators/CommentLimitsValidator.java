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
import com.google.gerrit.extensions.validators.CommentForValidation.CommentType;
import com.google.gerrit.extensions.validators.CommentValidationContext;
import com.google.gerrit.extensions.validators.CommentValidationFailure;
import com.google.gerrit.extensions.validators.CommentValidator;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.inject.Inject;
import org.eclipse.jgit.lib.Config;

/** Limits the size of comments to prevent large comments from causing issues. */
public class CommentLimitsValidator implements CommentValidator {
  private final int maxCommentLength;
  private final int maxComments;
  private final ChangeNotes.Factory notesFactory;

  @Inject
  CommentLimitsValidator(
      @GerritServerConfig Config serverConfig, ChangeNotes.Factory notesFactory) {
    this.notesFactory = notesFactory;
    maxCommentLength = serverConfig.getInt("change", null, "maxCommentLength", 16 << 10);
    maxComments = serverConfig.getInt("change", null, "maxComments", 5_000);
  }

  @Override
  public ImmutableList<CommentValidationFailure> validateComments(
      CommentValidationContext ctx, ImmutableList<CommentForValidation> comments) {
    ChangeNotes notes =
        notesFactory.createChecked(Project.nameKey(ctx.getProject()), Change.id(ctx.getChangeId()));
    ImmutableList.Builder<CommentValidationFailure> failures = ImmutableList.builder();
    failures.addAll(
        comments.stream()
            .filter(c -> c.getText().length() > maxCommentLength)
            .map(
                c ->
                    c.failValidation(
                        String.format(
                            "Comment too large (%d > %d)", c.getText().length(), maxCommentLength)))
            .iterator());
    int numExistingComments = notes.getComments().size() + notes.getRobotComments().size();
    int numNewComments = comments.size() + ctx.getNumNewRobotComments();
    if (numExistingComments + numNewComments > maxComments) {
      // This warning really applies to the set of all comments, but we need to pick one to attach
      // the message to.
      CommentForValidation commentForFailureMessage =
          comments.isEmpty()
              ? CommentForValidation.create(CommentType.ROBOT_COMMENT_PLACEHOLDER, "")
              : Iterables.getLast(comments);
      failures.add(
          commentForFailureMessage.failValidation(
              String.format(
                  "Exceeding maximum number of comments: %d (existing) + %d (new) > %d",
                  numExistingComments, numNewComments, maxComments)));
    }
    return failures.build();
  }
}
