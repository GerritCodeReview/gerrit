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
import com.google.gerrit.reviewdb.client.AccountProjectWatch.NotifyType;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

/**
 * Send notice about direct pushes on branch to the subscribers of project.
 */
public class DirectPushedSender extends NotificationEmail {
  private static final Logger log = LoggerFactory.getLogger(DirectPushedSender.class);

  private static final String FILTER_BRANCH = "branch";

  public static interface Factory {
    DirectPushedSender create(Project project, String branch, ReceiveCommand.Type type);
  }

  protected final ReceiveCommand.Type type;

  @Inject
  protected DirectPushedSender(EmailArguments ea,
      @AnonymousCowardName String anonymousCowardName,
      @Assisted Project project, @Assisted String branch,
      @Assisted ReceiveCommand.Type type) {
    super(ea, anonymousCowardName, "direct-push", project.getNameKey(),
        new Branch.NameKey(project.getNameKey(), branch));
    this.type = type;
  }

  public boolean getIsPushCreate() {
    if (type == ReceiveCommand.Type.CREATE) {
      return true;
    }
    return false;
  }

  public boolean getIsPushUpdate() {
    if (type == ReceiveCommand.Type.UPDATE ||
        type == ReceiveCommand.Type.UPDATE_NONFASTFORWARD) {
      return true;
    }
    return false;
  }

  public boolean getIsPushDelete() {
    if (type == ReceiveCommand.Type.DELETE) {
      return true;
    }
    return false;
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

  protected boolean checkFilter(CurrentUser user, String filter,
      boolean visible) throws OrmException {
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

  /** Format the message body by calling {@link #appendText(String)}. */
  protected void format() throws EmailException {
    appendText(velocifyFile("DirectPushed.vm"));
  }

  /** Setup the message headers and envelope (TO, CC, BCC). */
  @Override
  protected void init() throws EmailException {

    super.init();

    bccWatches(NotifyType.DIRECT_PUSHES);
    setBranchSubjectHeader();
    setListIdHeader();
  }

  private void setBranchSubjectHeader() throws EmailException {
    setHeader("Subject", velocifyFile("DirectPushedSubject.vm"));
  }

  @Override
  protected void setupVelocityContext() {
    super.setupVelocityContext();
  }
}
