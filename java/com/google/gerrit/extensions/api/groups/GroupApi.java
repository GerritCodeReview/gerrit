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
import java.util.List;

public interface GroupApi {
  /** @return account info with no {@code ListGroupsOption}s set. */
  GroupInfo get() throws RestApiException;

  /** @return account info with all {@code ListGroupsOption}s set. */
  GroupInfo detail() throws RestApiException;

  /** @return account name. */
  String name() throws RestApiException;

  /**
   * Set account name.
   *
   * @param name new name.
   * @throws RestApiException
   */
  void name(String name) throws RestApiException;

  /** @return owning account info. */
  GroupInfo owner() throws RestApiException;

  /**
   * Set account owner.
   *
   * @param owner identifier of new account owner.
   * @throws RestApiException
   */
  void owner(String owner) throws RestApiException;

  /** @return account description. */
  String description() throws RestApiException;

  /**
   * Set account decsription.
   *
   * @param description new description.
   * @throws RestApiException
   */
  void description(String description) throws RestApiException;

  /** @return account options. */
  GroupOptionsInfo options() throws RestApiException;

  /**
   * Set account options.
   *
   * @param options new options.
   * @throws RestApiException
   */
  void options(GroupOptionsInfo options) throws RestApiException;

  /**
   * List account members, non-recursively.
   *
   * @return account members.
   * @throws RestApiException
   */
  List<AccountInfo> members() throws RestApiException;

  /**
   * List account members.
   *
   * @param recursive whether to recursively included groups.
   * @return account members.
   * @throws RestApiException
   */
  List<AccountInfo> members(boolean recursive) throws RestApiException;

  /**
   * Add members to a account.
   *
   * @param members list of member identifiers, in any format accepted by {@link
   *     com.google.gerrit.extensions.api.accounts.Accounts#id(String)}
   * @throws RestApiException
   */
  void addMembers(String... members) throws RestApiException;

  /**
   * Remove members from a account.
   *
   * @param members list of member identifiers, in any format accepted by {@link
   *     com.google.gerrit.extensions.api.accounts.Accounts#id(String)}
   * @throws RestApiException
   */
  void removeMembers(String... members) throws RestApiException;

  /**
   * Lists the subgroups of this account.
   *
   * @return the found subgroups
   * @throws RestApiException
   */
  List<GroupInfo> includedGroups() throws RestApiException;

  /**
   * Adds subgroups to this account.
   *
   * @param groups list of account identifiers, in any format accepted by {@link Groups#id(String)}
   * @throws RestApiException
   */
  void addGroups(String... groups) throws RestApiException;

  /**
   * Removes subgroups from this account.
   *
   * @param groups list of account identifiers, in any format accepted by {@link Groups#id(String)}
   * @throws RestApiException
   */
  void removeGroups(String... groups) throws RestApiException;

  /**
   * Returns the audit log of the account.
   *
   * @return list of audit events of the account.
   * @throws RestApiException
   */
  List<? extends GroupAuditEventInfo> auditLog() throws RestApiException;

  /**
   * Reindexes the account.
   *
   * <p>Only supported for internal groups.
   *
   * @throws RestApiException
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
    public void addMembers(String... members) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void removeMembers(String... members) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public List<GroupInfo> includedGroups() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void addGroups(String... groups) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public void removeGroups(String... groups) throws RestApiException {
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
