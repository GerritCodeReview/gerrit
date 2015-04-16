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
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.common.GroupOptionsInfo;
import com.google.gerrit.extensions.restapi.RestApiException;

import java.util.List;

public interface GroupApi {
  /** @return group info with no {@code ListGroupsOption}s set. */
  GroupInfo get() throws RestApiException;

  /** @return group info with all {@code ListGroupsOption}s set. */
  GroupInfo detail() throws RestApiException;

  /** @return group name. */
  String name() throws RestApiException;

  /**
   * Set group name.
   *
   * @param name new name.
   * @throws RestApiException
   */
  void name(String name) throws RestApiException;

  /** @return owning group info. */
  GroupInfo owner() throws RestApiException;

  /**
   * Set group owner.
   *
   * @param owner identifier of new group owner.
   * @throws RestApiException
   */
  void owner(String owner) throws RestApiException;

  /** @return group description. */
  String description() throws RestApiException;

  /**
   * Set group decsription.
   *
   * @param description new description.
   * @throws RestApiException
   */
  void description(String description) throws RestApiException;

  /** @return group options. */
  GroupOptionsInfo options() throws RestApiException;

  /**
   * Set group options.
   *
   * @param options new options.
   * @throws RestApiException
   */
  void options(GroupOptionsInfo options) throws RestApiException;

  /**
   * List group members, non-recursively.
   *
   * @return group members.
   * @throws RestApiException
   */
  List<AccountInfo> members() throws RestApiException;

  /**
   * List group members.
   *
   * @param recursive whether to recursively included groups.
   * @return group members.
   * @throws RestApiException
   */
  List<AccountInfo> members(boolean recursive) throws RestApiException;

  /**
   * Add members to a group.
   *
   * @param members list of member identifiers, in any format accepted by
   *     {@link com.google.gerrit.extensions.api.accounts.Accounts#id(String)}
   * @throws RestApiException
   */
  void addMembers(String... members) throws RestApiException;

  /**
   * Remove members from a group.
   *
   * @param members list of member identifiers, in any format accepted by
   *     {@link com.google.gerrit.extensions.api.accounts.Accounts#id(String)}
   * @throws RestApiException
   */
  void removeMembers(String... members) throws RestApiException;

  /**
   * List included groups.
   *
   * @return included groups.
   * @throws RestApiException
   */
  List<GroupInfo> includedGroups() throws RestApiException;

  /**
   * Add groups to be included in this one.
   *
   * @param groups list of group identifiers, in any format accepted by
   *     {@link Groups#id(String)}
   * @throws RestApiException
   */
  void addGroups(String... groups) throws RestApiException;

  /**
   * Remove included groups from this one.
   *
   * @param groups list of group identifiers, in any format accepted by
   *     {@link Groups#id(String)}
   * @throws RestApiException
   */
  void removeGroups(String... groups) throws RestApiException;
}
