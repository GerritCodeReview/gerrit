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

package com.google.gerrit.extensions.api.groups;

import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.GroupAuditEventInfo;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.common.GroupOptionsInfo;
import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.extensions.restapi.RestApiException;
import java.util.Arrays;
import java.util.List;

public interface GroupApi {
  /** Returns group info with no {@code ListGroupsOption}s set. */
  GroupInfo get() throws RestApiException;

  /** Returns group info with all {@code ListGroupsOption}s set. */
  GroupInfo detail() throws RestApiException;

  /** Returns group name. */
  String name() throws RestApiException;

  /**
   * Set group name.
   *
   * @param name new name.
   */
  void name(String name) throws RestApiException;

  /** Delete group. */
  void delete() throws RestApiException;

  /** Returns owning group info. */
  GroupInfo owner() throws RestApiException;

  /**
   * Set group owner.
   *
   * @param owner identifier of new group owner.
   */
  void owner(String owner) throws RestApiException;

  /** Returns group description. */
  String description() throws RestApiException;

  /**
   * Set group decsription.
   *
   * @param description new description.
   */
  void description(String description) throws RestApiException;

  /** Returns group options. */
  GroupOptionsInfo options() throws RestApiException;

  /**
   * Set group options.
   *
   * @param options new options.
   */
  void options(GroupOptionsInfo options) throws RestApiException;

  /**
   * List group members, non-recursively.
   *
   * @return group members.
   */
  List<AccountInfo> members() throws RestApiException;

  /**
   * List group members.
   *
   * @param recursive whether to recursively included groups.
   * @return group members.
   */
  List<AccountInfo> members(boolean recursive) throws RestApiException;

  /**
   * Add members to a group.
   *
   * @param members list of member identifiers, in any format accepted by {@link
   *     com.google.gerrit.extensions.api.accounts.Accounts#id(String)}
   */
  void addMembers(List<String> members) throws RestApiException;

  /**
   * Add members to a group.
   *
   * @param members list of member identifiers, in any format accepted by {@link
   *     com.google.gerrit.extensions.api.accounts.Accounts#id(String)}
   */
  default void addMembers(String... members) throws RestApiException {
    addMembers(Arrays.asList(members));
  }

  /**
   * Remove members from a group.
   *
   * @param members list of member identifiers, in any format accepted by {@link
   *     com.google.gerrit.extensions.api.accounts.Accounts#id(String)}
   */
  void removeMembers(List<String> members) throws RestApiException;

  /**
   * Remove members from a group.
   *
   * @param members list of member identifiers, in any format accepted by {@link
   *     com.google.gerrit.extensions.api.accounts.Accounts#id(String)}
   */
  default void removeMembers(String... members) throws RestApiException {
    removeMembers(Arrays.asList(members));
  }

  /**
   * Lists the subgroups of this group.
   *
   * @return the found subgroups
   */
  List<GroupInfo> includedGroups() throws RestApiException;

  /**
   * Adds subgroups to this group.
   *
   * @param groups list of group identifiers, in any format accepted by {@link Groups#id(String)}
   */
  void addGroups(List<String> groups) throws RestApiException;

  /**
   * Adds subgroups to this group.
   *
   * @param groups list of group identifiers, in any format accepted by {@link Groups#id(String)}
   */
  default void addGroups(String... groups) throws RestApiException {
    addGroups(Arrays.asList(groups));
  }

  /**
   * Removes subgroups from this group.
   *
   * @param groups list of group identifiers, in any format accepted by {@link Groups#id(String)}
   */
  void removeGroups(List<String> groups) throws RestApiException;

  /**
   * Removes subgroups from this group.
   *
   * @param groups list of group identifiers, in any format accepted by {@link Groups#id(String)}
   */
  default void removeGroups(String... groups) throws RestApiException {
    removeGroups(Arrays.asList(groups));
  }

  /**
   * Returns the audit log of the group.
   *
   * @return list of audit events of the group.
   */
  List<? extends GroupAuditEventInfo> auditLog() throws RestApiException;

  /**
   * Reindexes the group.
   *
   * <p>Only supported for internal groups.
   */
  void index() throws RestApiException;

  /**
   * A default implementation which allows source compatibility when adding new methods to the
   * interface.
   */
  class NotImplemented implements GroupApi {
    @Override
    public GroupInfo get() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public GroupInfo detail() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public String name() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void name(String name) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void delete() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public GroupInfo owner() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void owner(String owner) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public String description() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void description(String description) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public GroupOptionsInfo options() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void options(GroupOptionsInfo options) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public List<AccountInfo> members() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public List<AccountInfo> members(boolean recursive) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void addMembers(List<String> members) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void removeMembers(List<String> members) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public List<GroupInfo> includedGroups() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void addGroups(List<String> groups) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void removeGroups(List<String> groups) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public List<? extends GroupAuditEventInfo> auditLog() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void index() throws RestApiException {
      throw new NotImplementedException();
    }
  }
}
