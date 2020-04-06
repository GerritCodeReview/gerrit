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
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.validators.CommentForValidation;
import com.google.gerrit.extensions.validators.CommentValidationContext;
import com.google.gerrit.extensions.validators.CommentValidationFailure;
import com.google.gerrit.extensions.validators.CommentValidator;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.inject.Inject;
import java.util.stream.Stream;
import org.eclipse.jgit.lib.Config;

// ö Can we make sure the change message is also counted?
// MailProcessor: should be OK, is included
// ReceiveCommits: should be OK, cannot specify change message
/** Limits the total size of all comments to prevent space/time complexity issues. */
public class CommentCumulativeSizeValidator implements CommentValidator {
  private final int maxCumulativeSize;
  private final ChangeNotes.Factory notesFactory;

  @Inject
  CommentCumulativeSizeValidator(
      @GerritServerConfig Config serverConfig, ChangeNotes.Factory notesFactory) {
    this.notesFactory = notesFactory;
    maxCumulativeSize = serverConfig.getInt("change", "maxCumulativeSizeBytes", 3 << 20);
  }

  @Override
  public ImmutableList<CommentValidationFailure> validateComments(
      CommentValidationContext ctx, ImmutableList<CommentForValidation> comments) {
    ChangeNotes notes =
        notesFactory.createChecked(Project.nameKey(ctx.getProject()), Change.id(ctx.getChangeId()));
    int existingCommentsSize =
        Stream.concat(
                notes.getComments().values().stream(), notes.getRobotComments().values().stream())
            .mapToInt(Comment::getApproximateSize)
            .reduce(0, Integer::sum);
    int newCommentsSize =
        comments.stream()
            .mapToInt(CommentForValidation::getApproximateSize)
            .reduce(0, Integer::sum);
    ImmutableList.Builder<CommentValidationFailure> failures = ImmutableList.builder();
    if (!comments.isEmpty() && existingCommentsSize + newCommentsSize > maxCumulativeSize) {
      // This warning really applies to the set of all comments, but we need to pick one to attach
      // the message to.
      CommentForValidation commentForFailureMessage = Iterables.getLast(comments);

      failures.add(
          commentForFailureMessage.failValidation(
              String.format(
                  "Exceeding maximum cumulative size of comments: %d (existing) + %d (new) > %d",
                  existingCommentsSize, newCommentsSize, maxCumulativeSize)));
    }
    return failures.build();
  }
}
