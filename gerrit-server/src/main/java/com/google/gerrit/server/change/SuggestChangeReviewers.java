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

import com.google.gerrit.extensions.common.AccountVisibility;
import com.google.gerrit.extensions.common.SuggestedReviewerInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.IdentifiedUser.GenericFactory;
import com.google.gerrit.server.ReviewersUtil;
import com.google.gerrit.server.ReviewersUtil.VisibilityControl;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;
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

  private final PermissionBackend permissionBackend;
  private final Provider<CurrentUser> self;
  private final ProjectCache projectCache;

  @Inject
  SuggestChangeReviewers(
      AccountVisibility av,
      GenericFactory identifiedUserFactory,
      Provider<ReviewDb> dbProvider,
      PermissionBackend permissionBackend,
      Provider<CurrentUser> self,
      @GerritServerConfig Config cfg,
      ReviewersUtil reviewersUtil,
      ProjectCache projectCache) {
    super(av, identifiedUserFactory, dbProvider, cfg, reviewersUtil);
    this.permissionBackend = permissionBackend;
    this.self = self;
    this.projectCache = projectCache;
  }

  @Override
  public List<SuggestedReviewerInfo> apply(ChangeResource rsrc)
      throws AuthException, BadRequestException, OrmException, IOException, ConfigInvalidException {
    if (!self.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }
    return reviewersUtil.suggestReviewers(
        rsrc.getNotes(),
        this,
        projectCache.checkedGet(rsrc.getProject()),
        getVisibility(rsrc),
        excludeGroups);
  }

  private VisibilityControl getVisibility(ChangeResource rsrc) {
    // Use the destination reference, not the change, as drafts may deny
    // anyone who is not already a reviewer.
    // TODO(hiesel) Replace this with a check on the change resource once support for drafts was
    // removed
    PermissionBackend.ForRef perm = permissionBackend.user(self).ref(rsrc.getChange().getDest());
    return new VisibilityControl() {
      @Override
      public boolean isVisibleTo(Account.Id account) throws OrmException {
        IdentifiedUser who = identifiedUserFactory.create(account);
        return perm.user(who).testOrFalse(RefPermission.READ);
      }
    };
  }
}
