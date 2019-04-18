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
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
public class ListChangeMessages implements RestReadView<ChangeResource> {
  private final ChangeMessagesUtil changeMessagesUtil;
  private final AccountLoader accountLoader;

  @Inject
  public ListChangeMessages(
      ChangeMessagesUtil changeMessagesUtil, AccountLoader.Factory accountLoaderFactory) {
    this.changeMessagesUtil = changeMessagesUtil;
    this.accountLoader = accountLoaderFactory.create(true);
  }

  @Override
  public List<ChangeMessageInfo> apply(ChangeResource resource) throws PermissionBackendException {
    List<ChangeMessage> messages = changeMessagesUtil.byChange(resource.getNotes());
    List<ChangeMessageInfo> messageInfos =
        messages.stream()
            .map(m -> createChangeMessageInfo(m, accountLoader))
            .collect(Collectors.toList());
    accountLoader.fill();
    return messageInfos;
  }
}
