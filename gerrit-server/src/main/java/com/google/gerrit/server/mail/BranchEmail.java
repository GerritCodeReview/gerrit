// Copyright (C) 2011 The Android Open Source Project
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
import com.google.gerrit.reviewdb.AccountGroupMember;

import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.server.project.ProjectState;
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

/**
 * Sends an email to the owner of the project and the watchers (if this project
 * has some watchers)
 */
public class BranchEmail extends OutgoingEmail {
  private static final Logger log = LoggerFactory.getLogger(BranchEmail.class);

  protected final Project project;
  protected final String branchName;
  protected final ReceiveCommand.Type type;
  protected ProjectState projectState;
  private HashSet<Account.Id> watchers = new HashSet<Account.Id>();
  private HashSet<Account.Id> owners = new HashSet<Account.Id>();
  private final SshInfo sshInfo;

  public static interface Factory {
    BranchEmail create(Project p, String branchName, ReceiveCommand.Type type);
  }

  @Inject
  protected BranchEmail(EmailArguments ea, SshInfo sshInfo,
      @Assisted Project p, @Assisted String bn,
      @Assisted ReceiveCommand.Type type) {
    super(ea, getMessageClass(type));
    this.project = p;
    this.branchName = bn;
    this.type = type;
    this.sshInfo = sshInfo;
  }

  private static String getMessageClass(ReceiveCommand.Type type) {
    String mc = "";
    switch (type) {
      case CREATE:
        mc = "newBranch";
        break;
      case UPDATE:
        mc = "modifyBranch";
        break;
      case DELETE:
        mc = "deleteBranch";
        break;
    }
    return mc;
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

  public List<String> getOwnerNames() {
    if (owners.isEmpty()) {
      return null;
    }
    List<String> names = new ArrayList<String>();
    for (Account.Id id : owners) {
      names.add(getNameFor(id));
    }
    return names;
  }

  public String getFromName() {
    String fromName;
    fromName = getNameFor(fromId);
    return fromName;
  }

  private HashSet<Account.Id> getWatchers() throws EmailException {
    HashSet<Account.Id> watchers = new HashSet<Account.Id>();
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

  private HashSet<Account.Id> getOwners() throws EmailException {
    HashSet<Account.Id> owners = new HashSet<Account.Id>();
    try {
      for (AccountGroup.UUID uuid : projectState.getOwners()) {
        for (AccountGroup ag : args.db.get().accountGroups().byUUID(uuid)) {
          for (AccountGroupMember gm : args.db.get().accountGroupMembers()
              .byGroup(ag.getId())) {
            owners.add(gm.getAccountId());
          }
        }
      }
    } catch (OrmException e) {
      log.error("Invalid watcher is set", e);
    }

    return owners;
  }

  /** Format the message body by calling {@link #appendText(String)}. */
  protected void format() throws EmailException {
    formatBranch();
    appendText(velocifyFile("BranchFooter.vm"));
    for (Account.Id thisOwner : owners) {
      appendText("Gerrit-Owner: " + getNameEmailFor(thisOwner) + "\n");
    }
  }

  /** Format the message body by calling {@link #appendText(String)}. */
  protected void formatBranch() throws EmailException {
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
  @Override
  protected void init() throws EmailException {
    if (args.projectCache != null) {
      projectState = args.projectCache.get(project.getNameKey());
    } else {
      projectState = null;
    }

    super.init();

    owners = getOwners();
    watchers = getWatchers();
    add(RecipientType.TO, owners);
    if (!watchers.isEmpty()) {
      add(RecipientType.BCC, watchers);
    }
    setBranchSubjectHeader();
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

  private void setBranchSubjectHeader() throws EmailException {
    setHeader("Subject", velocifyFile("BranchSubject.vm"));
  }

  /** Format the sender's "cover letter", {@link #getCoverLetter()}. */
  protected void formatCoverLetter() {
    final String cover = getCoverLetter();
    if (!"".equals(cover)) {
      appendText(cover);
      appendText("\n\n");
    }
  }

  /** Get the text of the "cover letter", from {@link BranchMessage}. */
  public String getCoverLetter() {
    return "";
  }

  /** Get the project entity you are pushing to; null if its been deleted. */
  protected ProjectState getProjectState() {
    return projectState;
  }

  /** Get the groups which own the project. */
  protected Set<AccountGroup.UUID> getProjectOwners() {
    final ProjectState r;

    r = args.projectCache.get(project.getNameKey());
    return r != null ? r.getOwners() : Collections
        .<AccountGroup.UUID> emptySet();
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
