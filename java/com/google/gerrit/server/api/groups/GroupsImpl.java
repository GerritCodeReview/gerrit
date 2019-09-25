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

package com.google.gerrit.server.api.groups;

import static com.google.gerrit.server.api.ApiUtil.asRestApiException;
import static java.util.Objects.requireNonNull;

import com.google.gerrit.extensions.api.groups.GroupApi;
import com.google.gerrit.extensions.api.groups.GroupInput;
import com.google.gerrit.extensions.api.groups.Groups;
import com.google.gerrit.extensions.client.ListGroupsOption;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.group.GroupResolver;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.restapi.group.CreateGroup;
import com.google.gerrit.server.restapi.group.GroupsCollection;
import com.google.gerrit.server.restapi.group.ListGroups;
import com.google.gerrit.server.restapi.group.QueryGroups;
import com.google.gerrit.server.restapi.project.ProjectsCollection;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.List;
import java.util.SortedMap;

@Singleton
class GroupsImpl implements Groups {
  private final AccountResolver accountResolver;
  private final GroupsCollection groups;
  private final GroupResolver groupResolver;
  private final ProjectsCollection projects;
  private final Provider<ListGroups> listGroups;
  private final Provider<QueryGroups> queryGroups;
  private final PermissionBackend permissionBackend;
  private final CreateGroup createGroup;
  private final GroupApiImpl.Factory api;

  @Inject
  GroupsImpl(
      AccountResolver accountResolver,
      GroupsCollection groups,
      GroupResolver groupResolver,
      ProjectsCollection projects,
      Provider<ListGroups> listGroups,
      Provider<QueryGroups> queryGroups,
      PermissionBackend permissionBackend,
      CreateGroup createGroup,
      GroupApiImpl.Factory api) {
    this.accountResolver = accountResolver;
    this.groups = groups;
    this.groupResolver = groupResolver;
    this.projects = projects;
    this.listGroups = listGroups;
    this.queryGroups = queryGroups;
    this.permissionBackend = permissionBackend;
    this.createGroup = createGroup;
    this.api = api;
  }

  @Override
  public GroupApi id(String id) throws RestApiException {
    return api.create(groups.parse(TopLevelResource.INSTANCE, IdString.fromDecoded(id)));
  }

  @Override
  public GroupApi create(String name) throws RestApiException {
    GroupInput in = new GroupInput();
    in.name = name;
    return create(in);
  }

  @Override
  public GroupApi create(GroupInput in) throws RestApiException {
    if (requireNonNull(in, "GroupInput").name == null) {
      throw new BadRequestException("GroupInput must specify name");
    }
    try {
      permissionBackend
          .currentUser()
          .checkAny(GlobalPermission.fromAnnotation(createGroup.getClass()));
      GroupInfo info =
          createGroup.apply(TopLevelResource.INSTANCE, IdString.fromDecoded(in.name), in).value();
      return id(info.id);
    } catch (Exception e) {
      throw asRestApiException("Cannot create group " + in.name, e);
    }
  }

  @Override
  public ListRequest list() {
    return new ListRequest() {
      @Override
      public SortedMap<String, GroupInfo> getAsMap() throws RestApiException {
        return list(this);
      }
    };
  }

  private SortedMap<String, GroupInfo> list(ListRequest req) throws RestApiException {
    TopLevelResource tlr = TopLevelResource.INSTANCE;
    ListGroups list = listGroups.get();
    list.setOptions(req.getOptions());

    for (String project : req.getProjects()) {
      try {
        ProjectResource rsrc = projects.parse(tlr, IdString.fromDecoded(project));
        list.addProject(rsrc.getProjectState());
      } catch (Exception e) {
        throw asRestApiException("Error looking up project " + project, e);
      }
    }

    for (String group : req.getGroups()) {
      list.addGroup(groupResolver.parse(group).getGroupUUID());
    }

    list.setVisibleToAll(req.getVisibleToAll());

    if (req.getOwnedBy() != null) {
      list.setOwnedBy(req.getOwnedBy());
    }

    if (req.getUser() != null) {
      try {
        list.setUser(accountResolver.resolve(req.getUser()).asUnique().account().id());
      } catch (Exception e) {
        throw asRestApiException("Error looking up user " + req.getUser(), e);
      }
    }

    list.setOwned(req.getOwned());
    list.setLimit(req.getLimit());
    list.setStart(req.getStart());
    list.setMatchSubstring(req.getSubstring());
    list.setMatchRegex(req.getRegex());
    list.setSuggest(req.getSuggest());
    try {
      return list.apply(tlr).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot list groups", e);
    }
  }

  @Override
  public QueryRequest query() {
    return new QueryRequest() {
      @Override
      public List<GroupInfo> get() throws RestApiException {
        return GroupsImpl.this.query(this);
      }
    };
  }

  @Override
  public QueryRequest query(String query) {
    return query().withQuery(query);
  }

  private List<GroupInfo> query(QueryRequest r) throws RestApiException {
    try {
      QueryGroups myQueryGroups = queryGroups.get();
      myQueryGroups.setQuery(r.getQuery());
      myQueryGroups.setLimit(r.getLimit());
      myQueryGroups.setStart(r.getStart());
      for (ListGroupsOption option : r.getOptions()) {
        myQueryGroups.addOption(option);
      }
      return myQueryGroups.apply(TopLevelResource.INSTANCE).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot query groups", e);
    }
  }
}
