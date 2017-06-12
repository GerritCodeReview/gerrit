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

import com.google.gerrit.extensions.common.SuggestedReviewerInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.IdentifiedUser.GenericFactory;
import com.google.gerrit.server.ReviewersUtil;
import com.google.gerrit.server.ReviewersUtil.VisibilityControl;
import com.google.gerrit.server.account.AccountVisibility;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.lib.Config;
import org.kohsuke.args4j.Option;

public class SuggestChangeReviewers extends SuggestReviewers
    implements RestReadView<ChangeResource> {

  @Option(
    name = "--exclude-groups",
    aliases = {"-e"},
    usage = "exclude groups from query"
  )
  boolean excludeGroups;

  @Inject
  SuggestChangeReviewers(
      AccountVisibility av,
      GenericFactory identifiedUserFactory,
      Provider<ReviewDb> dbProvider,
      @GerritServerConfig Config cfg,
      ReviewersUtil reviewersUtil) {
    super(av, identifiedUserFactory, dbProvider, cfg, reviewersUtil);
  }

  @Override
  public List<SuggestedReviewerInfo> apply(ChangeResource rsrc)
      throws BadRequestException, OrmException, IOException {
    return reviewersUtil.suggestReviewers(
        rsrc.getNotes(),
        this,
        rsrc.getControl().getProjectControl(),
        getVisibility(rsrc),
        excludeGroups);
  }

  private VisibilityControl getVisibility(final ChangeResource rsrc) {
    if (rsrc.getControl().getRefControl().isVisibleByRegisteredUsers()) {
      return new VisibilityControl() {
        @Override
        public boolean isVisibleTo(Account.Id account) throws OrmException {
          return true;
        }
      };
    }
    return new VisibilityControl() {
      @Override
      public boolean isVisibleTo(Account.Id account) throws OrmException {
        IdentifiedUser who = identifiedUserFactory.create(account);
        // we can't use changeControl directly as it won't suggest reviewers
        // to drafts
        return rsrc.getControl().forUser(who).isRefVisible();
      }
    };
  }
}
