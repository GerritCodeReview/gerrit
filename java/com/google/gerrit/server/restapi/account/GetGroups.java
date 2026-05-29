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

package com.google.gerrit.server.restapi.account;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.exceptions.NoSuchGroupException;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.restapi.group.GroupJson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * REST endpoint to get all known groups of an account (groups that contain the account as member).
 *
 * <p>This REST endpoint handles {@code GET /accounts/<account-identifier>/groups} requests.
 *
 * <p>The response may not contain all groups of the account as not all groups may be known (see
 * {@link com.google.gerrit.server.account.GroupMembership#getKnownGroups()}). In addition groups
 * that are not visible to the calling user are filtered out.
 */
@Singleton
public class GetGroups implements RestReadView<AccountResource> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final GroupControl.Factory groupControlFactory;
  private final GroupJson json;

  @Inject
  GetGroups(GroupControl.Factory groupControlFactory, GroupJson json) {
    this.groupControlFactory = groupControlFactory;
    this.json = json;
  }

  @Override
  public Response<List<GroupInfo>> apply(AccountResource resource)
      throws PermissionBackendException {
    IdentifiedUser user = resource.getUser();
    Account.Id userId = user.getAccountId();
    Set<AccountGroup.UUID> knownGroups = user.getEffectiveGroups().getKnownGroups();
    List<GroupInfo> visibleGroups = new ArrayList<>();
    for (AccountGroup.UUID uuid : knownGroups) {
      GroupControl ctl;
      try {
        ctl = groupControlFactory.controlFor(uuid);
      } catch (NoSuchGroupException e) {
        logger.atFine().log("skipping non-existing group %s", uuid);
        continue;
      }

      if (!ctl.isVisible()) {
        logger.atFine().log("skipping non-visible group %s", uuid);
        continue;
      }

      if (!ctl.canSeeMember(userId)) {
        logger.atFine().log(
            "skipping group %s because member %d cannot be seen", uuid, userId.get());
        continue;
      }

      visibleGroups.add(json.format(ctl.getGroup()));
    }
    return Response.ok(visibleGroups);
  }
}
