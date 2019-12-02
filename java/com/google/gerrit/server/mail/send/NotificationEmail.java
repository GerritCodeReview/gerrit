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

package com.google.gerrit.server.mail.send;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.exceptions.EmailException;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.mail.Address;
import com.google.gerrit.mail.MailHeader;
import com.google.gerrit.server.account.ProjectWatches.NotifyType;
import com.google.gerrit.server.mail.send.ProjectWatch.Watchers;
import java.util.HashMap;
import java.util.Map;

/** Common class for notifications that are related to a project and branch */
public abstract class NotificationEmail extends OutgoingEmail {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  protected BranchNameKey branch;

  protected NotificationEmail(EmailArguments args, String messageClass, BranchNameKey branch) {
    super(args, messageClass);
    this.branch = branch;
  }

  @Override
  protected void init() throws EmailException {
    super.init();
    setListIdHeader();
  }

  private void setListIdHeader() {
    // Set a reasonable list id so that filters can be used to sort messages
    setHeader(
        "List-Id",
        "<gerrit-" + branch.project().get().replace('/', '-') + "." + getGerritHost() + ">");
    if (getSettingsUrl() != null) {
      setHeader("List-Unsubscribe", "<" + getSettingsUrl() + ">");
    }
  }

  /** Include users and groups that want notification of events. */
  protected void includeWatchers(NotifyType type) {
    includeWatchers(type, true);
  }

  /** Include users and groups that want notification of events. */
  protected void includeWatchers(NotifyType type, boolean includeWatchersFromNotifyConfig) {
    try {
      Watchers matching = getWatchers(type, includeWatchersFromNotifyConfig);
      add(RecipientType.TO, matching.to);
      add(RecipientType.CC, matching.cc);
      add(RecipientType.BCC, matching.bcc);
    } catch (StorageException err) {
      // Just don't CC everyone. Better to send a partial message to those
      // we already have queued up then to fail deliver entirely to people
      // who have a lower interest in the change.
      logger.atWarning().withCause(err).log("Cannot BCC watchers for %s", type);
    }
  }

  /** Returns all watchers that are relevant */
  protected abstract Watchers getWatchers(NotifyType type, boolean includeWatchersFromNotifyConfig);

  /** Add users or email addresses to the TO, CC, or BCC list. */
  protected void add(RecipientType type, Watchers.List list) {
    for (Account.Id user : list.accounts) {
      add(type, user);
    }
    for (Address addr : list.emails) {
      add(type, addr);
    }
  }

  public String getSshHost() {
    String host = Iterables.getFirst(args.sshAddresses, null);
    if (host == null) {
      return null;
    }
    if (host.startsWith("*:")) {
      return getGerritHost() + host.substring(1);
    }
    return host;
  }

  @Override
  protected void setupSoyContext() {
    super.setupSoyContext();

    String projectName = branch.project().get();
    soyContext.put("projectName", projectName);
    // shortProjectName is the project name with the path abbreviated.
    soyContext.put("shortProjectName", getShortProjectName(projectName));

    // instanceAndProjectName is the instance's name followed by the abbreviated project path
    soyContext.put(
        "instanceAndProjectName",
        getInstanceAndProjectName(args.instanceNameProvider.get(), projectName));
    soyContext.put("addInstanceNameInSubject", args.addInstanceNameInSubject);

    soyContextEmailData.put("sshHost", getSshHost());

    Map<String, String> branchData = new HashMap<>();
    branchData.put("shortName", branch.shortName());
    soyContext.put("branch", branchData);

    footers.add(MailHeader.PROJECT.withDelimiter() + branch.project().get());
    footers.add(MailHeader.BRANCH.withDelimiter() + branch.shortName());
  }

  @VisibleForTesting
  protected static String getShortProjectName(String projectName) {
    int lastIndexSlash = projectName.lastIndexOf('/');
    if (lastIndexSlash == 0) {
      return projectName.substring(1); // Remove the first slash
    }
    if (lastIndexSlash == -1) { // No slash in the project name
      return projectName;
    }

    return "..." + projectName.substring(lastIndexSlash + 1);
  }

  @VisibleForTesting
  protected static String getInstanceAndProjectName(String instanceName, String projectName) {
    if (instanceName == null || instanceName.isEmpty()) {
      return getShortProjectName(projectName);
    }
    // Extract the project name (everything after the last slash) and prepends it with gerrit's
    // instance name
    return instanceName + "/" + projectName.substring(projectName.lastIndexOf('/') + 1);
  }
}
