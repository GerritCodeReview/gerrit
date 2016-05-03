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

import com.google.gerrit.extensions.annotations.ExtensionPoint;
import com.google.gerrit.reviewdb.client.Account;

import java.util.List;
import java.util.Set;

/**
 * Listener to provide reviewer suggestions.
 *
 * Invoked by Gerrit a user who is searching for a reviewer to add to a change.
 */
@ExtensionPoint
public interface ReviewerSuggestion {
  /**
   * Reviewer suggestion.
   *
   * @param query The query as typed by the user. Can be an empty string.
   * @param candidates A list of candidates for the ranking.
   * @return List of suggested reviewers as a tuple of account id and score.
   *         The account ids listed here don't have to be a part of candidates.
   */
  List<SuggestedReviewer> suggestReviewers(String query, Set<Account.Id> candidates);
}
