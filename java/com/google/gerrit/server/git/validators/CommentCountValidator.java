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

/** Limits number of comments to prevent space/time complexity issues. */
public class CommentCountValidator implements CommentValidator {
  private final int maxComments;
  private final ChangeNotes.Factory notesFactory;

  @Inject
  CommentCountValidator(@GerritServerConfig Config serverConfig, ChangeNotes.Factory notesFactory) {
    this.notesFactory = notesFactory;
    maxComments = serverConfig.getInt("change", "maxComments", 5_000);
  }

  @Override
  public ImmutableList<CommentValidationFailure> validateComments(
      CommentValidationContext ctx, ImmutableList<CommentForValidation> comments) {
    ImmutableList.Builder<CommentValidationFailure> failures = ImmutableList.builder();
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
}
