// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.http;

import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.servlet.RequestScoped;

@RequestScoped
class HttpCurrentUserProvider implements Provider<CurrentUser> {
  private final GerritCall call;
  private final IdentifiedUser.Factory factory;

  @Inject
  HttpCurrentUserProvider(final GerritCall c, final IdentifiedUser.Factory f) {
    call = c;
    factory = f;
  }

  @Override
  public CurrentUser get() {
    final Account.Id id = call.getAccountId();
    return id != null ? factory.create(id) : CurrentUser.ANONYMOUS;
  }
}
