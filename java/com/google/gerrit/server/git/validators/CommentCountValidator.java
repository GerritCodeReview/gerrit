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
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.validators.CommentForValidation;
import com.google.gerrit.extensions.validators.CommentValidationContext;
import com.google.gerrit.extensions.validators.CommentValidationFailure;
import com.google.gerrit.extensions.validators.CommentValidator;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.inject.Inject;
import com.google.inject.Provider;
import org.eclipse.jgit.lib.Config;

/** Limits number of comments to prevent space/time complexity issues. */
public class CommentCountValidator implements CommentValidator {

  private final int maxComments;
  private final int maxCommentsPerUser;
  private final ChangeNotes.Factory notesFactory;
  private final Provider<CurrentUser> currentUserProvider;

  @Inject
  CommentCountValidator(
      @GerritServerConfig Config serverConfig,
      ChangeNotes.Factory notesFactory,
      Provider<CurrentUser> currentUserProvider) {
    this.notesFactory = notesFactory;
    this.currentUserProvider = currentUserProvider;
    this.maxComments = serverConfig.getInt("change", "maxComments", 5_000);
    this.maxCommentsPerUser = serverConfig.getInt("change", "maxCommentsPerUser", 0);
  }

  @Override
  public ImmutableList<CommentValidationFailure> validateComments(
      CommentValidationContext ctx, ImmutableList<CommentForValidation> comments) {

    ImmutableList.Builder<CommentValidationFailure> failures = ImmutableList.builder();

    ChangeNotes notes =
        notesFactory.createChecked(Project.nameKey(ctx.getProject()), Change.id(ctx.getChangeId()));

    int totalExistingComments = notes.getHumanComments().size() + notes.getChangeMessages().size();

    CommentForValidation lastComment = Iterables.getLast(comments, null);

    if (!comments.isEmpty() && totalExistingComments + comments.size() > maxComments) {
      // This warning really applies to the set of all comments, but we need to pick one to attach
      // the message to.
      failures.add(
          lastComment.failValidation(
              String.format(
                  "Exceeding maximum number of comments: %d (existing) + %d (new) > %d",
                  totalExistingComments, comments.size(), maxComments)));
    }

    // No per-user limit configured
    if (maxCommentsPerUser <= 0) {
      return failures.build();
    }

    // Identify the user
    CurrentUser user = currentUserProvider.get();
    if (!user.isIdentifiedUser()) {
      return failures.build();
    }

    Account.Id userId = user.asIdentifiedUser().getAccountId();

    // Count existing comments per user only if needed
    long existing =
        notes.getHumanComments().values().stream()
            .filter(c -> c.author != null && c.author.getId().equals(userId))
            .count();
    existing +=
        notes.getChangeMessages().stream()
            .filter(cm -> cm.getAuthor() != null && cm.getAuthor().equals(userId))
            .count();

    if (existing + comments.size() > maxCommentsPerUser) {
      failures.add(
          lastComment.failValidation(
              String.format(
                  "Exceeding maximum comments per user: %d (existing) + %d (new) > %d",
                  existing, comments.size(), maxCommentsPerUser)));
    }

    return failures.build();
  }
}
