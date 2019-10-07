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

package com.google.gerrit.server.extensions.events;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ApprovalInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.server.GpgException;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.change.RevisionJson;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.jgit.lib.Config;

/**
 * Formats change and revision info objects to serve as payload for Gerrit events.
 *
 * <p>Uses configurable options ({@code event.payload.listChangeOptions}) to decide which fields to
 * populate.
 */
@Singleton
public class EventUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final ImmutableSet<ListChangesOption> DEFAULT_CHANGE_OPTIONS;

  static {
    EnumSet<ListChangesOption> opts = EnumSet.allOf(ListChangesOption.class);
    // Some options, like actions, are expensive to compute because they potentially have to walk
    // lots of history and inspect lots of other changes.
    opts.remove(ListChangesOption.CHANGE_ACTIONS);
    opts.remove(ListChangesOption.CURRENT_ACTIONS);
    // CHECK suppresses some exceptions on corrupt changes, which is not appropriate for passing
    // through the event system as we would rather let them propagate.
    opts.remove(ListChangesOption.CHECK);
    DEFAULT_CHANGE_OPTIONS = Sets.immutableEnumSet(opts);
  }

  private final ChangeData.Factory changeDataFactory;
  private final ChangeJson.Factory changeJsonFactory;
  private final RevisionJson.Factory revisionJsonFactory;
  private final ImmutableSet<ListChangesOption> changeOptions;

  @Inject
  EventUtil(
      ChangeJson.Factory changeJsonFactory,
      RevisionJson.Factory revisionJsonFactory,
      ChangeData.Factory changeDataFactory,
      @GerritServerConfig Config gerritConfig) {
    this.changeDataFactory = changeDataFactory;
    this.changeJsonFactory = changeJsonFactory;
    this.revisionJsonFactory = revisionJsonFactory;
    this.changeOptions = parseChangeListOptions(gerritConfig);
  }

  public ChangeInfo changeInfo(Change change) {
    return changeJsonFactory.create(changeOptions).format(change);
  }

  public RevisionInfo revisionInfo(Project project, PatchSet ps)
      throws PatchListNotAvailableException, GpgException, IOException, PermissionBackendException {
    return revisionInfo(project.getNameKey(), ps);
  }

  public RevisionInfo revisionInfo(Project.NameKey project, PatchSet ps)
      throws PatchListNotAvailableException, GpgException, IOException, PermissionBackendException {
    ChangeData cd = changeDataFactory.create(project, ps.id().changeId());
    return revisionJsonFactory.create(changeOptions).getRevisionInfo(cd, ps);
  }

  public AccountInfo accountInfo(AccountState accountState) {
    if (accountState == null || accountState.account().id() == null) {
      return null;
    }
    Account account = accountState.account();
    AccountInfo accountInfo = new AccountInfo(account.id().get());
    accountInfo.email = account.preferredEmail();
    accountInfo.name = account.fullName();
    accountInfo.username = accountState.userName().orElse(null);
    return accountInfo;
  }

  public Map<String, ApprovalInfo> approvals(
      AccountState accountState, Map<String, Short> approvals, Timestamp ts) {
    Map<String, ApprovalInfo> result = new HashMap<>();
    for (Map.Entry<String, Short> e : approvals.entrySet()) {
      Integer value = e.getValue() != null ? Integer.valueOf(e.getValue()) : null;
      result.put(
          e.getKey(), new ApprovalInfo(accountState.account().id().get(), value, null, null, ts));
    }
    return result;
  }

  private static ImmutableSet<ListChangesOption> parseChangeListOptions(Config gerritConfig) {
    String[] config = gerritConfig.getStringList("event", "payload", "listChangeOptions");
    if (config.length == 0) {
      return DEFAULT_CHANGE_OPTIONS;
    }

    ImmutableSet.Builder<ListChangesOption> result = ImmutableSet.builder();
    for (String c : config) {
      try {
        result.add(ListChangesOption.valueOf(c));
      } catch (IllegalArgumentException e) {
        logger.atWarning().withCause(e).log("could not parse list change option %s", c);
      }
    }
    return result.build();
  }
}
