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

package com.google.gerrit.server.restapi.group;

import com.google.common.collect.ListMultimap;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.NeedsParams;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestCollection;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.group.GroupResolver;
import com.google.gerrit.server.group.GroupResource;
import com.google.inject.Inject;
import com.google.inject.Provider;

public class GroupsCollection
    implements RestCollection<TopLevelResource, GroupResource>, NeedsParams {
  private final DynamicMap<RestView<GroupResource>> views;
  private final Provider<ListGroups> list;
  private final Provider<QueryGroups> queryGroups;
  private final GroupControl.Factory groupControlFactory;
  private final GroupResolver groupResolver;
  private final Provider<CurrentUser> self;

  private boolean hasQuery2;

  @Inject
  public GroupsCollection(
      DynamicMap<RestView<GroupResource>> views,
      Provider<ListGroups> list,
      Provider<QueryGroups> queryGroups,
      GroupControl.Factory groupControlFactory,
      GroupResolver groupResolver,
      Provider<CurrentUser> self) {
    this.views = views;
    this.list = list;
    this.queryGroups = queryGroups;
    this.groupControlFactory = groupControlFactory;
    this.groupResolver = groupResolver;
    this.self = self;
  }

  @Override
  public void setParams(ListMultimap<String, String> params) throws BadRequestException {
    if (params.containsKey("query") && params.containsKey("query2")) {
      throw new BadRequestException("\"query\" and \"query2\" options are mutually exclusive");
    }

    // The --query2 option is defined in QueryGroups
    this.hasQuery2 = params.containsKey("query2");
  }

  @Override
  public RestView<TopLevelResource> list() throws ResourceNotFoundException, AuthException {
    final CurrentUser user = self.get();
    if (user instanceof AnonymousUser) {
      throw new AuthException("Authentication required");
    } else if (!(user.isIdentifiedUser())) {
      throw new ResourceNotFoundException();
    }

    if (hasQuery2) {
      return queryGroups.get();
    }

    return list.get();
  }

  @Override
  public GroupResource parse(TopLevelResource parent, IdString id)
      throws AuthException, ResourceNotFoundException {
    final CurrentUser user = self.get();
    if (user instanceof AnonymousUser) {
      throw new AuthException("Authentication required");
    } else if (!(user.isIdentifiedUser())) {
      throw new ResourceNotFoundException(id);
    }

    GroupDescription.Basic group = groupResolver.parseId(id.get());
    if (group == null) {
      throw new ResourceNotFoundException(id.get());
    }
    GroupControl ctl = groupControlFactory.controlFor(group);
    if (!ctl.isVisible()) {
      throw new ResourceNotFoundException(id);
    }
    return new GroupResource(ctl);
  }

  @Override
  public DynamicMap<RestView<GroupResource>> views() {
    return views;
  }
}
