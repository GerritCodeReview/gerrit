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

import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.extensions.api.groups.GroupApi;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.GroupAuditEventInfo;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.common.GroupOptionsInfo;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.group.AddIncludedGroups;
import com.google.gerrit.server.group.AddMembers;
import com.google.gerrit.server.group.DeleteIncludedGroups;
import com.google.gerrit.server.group.DeleteMembers;
import com.google.gerrit.server.group.GetAuditLog;
import com.google.gerrit.server.group.GetDescription;
import com.google.gerrit.server.group.GetDetail;
import com.google.gerrit.server.group.GetGroup;
import com.google.gerrit.server.group.GetName;
import com.google.gerrit.server.group.GetOptions;
import com.google.gerrit.server.group.GetOwner;
import com.google.gerrit.server.group.GroupResource;
import com.google.gerrit.server.group.ListIncludedGroups;
import com.google.gerrit.server.group.ListMembers;
import com.google.gerrit.server.group.PutDescription;
import com.google.gerrit.server.group.PutName;
import com.google.gerrit.server.group.PutOptions;
import com.google.gerrit.server.group.PutOwner;
import com.google.gwtorm.server.OrmException;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

class GroupApiImpl implements GroupApi {
  interface Factory {
    GroupApiImpl create(GroupResource rsrc);
  }

  private final GetGroup getGroup;
  private final GetDetail getDetail;
  private final GetName getName;
  private final PutName putName;
  private final GetOwner getOwner;
  private final PutOwner putOwner;
  private final GetDescription getDescription;
  private final PutDescription putDescription;
  private final GetOptions getOptions;
  private final PutOptions putOptions;
  private final ListMembers listMembers;
  private final AddMembers addMembers;
  private final DeleteMembers deleteMembers;
  private final ListIncludedGroups listGroups;
  private final AddIncludedGroups addGroups;
  private final DeleteIncludedGroups deleteGroups;
  private final GetAuditLog getAuditLog;
  private final GroupResource rsrc;

  @AssistedInject
  GroupApiImpl(
      GetGroup getGroup,
      GetDetail getDetail,
      GetName getName,
      PutName putName,
      GetOwner getOwner,
      PutOwner putOwner,
      GetDescription getDescription,
      PutDescription putDescription,
      GetOptions getOptions,
      PutOptions putOptions,
      ListMembers listMembers,
      AddMembers addMembers,
      DeleteMembers deleteMembers,
      ListIncludedGroups listGroups,
      AddIncludedGroups addGroups,
      DeleteIncludedGroups deleteGroups,
      GetAuditLog getAuditLog,
      @Assisted GroupResource rsrc) {
    this.getGroup = getGroup;
    this.getDetail = getDetail;
    this.getName = getName;
    this.putName = putName;
    this.getOwner = getOwner;
    this.putOwner = putOwner;
    this.getDescription = getDescription;
    this.putDescription = putDescription;
    this.getOptions = getOptions;
    this.putOptions = putOptions;
    this.listMembers = listMembers;
    this.addMembers = addMembers;
    this.deleteMembers = deleteMembers;
    this.listGroups = listGroups;
    this.addGroups = addGroups;
    this.deleteGroups = deleteGroups;
    this.getAuditLog = getAuditLog;
    this.rsrc = rsrc;
  }

  @Override
  public GroupInfo get() throws RestApiException {
    try {
      return getGroup.apply(rsrc);
    } catch (OrmException e) {
      throw new RestApiException("Cannot retrieve group", e);
    }
  }

  @Override
  public GroupInfo detail() throws RestApiException {
    try {
      return getDetail.apply(rsrc);
    } catch (OrmException e) {
      throw new RestApiException("Cannot retrieve group", e);
    }
  }

  @Override
  public String name() throws RestApiException {
    return getName.apply(rsrc);
  }

  @Override
  public void name(String name) throws RestApiException {
    PutName.Input in = new PutName.Input();
    in.name = name;
    try {
      putName.apply(rsrc, in);
    } catch (NoSuchGroupException e) {
      throw new ResourceNotFoundException(name, e);
    } catch (OrmException e) {
      throw new RestApiException("Cannot put group name", e);
    }
  }

  @Override
  public GroupInfo owner() throws RestApiException {
    try {
      return getOwner.apply(rsrc);
    } catch (OrmException e) {
      throw new RestApiException("Cannot get group owner", e);
    }
  }

  @Override
  public void owner(String owner) throws RestApiException {
    PutOwner.Input in = new PutOwner.Input();
    in.owner = owner;
    try {
      putOwner.apply(rsrc, in);
    } catch (OrmException e) {
      throw new RestApiException("Cannot put group owner", e);
    }
  }

  @Override
  public String description() throws RestApiException {
    return getDescription.apply(rsrc);
  }

  @Override
  public void description(String description) throws RestApiException {
    PutDescription.Input in = new PutDescription.Input();
    in.description = description;
    try {
      putDescription.apply(rsrc, in);
    } catch (OrmException e) {
      throw new RestApiException("Cannot put group description", e);
    }
  }

  @Override
  public GroupOptionsInfo options() throws RestApiException {
    return getOptions.apply(rsrc);
  }

  @Override
  public void options(GroupOptionsInfo options) throws RestApiException {
    try {
      putOptions.apply(rsrc, options);
    } catch (OrmException e) {
      throw new RestApiException("Cannot put group options", e);
    }
  }

  @Override
  public List<AccountInfo> members() throws RestApiException {
    return members(false);
  }

  @Override
  public List<AccountInfo> members(boolean recursive) throws RestApiException {
    listMembers.setRecursive(recursive);
    try {
      return listMembers.apply(rsrc);
    } catch (OrmException e) {
      throw new RestApiException("Cannot list group members", e);
    }
  }

  @Override
  public void addMembers(String... members) throws RestApiException {
    try {
      addMembers.apply(rsrc, AddMembers.Input.fromMembers(Arrays.asList(members)));
    } catch (OrmException | IOException e) {
      throw new RestApiException("Cannot add group members", e);
    }
  }

  @Override
  public void removeMembers(String... members) throws RestApiException {
    try {
      deleteMembers.apply(rsrc, AddMembers.Input.fromMembers(Arrays.asList(members)));
    } catch (OrmException | IOException e) {
      throw new RestApiException("Cannot remove group members", e);
    }
  }

  @Override
  public List<GroupInfo> includedGroups() throws RestApiException {
    try {
      return listGroups.apply(rsrc);
    } catch (OrmException e) {
      throw new RestApiException("Cannot list included groups", e);
    }
  }

  @Override
  public void addGroups(String... groups) throws RestApiException {
    try {
      addGroups.apply(rsrc, AddIncludedGroups.Input.fromGroups(Arrays.asList(groups)));
    } catch (OrmException e) {
      throw new RestApiException("Cannot add group members", e);
    }
  }

  @Override
  public void removeGroups(String... groups) throws RestApiException {
    try {
      deleteGroups.apply(rsrc, AddIncludedGroups.Input.fromGroups(Arrays.asList(groups)));
    } catch (OrmException e) {
      throw new RestApiException("Cannot remove group members", e);
    }
  }

  @Override
  public List<? extends GroupAuditEventInfo> auditLog() throws RestApiException {
    try {
      return getAuditLog.apply(rsrc);
    } catch (OrmException e) {
      throw new RestApiException("Cannot get audit log", e);
    }
  }
}
