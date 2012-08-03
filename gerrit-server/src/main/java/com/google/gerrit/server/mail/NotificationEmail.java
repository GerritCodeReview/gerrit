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

import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.jcraft.jsch.HostKey;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Common class for sending out notifications related to alterations in
 * repositories and changes
 */
public abstract class NotificationEmail extends OutgoingEmail {
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
}