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

package com.google.gerrit.server.ssh;

import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.ssh.SshScopes.Context;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;

class SshCurrentUserProvider implements Provider<IdentifiedUser> {
  private final IdentifiedUser.Factory factory;

  @Inject
  SshCurrentUserProvider(final IdentifiedUser.Factory f) {
    factory = f;
  }

  @Override
  public IdentifiedUser get() {
    final Context ctx = SshScopes.getContext();
    final Account.Id id = ctx.session.getAttribute(SshUtil.CURRENT_ACCOUNT);
    if (id == null) {
      throw new ProvisionException("User not yet authenticated");
    }
    return factory.create(id);
  }
}
