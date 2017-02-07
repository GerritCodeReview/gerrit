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
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.ChangeResource;
import com.google.inject.TypeLiteral;
import java.util.Set;

public class AccountResource implements RestResource {
  public static final TypeLiteral<RestView<AccountResource>> ACCOUNT_KIND =
      new TypeLiteral<RestView<AccountResource>>() {};

  public static final TypeLiteral<RestView<Capability>> CAPABILITY_KIND =
      new TypeLiteral<RestView<Capability>>() {};

  public static final TypeLiteral<RestView<Email>> EMAIL_KIND =
      new TypeLiteral<RestView<Email>>() {};

  public static final TypeLiteral<RestView<SshKey>> SSH_KEY_KIND =
      new TypeLiteral<RestView<SshKey>>() {};

  public static final TypeLiteral<RestView<StarredChange>> STARRED_CHANGE_KIND =
      new TypeLiteral<RestView<StarredChange>>() {};

  private final IdentifiedUser user;

  public AccountResource(IdentifiedUser user) {
    this.user = user;
  }

  public IdentifiedUser getUser() {
    return user;
  }

  public static class Capability implements RestResource {
    private final IdentifiedUser user;
    private final String capability;

    public Capability(IdentifiedUser user, String capability) {
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

  public static class SshKey extends AccountResource {
    private final AccountSshKey sshKey;

    public SshKey(IdentifiedUser user, AccountSshKey sshKey) {
      super(user);
      this.sshKey = sshKey;
    }

    public AccountSshKey getSshKey() {
      return sshKey;
    }
  }

  public static class StarredChange extends AccountResource {
    private final ChangeResource change;

    public StarredChange(IdentifiedUser user, ChangeResource change) {
      super(user);
      this.change = change;
    }

    public Change getChange() {
      return change.getChange();
    }
  }

  public static class Star implements RestResource {
    public static final TypeLiteral<RestView<Star>> STAR_KIND =
        new TypeLiteral<RestView<Star>>() {};

    private final IdentifiedUser user;
    private final ChangeResource change;
    private final Set<String> labels;

    public Star(IdentifiedUser user, ChangeResource change, Set<String> labels) {
      this.user = user;
      this.change = change;
      this.labels = labels;
    }

    public IdentifiedUser getUser() {
      return user;
    }

    public Change getChange() {
      return change.getChange();
    }

    public Set<String> getLabels() {
      return labels;
    }
  }
}
