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

package com.google.gerrit.server.group;

import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.data.GroupDescriptions;
import com.google.gerrit.common.errors.NameAlreadyUsedException;
import com.google.gerrit.common.errors.PermissionDeniedException;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.PerformCreateGroup;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.group.CreateGroup.Input;
import com.google.gerrit.server.group.GroupJson.GroupInfo;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.lib.Config;

import java.util.Collections;

@RequiresCapability(GlobalCapability.CREATE_GROUP)
class CreateGroup implements RestModifyView<TopLevelResource, Input> {
  static class Input {
    String name;
    String description;
    Boolean visibleToAll;
    String ownerId;
  }

  static interface Factory {
    CreateGroup create(@Assisted String name);
  }

  private final Provider<IdentifiedUser> self;
  private final GroupsCollection groups;
  private final PerformCreateGroup.Factory op;
  private final GroupJson json;
  private final boolean defaultVisibleToAll;
  private final String name;

  @Inject
  CreateGroup(Provider<IdentifiedUser> self, GroupsCollection groups,
      PerformCreateGroup.Factory performCreateGroupFactory, GroupJson json,
      @GerritServerConfig Config cfg, @Assisted String name) {
    this.self = self;
    this.groups = groups;
    this.op = performCreateGroupFactory;
    this.json = json;
    this.defaultVisibleToAll = cfg.getBoolean("groups", "newGroupsVisibleToAll", false);
    this.name = name;
  }

  @Override
  public GroupInfo apply(TopLevelResource resource, Input input)
      throws AuthException, BadRequestException, UnprocessableEntityException,
      NameAlreadyUsedException, OrmException {
    if (input == null) {
      input = new Input();
    }
    if (input.name != null && !name.equals(input.name)) {
      throw new BadRequestException("name must match URL");
    }

    AccountGroup.Id ownerId = owner(input);
    AccountGroup group;
    try {
      group = op.create().createGroup(
          name,
          Strings.emptyToNull(input.description),
          Objects.firstNonNull(input.visibleToAll, defaultVisibleToAll),
          ownerId,
          ownerId == null
            ? Collections.singleton(self.get().getAccountId())
            : Collections.<Account.Id> emptySet(),
          null);
    } catch (PermissionDeniedException e) {
      throw new AuthException(e.getMessage());
    }
    return json.format(GroupDescriptions.forAccountGroup(group));
  }

  private AccountGroup.Id owner(Input input)
      throws UnprocessableEntityException {
    if (input.ownerId != null) {
      GroupDescription.Basic d = groups.parseInternal(Url.decode(input.ownerId));
      return GroupDescriptions.toAccountGroup(d).getId();
    }
    return null;
  }
}
