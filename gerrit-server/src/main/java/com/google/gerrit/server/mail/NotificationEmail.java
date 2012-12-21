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

/**
 * Common class for notifications that are related to a project and branch
 */
public abstract class NotificationEmail extends OutgoingEmail {
  protected Project.NameKey project;
  protected Branch.NameKey branch;

  protected NotificationEmail(EmailArguments ea, String anonymousCowardName,
      String mc, Project.NameKey project, Branch.NameKey branch) {
    super(ea, anonymousCowardName, mc);

    this.project = project;
    this.branch = branch;
  }

  @Override
  protected void setupVelocityContext() {
    super.setupVelocityContext();
    velocityContext.put("projectName", project.get());
    velocityContext.put("branch", branch);
  }
}