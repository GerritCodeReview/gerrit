// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server;

import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.validators.EmailIdValidationListener;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.lib.PersonIdent;

import java.util.TimeZone;

public class CommitIdentProvider {

  public interface Factory {
    CommitIdentProvider create(Project.NameKey project);
  }

  private final Provider<CurrentUser> currentUser;
  private final DynamicItem<EmailIdValidationListener> emailIdValidator;
  private final Project.NameKey project;
  private final TimeZone tz;

  @Inject
  CommitIdentProvider(@GerritPersonIdent PersonIdent gerritIdent,
      Provider<CurrentUser> currentUser,
      DynamicItem<EmailIdValidationListener> emailIdValidator,
      @Assisted Project.NameKey project) {
    this.currentUser = currentUser;
    this.tz = gerritIdent.getTimeZone();
    this.emailIdValidator = emailIdValidator;
    this.project = project;
  }

  public PersonIdent getCommitterIdent() {
    EmailIdValidationListener evl = emailIdValidator.get();
    if (evl != null) { // A plugin is available
      IdentifiedUser currentIdentifiedUser = currentUser.get().asIdentifiedUser();
      for (String email : currentIdentifiedUser.getEmailAddresses()) {
        if (evl.isCommitterEmailIdValid(project, email,
            currentIdentifiedUser)) {
          return currentIdentifiedUser.newCommitterIdent(
              TimeUtil.nowTs(), tz, email);
        }
      }
    }
    return null;
  }

  public PersonIdent getAuthorIdent() {
    EmailIdValidationListener evl = emailIdValidator.get();
    if (evl != null) { // A plugin is available
      IdentifiedUser currentIdentifiedUser = currentUser.get().asIdentifiedUser();
      for (String email : currentIdentifiedUser.getEmailAddresses()) {
        if (evl.isAuthorEmailIdValid(project, email, currentIdentifiedUser)) {
          return currentIdentifiedUser.newCommitterIdent(
              TimeUtil.nowTs(), tz, email);
        }
      }
    }
    return null;
  }
}
