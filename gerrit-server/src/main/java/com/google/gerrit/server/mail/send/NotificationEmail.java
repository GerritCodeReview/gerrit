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

import com.google.common.collect.Iterables;
import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountProjectWatch.NotifyType;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.server.mail.Address;
import com.google.gerrit.server.mail.RecipientType;
import com.google.gerrit.server.mail.send.ProjectWatch.Watchers;
import com.google.gwtorm.server.OrmException;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common class for notifications that are related to a project and branch */
public abstract class NotificationEmail extends OutgoingEmail {
  private static final Logger log = LoggerFactory.getLogger(NotificationEmail.class);

  protected Branch.NameKey branch;

  protected NotificationEmail(EmailArguments ea, String mc, Branch.NameKey branch) {
    super(ea, mc);
    this.branch = branch;
  }

  @Override
  protected void init() throws EmailException {
    super.init();
    setListIdHeader();
  }

  private void setListIdHeader() throws EmailException {
    // Set a reasonable list id so that filters can be used to sort messages
    setVHeader("List-Id", "<$email.listId.replace('@', '.')>");
    if (getSettingsUrl() != null) {
      setVHeader("List-Unsubscribe", "<$email.settingsUrl>");
    }
  }

  public String getListId() throws EmailException {
    return velocify("gerrit-$projectName.replace('/', '-')@$email.gerritHost");
  }

  /** Include users and groups that want notification of events. */
  protected void includeWatchers(NotifyType type) {
    try {
      Watchers matching = getWatchers(type);
      add(RecipientType.TO, matching.to);
      add(RecipientType.CC, matching.cc);
      add(RecipientType.BCC, matching.bcc);
    } catch (OrmException err) {
      // Just don't CC everyone. Better to send a partial message to those
      // we already have queued up then to fail deliver entirely to people
      // who have a lower interest in the change.
      log.warn("Cannot BCC watchers for " + type, err);
    }
  }

  /** Returns all watchers that are relevant */
  protected abstract Watchers getWatchers(NotifyType type) throws OrmException;

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
  protected void setupVelocityContext() {
    super.setupVelocityContext();
    velocityContext.put("projectName", branch.getParentKey().get());
    velocityContext.put("branch", branch);
  }

  @Override
  protected void setupSoyContext() {
    super.setupSoyContext();

    String projectName = branch.getParentKey().get();
    soyContext.put("projectName", projectName);
    // shortProjectName is the project name with the path abbreviated.
    soyContext.put("shortProjectName", projectName.replaceAll("/.*/", "..."));

    soyContextEmailData.put("sshHost", getSshHost());

    Map<String, String> branchData = new HashMap<>();
    branchData.put("shortName", branch.getShortName());
    soyContext.put("branch", branchData);
  }
}
