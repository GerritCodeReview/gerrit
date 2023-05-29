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

import com.google.gerrit.extensions.api.groups.GroupApi;
import com.google.gerrit.extensions.api.groups.OwnerInput;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.DeleteGroupInput;
import com.google.gerrit.extensions.common.DescriptionInput;
import com.google.gerrit.extensions.common.GroupAuditEventInfo;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.common.GroupOptionsInfo;
import com.google.gerrit.extensions.common.Input;
import com.google.gerrit.extensions.common.NameInput;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.group.GroupResource;
import com.google.gerrit.server.restapi.group.AddMembers;
import com.google.gerrit.server.restapi.group.AddSubgroups;
import com.google.gerrit.server.restapi.group.DeleteGroup;
import com.google.gerrit.server.restapi.group.DeleteMembers;
import com.google.gerrit.server.restapi.group.DeleteSubgroups;
import com.google.gerrit.server.restapi.group.GetAuditLog;
import com.google.gerrit.server.restapi.group.GetDescription;
import com.google.gerrit.server.restapi.group.GetDetail;
import com.google.gerrit.server.restapi.group.GetGroup;
import com.google.gerrit.server.restapi.group.GetName;
import com.google.gerrit.server.restapi.group.GetOptions;
import com.google.gerrit.server.restapi.group.GetOwner;
import com.google.gerrit.server.restapi.group.Index;
import com.google.gerrit.server.restapi.group.ListMembers;
import com.google.gerrit.server.restapi.group.ListSubgroups;
import com.google.gerrit.server.restapi.group.PutDescription;
import com.google.gerrit.server.restapi.group.PutName;
import com.google.gerrit.server.restapi.group.PutOptions;
import com.google.gerrit.server.restapi.group.PutOwner;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.List;

class GroupApiImpl implements GroupApi {
  interface Factory {
    GroupApiImpl create(GroupResource rsrc);
  }

  private final GetGroup getGroup;
  private final GetDetail getDetail;
  private final GetName getName;
  private final PutName putName;
  private final DeleteGroup deleteGroup;
  private final GetOwner getOwner;
  private final PutOwner putOwner;
  private final GetDescription getDescription;
  private final PutDescription putDescription;
  private final GetOptions getOptions;
  private final PutOptions putOptions;
  private final ListMembers listMembers;
  private final AddMembers addMembers;
  private final DeleteMembers deleteMembers;
  private final ListSubgroups listSubgroups;
  private final AddSubgroups addSubgroups;
  private final DeleteSubgroups deleteSubgroups;
  private final GetAuditLog getAuditLog;
  private final GroupResource rsrc;
  private final Index index;

  @Inject
  GroupApiImpl(
      GetGroup getGroup,
      GetDetail getDetail,
      GetName getName,
      PutName putName,
      DeleteGroup deleteGroup,
      GetOwner getOwner,
      PutOwner putOwner,
      GetDescription getDescription,
      PutDescription putDescription,
      GetOptions getOptions,
      PutOptions putOptions,
      ListMembers listMembers,
      AddMembers addMembers,
      DeleteMembers deleteMembers,
      ListSubgroups listSubgroups,
      AddSubgroups addSubgroups,
      DeleteSubgroups deleteSubgroups,
      GetAuditLog getAuditLog,
      Index index,
      @Assisted GroupResource rsrc) {
    this.getGroup = getGroup;
    this.getDetail = getDetail;
    this.getName = getName;
    this.putName = putName;
    this.deleteGroup = deleteGroup;
    this.getOwner = getOwner;
    this.putOwner = putOwner;
    this.getDescription = getDescription;
    this.putDescription = putDescription;
    this.getOptions = getOptions;
    this.putOptions = putOptions;
    this.listMembers = listMembers;
    this.addMembers = addMembers;
    this.deleteMembers = deleteMembers;
    this.listSubgroups = listSubgroups;
    this.addSubgroups = addSubgroups;
    this.deleteSubgroups = deleteSubgroups;
    this.getAuditLog = getAuditLog;
    this.index = index;
    this.rsrc = rsrc;
  }

  @Override
  public GroupInfo get() throws RestApiException {
    try {
      return getGroup.apply(rsrc).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot retrieve group", e);
    }
  }

