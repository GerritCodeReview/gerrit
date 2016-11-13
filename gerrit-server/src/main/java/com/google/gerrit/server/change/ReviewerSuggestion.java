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

package com.google.gerrit.server.change;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.annotations.ExtensionPoint;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import java.util.Set;

/**
 * Listener to provide reviewer suggestions.
 *
 * <p>Invoked by Gerrit a user who is searching for a reviewer to add to a change.
 */
@ExtensionPoint
public interface ReviewerSuggestion {
  /**
   * Reviewer suggestion.
   *
   * @param project The name key of the project the suggestion is for.
   * @param changeId The changeId that the suggestion is for. Can be an {@code null}.
   * @param query The query as typed by the user. Can be an {@code null}.
   * @param candidates A set of candidates for the ranking. Can be empty.
   * @return Set of suggested reviewers as a tuple of account id and score. The account ids listed
   *     here don't have to be a part of candidates.
   */
  Set<SuggestedReviewer> suggestReviewers(
      Project.NameKey project,
      @Nullable Change.Id changeId,
      @Nullable String query,
      Set<Account.Id> candidates);
}
