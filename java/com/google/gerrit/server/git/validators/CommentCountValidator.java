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
import java.util.HashMap;
import java.util.Map;
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

    maxComments = serverConfig.getInt("change", "maxComments", 5_000);
    maxCommentsPerUser = serverConfig.getInt("change", "maxCommentsPerUser", 0);
  }

  @Override
  public ImmutableList<CommentValidationFailure> validateComments(
      CommentValidationContext ctx, ImmutableList<CommentForValidation> comments) {

    ImmutableList.Builder<CommentValidationFailure> failures = ImmutableList.builder();

    ChangeNotes notes =
        notesFactory.createChecked(Project.nameKey(ctx.getProject()), Change.id(ctx.getChangeId()));

    int numExistingCommentsAndChangeMessages =
        notes.getHumanComments().size()
            + notes.getRobotComments().size()
            + notes.getChangeMessages().size();

    CommentForValidation commentForFailureMessage = Iterables.getLast(comments, null);

    // --- total comments limit ---
    if (!comments.isEmpty()
        && numExistingCommentsAndChangeMessages + comments.size() > maxComments) {

      failures.add(
          commentForFailureMessage.failValidation(
              String.format(
                  "Exceeding maximum number of comments: %d (existing) + %d (new) > %d",
                  numExistingCommentsAndChangeMessages, comments.size(), maxComments)));
    }

    // --- per-user comment counting (only for identified users) ---
    Map<Account.Id, Integer> existingCommentsPerUser = new HashMap<>();

    notes
        .getHumanComments()
        .values()
        .forEach(
            c -> {
              if (c.author != null) { // ensure author exists
                existingCommentsPerUser.merge(c.author.getId(), 1, Integer::sum);
              }
            });

    notes
        .getChangeMessages()
        .forEach(
            cm -> {
              if (cm.getAuthor() != null) { // only count identified users
                existingCommentsPerUser.merge(cm.getAuthor(), 1, Integer::sum);
              }
            });

    // --- identify current user ---
    CurrentUser user = currentUserProvider.get();
    Account.Id userId = null;

    if (user.isIdentifiedUser()) {
      userId = user.asIdentifiedUser().getAccountId();
    }

    // --- per-user limit enforcement ---
    if (userId != null && maxCommentsPerUser > 0) {
      if (!comments.isEmpty()
          && (existingCommentsPerUser.getOrDefault(userId, 0) + comments.size()
              > maxCommentsPerUser)) {
        failures.add(
            commentForFailureMessage.failValidation(
                String.format(
                    "Exceeding maximum comments per user: %d (existing) + %d (new) > %d",
                    existingCommentsPerUser.getOrDefault(userId, 0),
                    comments.size(),
                    maxCommentsPerUser)));
      }
    }

    return failures.build();
  }
}
