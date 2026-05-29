// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.acceptance.api.accounts;

import com.google.gerrit.extensions.client.AccountFieldName;
import com.google.gerrit.server.account.DefaultRealm;
import com.google.gerrit.server.account.EmailExpander;
import com.google.gerrit.server.account.Emails;
import com.google.gerrit.server.config.AuthConfig;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.HashSet;
import java.util.Set;

@Singleton
public class TestRealm extends DefaultRealm {

  private final Set<AccountFieldName> readOnlyFields = new HashSet<>();

  @Inject
  public TestRealm(EmailExpander emailExpander, Provider<Emails> emails, AuthConfig authConfig) {
    super(emailExpander, emails, authConfig);
  }

  public void denyEdit(AccountFieldName field) {
    readOnlyFields.add(field);
  }

  @Override
  public boolean allowsEdit(AccountFieldName field) {
    return !readOnlyFields.contains(field);
  }
}
