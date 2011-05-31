// Copyright (C) 2010 The Android Open Source Project
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

import com.google.gerrit.server.ssh.SshInfo;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.AccountProjectWatch;
import com.google.gerrit.reviewdb.ChangeMessage;

import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jcraft.jsch.HostKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Sends an email to one or more interested parties. */
public class BranchEmail extends OutgoingEmail {
  private static final Logger log =
    LoggerFactory.getLogger(BranchEmail.class);

  protected final Project project;
  protected final String branchName;
  protected final ReceiveCommand.Type type;
  protected ChangeData changeData = null;
  protected ProjectState projectState;
  protected Set<Account.Id> authors;
  protected boolean emailOnlyAuthors;
  private HashSet<Account.Id> watchers = new HashSet<Account.Id>();
  private final SshInfo sshInfo;

  public static interface Factory {
    BranchEmail create(Project p, List<String> bn, ReceiveCommand.Type type);
  }

  @Inject
  protected BranchEmail(EmailArguments ea, SshInfo sshInfo, @Assisted Project p,
      @Assisted List<String> bn, @Assisted ReceiveCommand.Type type) {
    super(ea, bn.get(0));
    this.project = p;
    this.branchName = bn.get(1);
    this.emailOnlyAuthors = false;
    this.type = type;
    this.sshInfo = sshInfo;
  }

  public void setFrom(final Account.Id id) {
    super.setFrom(id);

    final IdentifiedUser user =  args.identifiedUserFactory.create(id);
    final Set<AccountGroup.UUID> gids = user.getEffectiveGroups();
    for (final AccountGroup.UUID gid : gids) {
      if (args.groupCache.get(gid).isEmailOnlyAuthors()) {
        emailOnlyAuthors = true;
        break;
      }
    }
  }

  public List<String> getWatcherNames() {
    if (watchers.isEmpty()) {
      return null;
    }
    List<String> names = new ArrayList<String>();
    for (Account.Id id : watchers) {
      names.add(getNameFor(id));
    }
    return names;
  }

  private HashSet<Account.Id> getWatchers() throws EmailException {
    try {
      for (AccountProjectWatch p : args.db.get().accountProjectWatches()
          .byProject(project.getNameKey())) {
        watchers.add(p.getAccountId());
      }
    } catch (OrmException e) {
      log.error("Invalid watcher is set", e);
    }
    return watchers;
  }

  /** Format the message body by calling {@link #appendText(String)}. */
  protected void format() throws EmailException {
    formatChange();
    appendText(velocifyFile("BranchFooter.vm"));
    try {
      HashSet<Account.Id> localwatchers = new HashSet<Account.Id>();
      for (AccountProjectWatch p : args.db.get().accountProjectWatches().byProject(
          project.getNameKey())) {
        localwatchers.add(p.getAccountId());
      }

      TreeSet<String> names = new TreeSet<String>();
      for (Account.Id who : localwatchers) {
        names.add(getNameEmailFor(who));
      }

      for (String name : names) {
        appendText("Gerrit-Watcher: " + name + "\n");
      }
    } catch (OrmException e) {
      log.error("Format of branch email is Invalid", e);
    }
  }

  /** Format the message body by calling {@link #appendText(String)}. */
  protected void formatChange() throws EmailException {
    switch (this.type) {
      case CREATE:
        appendText(velocifyFile("NewBranch.vm"));
        break;
      case UPDATE:
        appendText(velocifyFile("ModifyBranch.vm"));
        break;
      case DELETE:
        appendText(velocifyFile("DeleteBranch.vm"));
        break;
    }
  }

  /** Setup the message headers and envelope (TO, CC, BCC). */
  protected void init() throws EmailException {
    if (args.projectCache != null) {
      projectState = args.projectCache.get(project.getNameKey());
    } else {
      projectState = null;
    }

    super.init();
    setHeader("Message-ID", getChangeMessageThreadId());

    getWatchers();
    add(RecipientType.TO, watchers);
    setChangeSubjectHeader();
    setListIdHeader();
  }

  private void setListIdHeader() throws EmailException {
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

  private void setChangeSubjectHeader() throws EmailException {
    setHeader("Subject", velocifyFile("BranchSubject.vm"));
  }

  public String getChangeMessageThreadId() throws EmailException {
    return velocify("<gerrit.${change.createdOn.time}.$change.key.get()" +
                    "@$email.gerritHost>");
  }

  /** Format the sender's "cover letter", {@link #getCoverLetter()}. */
  protected void formatCoverLetter() {
    final String cover = getCoverLetter();
    if (!"".equals(cover)) {
      appendText(cover);
      appendText("\n\n");
    }
  }

  /** Get the text of the "cover letter", from {@link ChangeMessage}. */
  public String getCoverLetter() {
    return "";
  }

  /** Get the project entity the change is in; null if its been deleted. */
  protected ProjectState getProjectState() {
    return projectState;
  }

  /** Get the groups which own the project. */
  protected Set<AccountGroup.UUID> getProjectOwners() {
    final ProjectState r;

    r = args.projectCache.get(project.getNameKey());
    return r != null ? r.getOwners() : Collections.<AccountGroup.UUID> emptySet();
  }

  /** TO or CC all vested parties (change owner, patch set uploader, author). */
  protected void rcptToAuthors(final RecipientType rt) {
    for (final Account.Id id : authors) {
      add(rt, id);
    }
  }

  /** Returns all watches that are relevant */
  protected final List<AccountProjectWatch> getWatches() throws OrmException {
    List<AccountProjectWatch> matching = new ArrayList<AccountProjectWatch>();
    Set<Account.Id> projectWatchers = new HashSet<Account.Id>();

    for (AccountProjectWatch w : args.db.get().accountProjectWatches()
        .byProject(project.getNameKey())) {
      projectWatchers.add(w.getAccountId());
      add(matching, w);
    }

    for (AccountProjectWatch w : args.db.get().accountProjectWatches()
        .byProject(args.wildProject)) {
      if (!projectWatchers.contains(w.getAccountId())) {
        add(matching, w);
      }
    }

    return Collections.unmodifiableList(matching);
  }

  @SuppressWarnings("unchecked")
  private void add(List<AccountProjectWatch> matching, AccountProjectWatch w)
      throws OrmException {
    IdentifiedUser user =
        args.identifiedUserFactory.create(args.db, w.getAccountId());
    ChangeQueryBuilder qb = args.queryBuilder.create(user);
    Predicate<ChangeData> p = qb.is_visible();
    if (w.getFilter() != null) {
      try {
        qb.setAllowFile(true);
        p = Predicate.and(qb.parse(w.getFilter()), p);
        p = args.queryRewriter.get().rewrite(p);
        if (p.match(changeData)) {
          matching.add(w);
        }
      } catch (QueryParseException e) {
        // Ignore broken filter expressions.
      }
    } else if (p.match(changeData)) {
      matching.add(w);
    }
  }

  @Override
  protected void setupVelocityContext() {
    super.setupVelocityContext();
    velocityContext.put("coverLetter", getCoverLetter());
    velocityContext.put("fromName", getNameFor(fromId));
    velocityContext.put("projectName", //
        projectState != null ? projectState.getProject().getName() : null);
    velocityContext.put("branchName", branchName);
  }

  public String getSshHost() {
    final List<HostKey> hostKeys = sshInfo.getHostKeys();
    if (hostKeys.isEmpty()) {
      return null;
    }

    final String host = hostKeys.get(0).getHost();
    if (host.startsWith("*:")) {
      return getGerritHost() + host.substring(1);
    }
    return host;
  }
}
