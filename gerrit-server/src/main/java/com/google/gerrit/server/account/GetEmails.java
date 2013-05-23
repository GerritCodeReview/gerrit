// Copyright (C) 2013 The Android Open Source Project
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

import com.google.common.collect.Lists;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.CurrentUser;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class GetEmails implements RestReadView<AccountResource> {
  private final Provider<CurrentUser> self;

  @Inject
  public GetEmails(Provider<CurrentUser> self) {
    this.self = self;
  }

  @Override
  public List<EmailInfo> apply(AccountResource rsrc) throws AuthException,
      OrmException {
    if (self.get() != rsrc.getUser()
        && !self.get().getCapabilities().canAdministrateServer()) {
      throw new AuthException("not allowed to list email addresses");
    }

    List<EmailInfo> emails = Lists.newArrayList();
    for (String email : rsrc.getUser().getEmailAddresses()) {
      if (email != null) {
        EmailInfo e = new EmailInfo();
        e.email = email;
        e.preferred(rsrc.getUser().getAccount().getPreferredEmail());
        emails.add(e);
      }
    }
    Collections.sort(emails, new Comparator<EmailInfo>() {
      @Override
      public int compare(EmailInfo a, EmailInfo b) {
        return a.email.compareTo(b.email);
      }
    });
    return emails;
  }

  public static class EmailInfo {
    public String email;
    public Boolean preferred;
    public Boolean pendingConfirmation;

    void preferred(String e) {
      this.preferred = e != null && e.equals(email) ? true : null;
    }
  }
}
