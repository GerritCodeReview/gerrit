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

package com.google.gerrit.server.mail;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gerrit.common.data.GroupDescriptions;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupIncludeByUuid;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.client.AccountProjectWatch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.AccountProjectWatch.NotifyType;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.NotifyConfig;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.change.SingleGroupUser;
import com.google.gwtorm.server.OrmException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

public class ProjectWatch {
  private static final Logger log = LoggerFactory.getLogger(ProjectWatch.class);

  protected final EmailArguments args;
  protected final ProjectState projectState;
  protected final Project.NameKey project;
  protected final ChangeData changeData;

  public ProjectWatch(EmailArguments args, Project.NameKey project,
    ProjectState projectState, ChangeData changeData) {
    this.args = args;
    this.project = project;
    this.projectState = projectState;
    this.changeData = changeData;
  }

  /** Returns all watches that are relevant */
  protected final Watchers getWatches(NotifyType type) throws OrmException {
    Watchers matching = new Watchers();
    Set<Account.Id> projectWatchers = new HashSet<Account.Id>();

    for (AccountProjectWatch w : args.db.get().accountProjectWatches()
        .byProject(project)) {
      if (w.isNotify(type)) {
        projectWatchers.add(w.getAccountId());
        add(matching, w);
      }
    }

    for (AccountProjectWatch w : args.db.get().accountProjectWatches()
        .byProject(args.allProjectsName)) {
      if (!projectWatchers.contains(w.getAccountId()) && w.isNotify(type)) {
        add(matching, w);
      }
    }

    ProjectState state = projectState;
    while (state != null) {
      for (NotifyConfig nc : state.getConfig().getNotifyConfigs()) {
        if (nc.isNotify(type)) {
          try {
            add(matching, nc, state.getProject().getNameKey());
          } catch (QueryParseException e) {
            log.warn(String.format(
                "Project %s has invalid notify %s filter \"%s\": %s",
                state.getProject().getName(), nc.getName(),
                nc.getFilter(), e.getMessage()));
          }
        }
      }
      state = state.getParentState();
    }

    return matching;
  }

  protected static class Watchers {
    static class List {
      protected final Set<Account.Id> accounts = Sets.newHashSet();
      protected final Set<Address> emails = Sets.newHashSet();
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

  private void add(Watchers matching, NotifyConfig nc, Project.NameKey project)
      throws OrmException, QueryParseException {
    for (GroupReference ref : nc.getGroups()) {
      AccountGroup group =
          GroupDescriptions.toAccountGroup(args.groupBackend.get(ref.getUUID()));
      if (group == null) {
        log.warn(String.format(
            "Project %s has invalid group %s in notify section %s",
            project.get(), ref.getName(), nc.getName()));
        continue;
      }

      if (group.getType() != AccountGroup.Type.INTERNAL) {
        log.warn(String.format(
            "Project %s cannot use group %s of type %s in notify section %s",
            project.get(), ref.getName(), group.getType(), nc.getName()));
        continue;
      }

      CurrentUser user = new SingleGroupUser(args.capabilityControlFactory,
          ref.getUUID());
      if (filterMatch(user, nc.getFilter())) {
        recursivelyAddAllAccounts(matching.list(nc.getHeader()), group);
      }
    }

    if (!nc.getAddresses().isEmpty()) {
      if (filterMatch(args.anonymousUser, nc.getFilter())) {
        matching.list(nc.getHeader()).emails.addAll(nc.getAddresses());
      }
    }
  }

  private void recursivelyAddAllAccounts(Watchers.List matching,
      AccountGroup group) throws OrmException {
    Set<AccountGroup.Id> seen = Sets.newHashSet();
    Queue<AccountGroup.Id> scan = Lists.newLinkedList();
    scan.add(group.getId());
    seen.add(group.getId());
    while (!scan.isEmpty()) {
      AccountGroup.Id next = scan.remove();
      for (AccountGroupMember m : args.db.get().accountGroupMembers()
          .byGroup(next)) {
        matching.accounts.add(m.getAccountId());
      }
      for (AccountGroupIncludeByUuid m : args.db.get().accountGroupIncludesByUuid()
          .byGroup(next)) {
        List<AccountGroup> incGroup = args.db.get().accountGroups().
            byUUID(m.getIncludeUUID()).toList();
        if (incGroup.size() == 1) {
          AccountGroup.Id includeId = incGroup.get(0).getId();
          if (seen.add(includeId)) {
            scan.add(includeId);
          }
        }
      }
    }
  }

  private void add(Watchers matching, AccountProjectWatch w)
      throws OrmException {
    IdentifiedUser user =
        args.identifiedUserFactory.create(args.db, w.getAccountId());

    try {
      if (filterMatch(user, w.getFilter())) {
        matching.bcc.accounts.add(w.getAccountId());
      }
    } catch (QueryParseException e) {
      // Ignore broken filter expressions.
    }
  }

  @SuppressWarnings("unchecked")
  private boolean filterMatch(CurrentUser user, String filter)
      throws OrmException, QueryParseException {
    ChangeQueryBuilder qb = args.queryBuilder.create(user);
    Predicate<ChangeData> p = null;

    if (!(user instanceof AnonymousUser)) {
      p = qb.is_visible();
    }

    if (filter != null) {
      qb.setAllowFile(true);
      Predicate<ChangeData> filterPredicate = qb.parse(filter);
      if (p == null) {
        p = filterPredicate;
      } else {
        p = Predicate.and(filterPredicate, p);
      }
      p = args.queryRewriter.get().rewrite(p);
    }
    return p == null ? true : p.match(changeData);
  }
}
