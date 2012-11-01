// Copyright (C) 2012 The Android Open Source Project
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
import com.google.gerrit.reviewdb.client.AccountGroupInclude;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.client.AccountProjectWatch;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.AccountProjectWatch.NotifyType;
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
import java.util.Queue;
import java.util.Set;
import java.util.regex.Pattern;

public abstract class ProjectWatch {
  private static final Logger log = LoggerFactory.getLogger(ProjectWatch.class);

  protected final EmailArguments args;
  protected final ProjectState projectState;
  protected final Project.NameKey project;

  public ProjectWatch(EmailArguments args, Project.NameKey project,
      ProjectState projectState) {
    this.args = args;
    this.project = project;
    this.projectState = projectState;
  }

  /** Returns all watches that are relevant */
  protected Watchers getWatches(NotifyType type) throws OrmException {
    Watchers matching = new Watchers();

    Set<Account.Id> projectWatchers = new HashSet<Account.Id>();

    for (AccountProjectWatch w : args.db.get().accountProjectWatches()
        .byProject(project)) {
      projectWatchers.add(w.getAccountId());
      if (w.isNotify(type)) {
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
      if (checkFilter(user, nc.getFilter(), true)) {
        recursivelyAddAllAccounts(matching.list(nc.getHeader()), group);
      }
    }

    if (!nc.getAddresses().isEmpty()) {
      if (nc.getFilter() != null) {
        if (checkFilter(args.anonymousUser, nc.getFilter(), false)) {
          matching.list(nc.getHeader()).emails.addAll(nc.getAddresses());
        }
      } else {
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
      for (AccountGroupInclude m : args.db.get().accountGroupIncludes()
          .byGroup(next)) {
        if (seen.add(m.getIncludeId())) {
          scan.add(m.getIncludeId());
        }
      }
    }
  }

  private void add(Watchers matching, AccountProjectWatch w)
      throws OrmException {
    IdentifiedUser user =
        args.identifiedUserFactory.create(args.db, w.getAccountId());
    if (checkFilter(user, w.getFilter(), true)) {
      matching.bcc.accounts.add(w.getAccountId());
    }
  }

  protected abstract boolean checkFilter(CurrentUser user, String filter,
      boolean visibleTest) throws OrmException;

  public static class ChangeProjectWatch extends ProjectWatch {
    private final ChangeData changeData;

    public ChangeProjectWatch(EmailArguments args, Project.NameKey project,
        ProjectState projectState, ChangeData changeData) {
      super(args, project, projectState);
      this.changeData = changeData;
    }

    @Override
    protected Watchers getWatches(NotifyType type) throws OrmException {
      if (changeData == null) {
        return new Watchers();
      }
      return super.getWatches(type);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected boolean checkFilter(CurrentUser user, String filter,
        boolean visibleTest) throws OrmException {
      ChangeQueryBuilder qb = args.queryBuilder.create(user);
      qb.setAllowFile(true);
      Predicate<ChangeData> p = null;
      if (visibleTest) {
        p = qb.is_visible();
      }
      if (filter != null) {
        try {
          if (p == null) {
            p = qb.parse(filter);
          } else {
            p = Predicate.and(qb.parse(filter), p);
          }
          p = args.queryRewriter.get().rewrite(p);
        } catch (QueryParseException e) {
          log.warn(String.format(
              "Invalid filter \"%s\": %s", filter, e.getMessage()));
          // Ignore broken filter expressions.
          return false;
        }
      }

      if (p == null) {
        return true;
      }
      return p.match(changeData);
    }
  }

  public static class DirectPushProjectWatch extends ProjectWatch {
    private static final String FILTER_BRANCH = "branch";

    private final Branch.NameKey branch;

    public DirectPushProjectWatch(EmailArguments args, Project.NameKey project,
        ProjectState projectState, Branch.NameKey branch) {
      super(args, project, projectState);
      this.branch = branch;
    }

    @Override
    protected boolean checkFilter(CurrentUser user, String filter,
        boolean visibleTest) throws OrmException {
     if (filter == null || filter.indexOf(FILTER_BRANCH) == -1) {
        return true;
      }
      for (String s : filter.split(" ")) {
        if (s.startsWith(FILTER_BRANCH + ":")) {
          String branchName = s.substring(FILTER_BRANCH.length() + 1);
         return branchMatch(branchName);
       }
     }
     return true;
    }

    private boolean branchMatch(String filter) {
      String shortBranch = branch.getShortName();
      if (filter.equals("*")) {
        return true;
      } else if (filter.startsWith("^") && filter.length() > 1) {
        Pattern pattern = Pattern.compile(filter.substring(1));
        return pattern.matcher(shortBranch).matches();
      } else if (filter.endsWith("/*")) {
        return shortBranch.startsWith(filter.substring(0, filter.length() - 1));
      } else {
        return shortBranch.equals(filter);
      }
    }
  }

  public static class Watchers {
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
}
