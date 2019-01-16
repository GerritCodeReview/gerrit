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

package com.google.gerrit.server.restapi.group;

import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.group.MemberResource;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class GetMember implements RestReadView<MemberResource> {
  private final AccountLoader.Factory infoFactory;

  @Inject
  GetMember(AccountLoader.Factory infoFactory) {
    this.infoFactory = infoFactory;
  }

  @Override
  public AccountInfo apply(MemberResource rsrc) throws PermissionBackendException {
    AccountLoader loader = infoFactory.create(true);
    AccountInfo info = loader.get(rsrc.getMember().getAccountId());
    loader.fill();
    return info;
  }
}
