// Copyright (C) 2013 The Android Open Source Project
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

import com.google.gerrit.config.GerritServerConfig;
import com.google.gerrit.extensions.common.AccountVisibility;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.inject.Inject;
import com.google.inject.Provider;
import org.eclipse.jgit.lib.Config;
import org.kohsuke.args4j.Option;

public class SuggestReviewers {
  private static final int DEFAULT_MAX_SUGGESTED = 10;

  protected final Provider<ReviewDb> dbProvider;
  protected final IdentifiedUser.GenericFactory identifiedUserFactory;
  protected final ReviewersUtil reviewersUtil;

  private final boolean suggestAccounts;
  private final int maxAllowed;
  private final int maxAllowedWithoutConfirmation;
  protected int limit;
  protected String query;
  protected final int maxSuggestedReviewers;

  @Option(
    name = "--limit",
    aliases = {"-n"},
    metaVar = "CNT",
    usage = "maximum number of reviewers to list"
  )
  public void setLimit(int l) {
    this.limit = l <= 0 ? maxSuggestedReviewers : Math.min(l, maxSuggestedReviewers);
  }

  @Option(
    name = "--query",
    aliases = {"-q"},
    metaVar = "QUERY",
    usage = "match reviewers query"
  )
  public void setQuery(String q) {
    this.query = q;
  }

  public String getQuery() {
    return query;
  }

  public boolean getSuggestAccounts() {
    return suggestAccounts;
  }

  public int getLimit() {
    return limit;
  }

  public int getMaxAllowed() {
    return maxAllowed;
  }

  public int getMaxAllowedWithoutConfirmation() {
    return maxAllowedWithoutConfirmation;
  }

  @Inject
  public SuggestReviewers(
      AccountVisibility av,
      IdentifiedUser.GenericFactory identifiedUserFactory,
      Provider<ReviewDb> dbProvider,
      @GerritServerConfig Config cfg,
      ReviewersUtil reviewersUtil) {
    this.dbProvider = dbProvider;
    this.identifiedUserFactory = identifiedUserFactory;
    this.reviewersUtil = reviewersUtil;
    this.maxSuggestedReviewers =
        cfg.getInt("suggest", "maxSuggestedReviewers", DEFAULT_MAX_SUGGESTED);
    this.limit = this.maxSuggestedReviewers;
    String suggest = cfg.getString("suggest", null, "accounts");
    if ("OFF".equalsIgnoreCase(suggest) || "false".equalsIgnoreCase(suggest)) {
      this.suggestAccounts = false;
    } else {
      this.suggestAccounts = (av != AccountVisibility.NONE);
    }

    this.maxAllowed = cfg.getInt("addreviewer", "maxAllowed", PostReviewers.DEFAULT_MAX_REVIEWERS);
    this.maxAllowedWithoutConfirmation =
        cfg.getInt(
            "addreviewer",
            "maxWithoutConfirmation",
            PostReviewers.DEFAULT_MAX_REVIEWERS_WITHOUT_CHECK);
  }
}
