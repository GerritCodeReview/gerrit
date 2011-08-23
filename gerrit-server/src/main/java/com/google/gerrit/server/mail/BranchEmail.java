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
import com.google.gerrit.reviewdb.AccountProjectWatch;

import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.server.project.ProjectState;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Sends an email to the owner of the project and the watchers (if this project
 * has some watchers)
 */
public class BranchEmail extends NotificationEmail {
  private static final Logger log = LoggerFactory.getLogger(BranchEmail.class);

  protected final Project project;
  protected final String branchName;
  protected final ReceiveCommand.Type type;
  protected ProjectState projectState;
  private Set<Account.Id> watchers;

  public static interface Factory {
    BranchEmail create(Project p, String branchName, ReceiveCommand.Type type);
  }

  @Inject
  protected BranchEmail(EmailArguments ea, SshInfo sshInfo,
      @Assisted Project p, @Assisted String bn,
      @Assisted ReceiveCommand.Type type) {
    super(ea, null, getMessageClass(type));
    this.project = p;
    this.branchName = bn;
    this.type = type;
    this.sshInfo = sshInfo;
  }

  private static String getMessageClass(ReceiveCommand.Type type) {
    switch (type) {
      case CREATE:
        return "newBranch";
      case UPDATE:
      case UPDATE_NONFASTFORWARD:
        return "modifyBranch";
      case DELETE:
        return "deleteBranch";
      default:
        return "errorBranch";
    }
  }

  public String getMessageClass() {
    return messageClass;
  }

  public List<String> getWatcherNames() {
    if (watchers.isEmpty()) {
      return null;
    }
    List<String> names = new ArrayList<String>(watchers.size());
    for (Account.Id id : watchers) {
      names.add(getNameFor(id));
    }
    return names;
  }

  private Set<Account.Id> getSubscribers() throws EmailException {
    watchers = new HashSet<Account.Id>();
    Set<Account.Id> subscribers = new HashSet<Account.Id>();
    try {
      for (AccountProjectWatch p : args.db.get().accountProjectWatches()
          .byProject(project.getNameKey())) {
        watchers.add(p.getAccountId());

        switch (type) {
          case CREATE:
          case DELETE:
            String filter = p.getFilter();
            if (filter.equals("*")) {
              subscribers.add(p.getAccountId());
            } else if (branchMatchWithFilter(branchName, filter)) {
                subscribers.add(p.getAccountId());
            }
            break;
          default:
            break;
        }
      }
    } catch (OrmException e) {
      log.error("Invalid watcher is set", e);
    }
    return subscribers;
  }

  private boolean branchMatchWithFilter(String branch, String filter) {
    if (filter.startsWith("^")) {
      Pattern pattern = Pattern.compile(filter);
      return pattern.matcher(branch).matches();
    } else if (filter.endsWith("/*")) {
      return branch.startsWith(filter.substring(0, filter.length() - 1));
    } else {
      return branch.equals(filter);
    }
  }
  /** Format the message body by calling {@link #appendText(String)}. */
  protected void format() throws EmailException {
    formatBranch();
    appendText(velocifyFile("BranchFooter.vm"));
  }

  /** Format the message body by calling {@link #appendText(String)}. */
  protected void formatBranch() throws EmailException {
    switch (this.type) {
      case CREATE:
        appendText(velocifyFile("NewBranch.vm"));
        break;
      case UPDATE:
      case UPDATE_NONFASTFORWARD:
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
    projectState = args.projectCache.get(project.getNameKey());

    super.init();

    Set<Account.Id> subscribers = getSubscribers();
    if (!subscribers.isEmpty()) {
      add(RecipientType.BCC, subscribers);
    }
    setBranchSubjectHeader();
    setListIdHeader();
  }

  public String getListId() throws EmailException {
    return velocify("gerrit-$projectName.replace('/', '-')@$email.gerritHost");
  }

  private void setBranchSubjectHeader() throws EmailException {
    setHeader("Subject", velocifyFile("BranchSubject.vm"));
  }

  /** Get the project entity you are pushing to; null if its been deleted. */
  protected ProjectState getProjectState() {
    return projectState;
  }

  @Override
  protected void setupVelocityContext() {
    super.setupVelocityContext();
    velocityContext.put("fromName", getNameFor(fromId));
    velocityContext.put("projectName", projectState != null ? projectState
        .getProject().getName() : null);
    velocityContext.put("branchName", branchName);
  }
}
