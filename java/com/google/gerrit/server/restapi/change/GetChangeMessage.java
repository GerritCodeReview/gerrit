// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server.restapi.change;

import static com.google.gerrit.server.ChangeMessagesUtil.createChangeMessageInfo;

import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.change.ChangeMessageResource;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class GetChangeMessage implements RestReadView<ChangeMessageResource> {
  private final AccountLoader accountLoader;

  @Inject
  GetChangeMessage(AccountLoader.Factory accountLoaderFactory) {
    this.accountLoader = accountLoaderFactory.create(true);
  }

  @Override
  public ChangeMessageInfo apply(ChangeMessageResource resource) throws OrmException {
    // Checks the change is visible to the user.

    ChangeMessageInfo info = createChangeMessageInfo(resource.getChangeMessage(), accountLoader);
    accountLoader.fill();
    return info;
  }
}
