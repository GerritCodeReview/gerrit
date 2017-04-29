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

package com.google.gerrit.server.mail.send;

import com.google.common.base.Strings;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.data.GroupDescriptions;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.WatchConfig.NotifyType;
import com.google.gerrit.server.account.WatchConfig.ProjectWatchKey;
import com.google.gerrit.server.git.NotifyConfig;
import com.google.gerrit.server.mail.Address;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.change.SingleGroupUser;
import com.google.gwtorm.server.OrmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProjectWatch {
  private static final Logger log = LoggerFactory.getLogger(ProjectWatch.class);

  protected final EmailArguments args;
  protected final ProjectState projectState;
  protected final Project.NameKey project;
  protected final ChangeData changeData;

  public ProjectWatch(
      EmailArguments args,
      Project.NameKey project,
      ProjectState projectState,
      ChangeData changeData) {
    this.args = args;
    this.project = project;
    this.projectState = projectState;
    this.changeData = changeData;
  }

  /** Returns all watchers that are relevant */
  public final Watchers getWatchers(NotifyType type, boolean includeWatchersFromNotifyConfig)
      throws OrmException {
    Watchers matching = new Watchers();
    Set<Account.Id> projectWatchers = new HashSet<>();

    for (AccountState a : args.accountQueryProvider.get().byWatchedProject(project)) {
      Account.Id accountId = a.getAccount().getId();
      for (Map.Entry<ProjectWatchKey, Set<NotifyType>> e : a.getProjectWatches().entrySet()) {
        if (project.equals(e.getKey().project())
            && add(matching, accountId, e.getKey(), e.getValue(), type)) {
          // We only want to prevent matching All-Projects if this filter hits
          projectWatchers.add(accountId);
        }
      }
    }

    for (AccountState a : args.accountQueryProvider.get().byWatchedProject(args.allProjectsName)) {
      for (Map.Entry<ProjectWatchKey, Set<NotifyType>> e : a.getProjectWatches().entrySet()) {
        if (args.allProjectsName.equals(e.getKey().project())) {
          Account.Id accountId = a.getAccount().getId();
          if (!projectWatchers.contains(accountId)) {
            add(matching, accountId, e.getKey(), e.getValue(), type);
          }
        }
      }
    }

    if (!includeWatchersFromNotifyConfig) {
      return matching;
    }

    for (ProjectState state : projectState.tree()) {
      for (NotifyConfig nc : state.getConfig().getNotifyConfigs()) {
        if (nc.isNotify(type)) {
          try {
            add(matching, nc);
          } catch (QueryParseException e) {
            log.warn(
                "Project {} has invalid notify {} filter \"{}\": {}",
                state.getProject().getName(),
                nc.getName(),
                nc.getFilter(),
                e.getMessage());
          }
        }
      }
    }

    return matching;
  }

  public static class Watchers {
    static class List {
      protected final Set<Account.Id> accounts = new HashSet<>();
      protected final Set<Address> emails = new HashSet<>();
    }

    protected final List to = new List();
    protected final List cc = new List();
    protected final List bcc = new List();

    List list(NotifyConfig.Header header) {
      switch (header) {
        case TO:
          return to;
        case CC:
          return cc;
        default:
        case BCC:
          return bcc;
      }
    }
  }

  private void add(Watchers matching, NotifyConfig nc) throws OrmException, QueryParseException {
    for (GroupReference ref : nc.getGroups()) {
      CurrentUser user = new SingleGroupUser(ref.getUUID());
      if (filterMatch(user, nc.getFilter())) {
        deliverToMembers(matching.list(nc.getHeader()), ref.getUUID());
      }
    }

    if (!nc.getAddresses().isEmpty()) {
      if (filterMatch(null, nc.getFilter())) {
        matching.list(nc.getHeader()).emails.addAll(nc.getAddresses());
      }
    }
  }

  private void deliverToMembers(Watchers.List matching, AccountGroup.UUID startUUID)
      throws OrmException {
    ReviewDb db = args.db.get();
    Set<AccountGroup.UUID> seen = new HashSet<>();
    List<AccountGroup.UUID> q = new ArrayList<>();

    seen.add(startUUID);
    q.add(startUUID);

    while (!q.isEmpty()) {
      AccountGroup.UUID uuid = q.remove(q.size() - 1);
      GroupDescription.Basic group = args.groupBackend.get(uuid);
      if (!Strings.isNullOrEmpty(group.getEmailAddress())) {
        // If the group has an email address, do not expand membership.
        matching.emails.add(new Address(group.getEmailAddress()));
        continue;
      }

      AccountGroup ig = GroupDescriptions.toAccountGroup(group);
      if (ig == null) {
        // Non-internal groups cannot be expanded by the server.
        continue;
      }

      for (AccountGroupMember m : db.accountGroupMembers().byGroup(ig.getId())) {
        matching.accounts.add(m.getAccountId());
      }
      for (AccountGroup.UUID m : args.groupIncludes.subgroupsOf(uuid)) {
        if (seen.add(m)) {
          q.add(m);
        }
      }
    }
  }

  private boolean add(
      Watchers matching,
      Account.Id accountId,
      ProjectWatchKey key,
      Set<NotifyType> watchedTypes,
      NotifyType type)
      throws OrmException {
    IdentifiedUser user = args.identifiedUserFactory.create(accountId);

    try {
      if (filterMatch(user, key.filter())) {
        // If we are set to notify on this type, add the user.
        // Otherwise, still return true to stop notifications for this user.
        if (watchedTypes.contains(type)) {
          matching.bcc.accounts.add(accountId);
        }
        return true;
      }
    } catch (QueryParseException e) {
      // Ignore broken filter expressions.
    }
    return false;
  }

  private boolean filterMatch(CurrentUser user, String filter)
      throws OrmException, QueryParseException {
    ChangeQueryBuilder qb;
    Predicate<ChangeData> p = null;

    if (user == null) {
      qb = args.queryBuilder.asUser(args.anonymousUser);
    } else {
      qb = args.queryBuilder.asUser(user);
      p = qb.is_visible();
    }

    if (filter != null) {
      Predicate<ChangeData> filterPredicate = qb.parse(filter);
      if (p == null) {
        p = filterPredicate;
      } else {
        p = Predicate.and(filterPredicate, p);
      }
    }
    return p == null || p.asMatchable().match(changeData);
  }
}
