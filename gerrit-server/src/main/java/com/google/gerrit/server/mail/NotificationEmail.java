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

import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.ssh.SshInfo;
import com.google.inject.Inject;

import com.jcraft.jsch.HostKey;

import java.util.List;

/**
 * Common class for notifications that are related to a project and branch
 */
public abstract class NotificationEmail extends OutgoingEmail {
  protected Project.NameKey project;
  protected Branch.NameKey branch;

  // Field sshInfo is used in some email sender classes, but not all of them,
  // and because some of them that don't use this field are injected before
  // initializing ssh module, such as AbandonedSender class, this field is
  // an optional injection.
  @Inject(optional=true)
  protected SshInfo sshInfo;

  protected NotificationEmail(EmailArguments ea, String anonymousCowardName,
      String mc, Project.NameKey project, Branch.NameKey branch) {
    super(ea, anonymousCowardName, mc);

    this.project = project;
    this.branch = branch;
  }

  public String getSshHost() {
    if (sshInfo == null) {
      return null;
    }
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

  @Override
  protected void setupVelocityContext() {
    super.setupVelocityContext();
    velocityContext.put("projectName", project.get());
    velocityContext.put("branch", branch);
  }
}