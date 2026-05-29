// Copyright (C) 2023 The Android Open Source Project
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
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.mail.MailHeader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Contains utils for email notification related to the events on project+branch. */
public class BranchEmailUtils {
  /** Set a reasonable list id so that filters can be used to sort messages. */
  public static void setListIdHeader(OutgoingEmail email, BranchNameKey branch) {
    email.setHeader(
        "List-Id",
        "<gerrit-" + branch.project().get().replace('/', '-') + "." + email.getGerritHost() + ">");
    if (email.getSettingsUrl() != null) {
      email.setHeader("List-Unsubscribe", "<" + email.getSettingsUrl() + ">");
    }
  }

  /** Add branch information to soy template params. */
  public static void addBranchData(OutgoingEmail email, EmailArguments args, BranchNameKey branch) {
    String projectName = branch.project().get();
    email.addSoyParam("projectName", projectName);
    // shortProjectName is the project name with the path abbreviated.
    email.addSoyParam("shortProjectName", getShortProjectName(projectName));

    // instanceAndProjectName is the instance's name followed by the abbreviated project path
    email.addSoyParam(
        "instanceAndProjectName", getInstanceAndProjectName(args.instanceName, projectName));
    email.addSoyParam("addInstanceNameInSubject", args.addInstanceNameInSubject);

    email.addSoyEmailDataParam("sshHost", getSshHost(email.getGerritHost(), args.sshAddresses));

    Map<String, String> branchData = new HashMap<>();
    branchData.put("shortName", branch.shortName());
    email.addSoyParam("branch", branchData);

    email.addFooter(MailHeader.PROJECT.withDelimiter() + branch.project().get());
    email.addFooter(MailHeader.BRANCH.withDelimiter() + branch.shortName());
  }

  @Nullable
  private static String getSshHost(String gerritHost, List<String> sshAddresses) {
    String host = Iterables.getFirst(sshAddresses, null);
    if (host == null) {
      return null;
    }
    if (host.startsWith("*:")) {
      return gerritHost + host.substring(1);
    }
    return host;
  }

  /** Shortens project/repo name to only show part after the last '/'. */
  public static String getShortProjectName(String projectName) {
    int lastIndexSlash = projectName.lastIndexOf('/');
    if (lastIndexSlash == 0) {
      return projectName.substring(1); // Remove the first slash
    }
    if (lastIndexSlash == -1) { // No slash in the project name
      return projectName;
    }

    return "..." + projectName.substring(lastIndexSlash + 1);
  }

  /** Returns a project/repo name that includes instance as prefix. */
  public static String getInstanceAndProjectName(String instanceName, String projectName) {
    if (instanceName == null || instanceName.isEmpty()) {
      return getShortProjectName(projectName);
    }
    // Extract the project name (everything after the last slash) and prepends it with gerrit's
    // instance name
    return instanceName + "/" + projectName.substring(projectName.lastIndexOf('/') + 1);
  }
}
