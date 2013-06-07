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

package com.google.gerrit.server.account;

import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.AccountSshKey;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.project.ProjectControl;
import com.google.inject.TypeLiteral;

public class AccountResource implements RestResource {
  public static final TypeLiteral<RestView<AccountResource>> ACCOUNT_KIND =
      new TypeLiteral<RestView<AccountResource>>() {};

  public static final TypeLiteral<RestView<Capability>> CAPABILITY_KIND =
      new TypeLiteral<RestView<Capability>>() {};

  public static final TypeLiteral<RestView<Email>> EMAIL_KIND =
      new TypeLiteral<RestView<Email>>() {};

  public static final TypeLiteral<RestView<SshKey>> SSH_KEY_KIND =
      new TypeLiteral<RestView<SshKey>>() {};

  public static final TypeLiteral<RestView<Project>> PROJECT_KIND =
      new TypeLiteral<RestView<Project>>() {};

  public static final TypeLiteral<RestView<ProjectCapability>> PROJECT_CAPABILITY_KIND =
      new TypeLiteral<RestView<ProjectCapability>>() {};

  private final IdentifiedUser user;

  public AccountResource(IdentifiedUser user) {
    this.user = user;
  }

  public IdentifiedUser getUser() {
    return user;
  }

  static class Capability implements RestResource {
    private final IdentifiedUser user;
    private final String capability;

    Capability(IdentifiedUser user, String capability) {
      this.user = user;
      this.capability = capability;
    }

    public IdentifiedUser getUser() {
      return user;
    }

    public String getCapability() {
      return capability;
    }

    public boolean has() {
      return user.getCapabilities().canPerform(getCapability());
    }
  }

  public static class Email extends AccountResource {
    private final String email;

    public Email(IdentifiedUser user, String email) {
      super(user);
      this.email = email;
    }

    public String getEmail() {
      return email;
    }
  }

  static class SshKey extends AccountResource {
    private final AccountSshKey sshKey;

    public SshKey(IdentifiedUser user, AccountSshKey sshKey) {
      super(user);
      this.sshKey = sshKey;
    }

    public AccountSshKey getSshKey() {
      return sshKey;
    }
  }

  static class Project extends AccountResource {
    private final ProjectControl control;

    Project(IdentifiedUser user, ProjectControl control) {
      super(user);
      this.control = control;
    }

    public ProjectControl getControl() {
      return control;
    }
  }

  static class ProjectCapability extends AccountResource.Project {
    private final String capability;

    ProjectCapability(IdentifiedUser user, ProjectControl control,
        String capability) {
      super(user, control);
      this.capability = capability;
    }

    public String getCapability() {
      return capability;
    }
  }
}
