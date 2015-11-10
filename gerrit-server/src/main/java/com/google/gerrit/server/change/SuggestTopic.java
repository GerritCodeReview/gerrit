// Copyright (C) 2015 The Android Open Source Project
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

import com.google.common.base.Strings;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwt.dev.util.collect.HashSet;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.Config;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SuggestTopic implements RestReadView<ChangeResource> {
  private static final int DEFAULT_MAX_SUGGESTED = 10;

  private final Provider<ReviewDb> dbProvider;
  private final ChangeControl.GenericFactory changeControlFactory;
  Provider<CurrentUser> currentUser;
  private final int suggestFrom;
  private final boolean suggestTopics;
  private int limit;
  private String query;
  private final int maxSuggestedReviewers;

  @Option(name = "--limit", aliases = {"-n"}, metaVar = "CNT",
      usage = "maximum number of reviewers to list")
  public void setLimit(int l) {
    this.limit =
        l <= 0 ? maxSuggestedReviewers : Math.min(l,
            maxSuggestedReviewers);
  }

  @Option(name = "--query", aliases = {"-q"}, metaVar = "QUERY",
      usage = "match reviewers query")
  public void setQuery(String q) {
    this.query = q;
  }

  @Inject
  SuggestTopic(Provider<CurrentUser> currentUser,
      Provider<ReviewDb> dbProvider,
      @GerritServerConfig Config cfg,
      ChangeControl.GenericFactory changeControlFactory) {
    this.dbProvider = dbProvider;
    this.maxSuggestedReviewers =
        cfg.getInt("suggest", "maxSuggestedReviewers", DEFAULT_MAX_SUGGESTED);
    this.limit = this.maxSuggestedReviewers;
    String suggest = cfg.getString("suggest", null, "topics");
    this.suggestTopics = !("OFF".equalsIgnoreCase(suggest)
        || "false".equalsIgnoreCase(suggest));

    this.suggestFrom = cfg.getInt("suggest", null, "from", 0);
    this.changeControlFactory = changeControlFactory;
    this.currentUser = currentUser;
  }

  @Override
  public List<String> apply(ChangeResource rsrc)
      throws BadRequestException, OrmException, IOException {
    if (Strings.isNullOrEmpty(query)) {
      throw new BadRequestException("missing query field");
    }

    if (!suggestTopics || query.length() < suggestFrom) {
      return Collections.emptyList();
    }

    List<String> suggestedTopics;
    try {
      suggestedTopics = suggestTopics();
    } catch (NoSuchChangeException e) {
      throw new OrmException(e);
    }
    if (suggestedTopics.size() <= limit) {
      return suggestedTopics;
    } else {
      return suggestedTopics.subList(0, limit);
    }
  }

  private List<String> suggestTopics()
      throws OrmException, NoSuchChangeException {
    HashSet<String> set = new HashSet<>();
    ReviewDb db = dbProvider.get();
    CurrentUser user = currentUser.get();
    for (Change c : db.changes().all()) {
      if (changeControlFactory.controlFor(c, user).isVisible(db)) {
        set.add(c.getTopic());
      }
    }

    List<String> ret = new ArrayList<>(set);
    return ret;
  }
}