  @Override
  public GroupInfo detail() throws RestApiException {
    try {
      return getDetail.apply(rsrc).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot retrieve group", e);
    }
  }

  @Override
  public String name() throws RestApiException {
    try {
      return getName.apply(rsrc).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot get group name", e);
    }
  }

  @Override
  public void name(String name) throws RestApiException {
    NameInput in = new NameInput();
    in.name = name;
    try {
      @SuppressWarnings("unused")
      var unused = putName.apply(rsrc, in);
    } catch (Exception e) {
      throw asRestApiException("Cannot put group name", e);
    }
  }

  @Override
  public void delete() throws RestApiException {
    DeleteGroupInput in = new DeleteGroupInput();
    try {
      @SuppressWarnings("unused")
      var unused = deleteGroup.apply(rsrc, in);
    } catch (Exception e) {
      throw asRestApiException("Cannot delete group", e);
    }
  }

  @Override
  public GroupInfo owner() throws RestApiException {
    try {
      return getOwner.apply(rsrc).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot get group owner", e);
    }
  }

  @Override
  public void owner(String owner) throws RestApiException {
    OwnerInput in = new OwnerInput();
    in.owner = owner;
    try {
      @SuppressWarnings("unused")
      var unused = putOwner.apply(rsrc, in);
    } catch (Exception e) {
      throw asRestApiException("Cannot put group owner", e);
    }
  }

  @Override
  public String description() throws RestApiException {
    try {
      return getDescription.apply(rsrc).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot get group description", e);
    }
  }

  @Override
  public void description(String description) throws RestApiException {
    DescriptionInput in = new DescriptionInput();
    in.description = description;
    try {
      @SuppressWarnings("unused")
      var unused = putDescription.apply(rsrc, in);
    } catch (Exception e) {
      throw asRestApiException("Cannot put group description", e);
    }
  }

  @Override
  public GroupOptionsInfo options() throws RestApiException {
    try {
      return getOptions.apply(rsrc).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot get group options", e);
    }
  }

  @Override
  public void options(GroupOptionsInfo options) throws RestApiException {
    try {
      @SuppressWarnings("unused")
      var unused = putOptions.apply(rsrc, options);
    } catch (Exception e) {
      throw asRestApiException("Cannot put group options", e);
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
      return listMembers.apply(rsrc).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot list group members", e);
    }
  }

  @Override
  public void addMembers(List<String> members) throws RestApiException {
    try {
      @SuppressWarnings("unused")
      var unused = addMembers.apply(rsrc, AddMembers.Input.fromMembers(members));
    } catch (Exception e) {
      throw asRestApiException("Cannot add group members", e);
    }
  }

  @Override
  public void removeMembers(List<String> members) throws RestApiException {
    try {
      @SuppressWarnings("unused")
      var unused = deleteMembers.apply(rsrc, AddMembers.Input.fromMembers(members));
    } catch (Exception e) {
      throw asRestApiException("Cannot remove group members", e);
    }
  }

  @Override
  public List<GroupInfo> includedGroups() throws RestApiException {
    try {
      return listSubgroups.apply(rsrc).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot list subgroups", e);
    }
  }

  @Override
  public void addGroups(List<String> groups) throws RestApiException {
    try {
      @SuppressWarnings("unused")
      var unused = addSubgroups.apply(rsrc, AddSubgroups.Input.fromGroups(groups));
    } catch (Exception e) {
      throw asRestApiException("Cannot add subgroups", e);
    }
  }

  @Override
  public void removeGroups(List<String> groups) throws RestApiException {
    try {
      @SuppressWarnings("unused")
      var unused = deleteSubgroups.apply(rsrc, AddSubgroups.Input.fromGroups(groups));
    } catch (Exception e) {
      throw asRestApiException("Cannot remove subgroups", e);
    }
  }

  @Override
  public List<? extends GroupAuditEventInfo> auditLog() throws RestApiException {
    try {
      return getAuditLog.apply(rsrc).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot get audit log", e);
    }
  }

  @Override
  public void index() throws RestApiException {
    try {
      @SuppressWarnings("unused")
      var unused = index.apply(rsrc, new Input());
    } catch (Exception e) {
      throw asRestApiException("Cannot index group", e);
    }
  }
}
