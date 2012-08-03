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
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.change.SingleGroupUser;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.jcraft.jsch.HostKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * Common class for sending out notifications related to alterations in
 * repositories and changes
 */
public abstract class NotificationEmail extends OutgoingEmail {
  private static final Logger log = LoggerFactory.getLogger(NotificationEmail.class);

  protected ProjectState projectState;
  protected Project.NameKey project;
  protected Branch.NameKey branch;

  @Inject
  protected NotificationEmail(EmailArguments ea, String anonymousCowardName,
      String mc, Project.NameKey project, Branch.NameKey branch) {
    super(ea, anonymousCowardName, mc);

    this.project = project;

    if (args.projectCache != null) {
      projectState = args.projectCache.get(project);
    } else {
      projectState = null;
    }

    this.branch = branch;
  }

  @Override
  protected void init() throws EmailException {
    super.init();

    setListIdHeader();
  }

  /** Get the project entity the change is in; null if its been deleted. */
  protected ProjectState getProjectState() {
    return projectState;
  }

  /** Get the groups which own the project. */
  protected Set<AccountGroup.UUID> getProjectOwners() {
    final ProjectState r;

    r = args.projectCache.get(project);
    return r != null ? r.getOwners() : Collections.<AccountGroup.UUID> emptySet();
  }

  public String getSshHost() {
    final List<HostKey> hostKeys = args.sshInfo.getHostKeys();
    if (hostKeys.isEmpty()) {
      return null;
    }

    final String host = hostKeys.get(0).getHost();
    if (host.startsWith("*:")) {
      return getGerritHost() + host.substring(1);
    }
    return host;
  }

  protected void setListIdHeader() throws EmailException {
    // Set a reasonable list id so that filters can be used to sort messages
    setVHeader("Mailing-List", "list $email.listId");
    setVHeader("List-Id", "<$email.listId.replace('@', '.')>");
    if (getSettingsUrl() != null) {
      setVHeader("List-Unsubscribe", "<$email.settingsUrl>");
    }
  }

  public String getListId() throws EmailException {
    return velocify("gerrit-$projectName.replace('/', '-')@$email.gerritHost");
  }

  @Override
  protected void setupVelocityContext() {
    super.setupVelocityContext();
    velocityContext.put("fromName", getNameFor(fromId));
    velocityContext.put("projectName", //
        projectState != null ? projectState.getProject().getName() : null);
    velocityContext.put("branch", branch);
  }

  /** BCC users and groups that want notification of events. */
  protected void bccWatches(NotifyType type) {
    try {
      Watchers matching = getWatches(type);
      for (Account.Id user : matching.accounts) {
        add(RecipientType.BCC, user);
      }
      for (Address addr : matching.emails) {
        add(RecipientType.BCC, addr);
      }
    } catch (OrmException err) {
      // Just don't CC everyone. Better to send a partial message to those
      // we already have queued up then to fail deliver entirely to people
      // who have a lower interest in the change.
      log.warn("Cannot BCC watchers for " + type, err);
    }
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
        recursivelyAddAllAccounts(matching, group);
      }
    }

    if (!nc.getAddresses().isEmpty()) {
      if (nc.getFilter() != null) {
        if (checkFilter(args.anonymousUser, nc.getFilter(), false)) {
          matching.emails.addAll(nc.getAddresses());
        }
      } else {
        matching.emails.addAll(nc.getAddresses());
      }
    }
  }

  private void recursivelyAddAllAccounts(Watchers matching, AccountGroup group)
      throws OrmException {
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
      matching.accounts.add(w.getAccountId());
    }
  }

  protected abstract boolean checkFilter(CurrentUser user, String filter,
      boolean visible) throws OrmException;

  protected static class Watchers {
    protected final Set<Account.Id> accounts = Sets.newHashSet();
    protected final Set<Address> emails = Sets.newHashSet();
  }
}