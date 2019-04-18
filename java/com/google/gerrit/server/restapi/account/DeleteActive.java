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

package com.google.gerrit.server.restapi.account;

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.common.Input;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.SetInactiveFlag;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

@RequiresCapability(GlobalCapability.MODIFY_ACCOUNT)
@Singleton
public class DeleteActive implements RestModifyView<AccountResource, Input> {

  private final Provider<IdentifiedUser> self;
  private final SetInactiveFlag setInactiveFlag;

  @Inject
  DeleteActive(SetInactiveFlag setInactiveFlag, Provider<IdentifiedUser> self) {
    this.setInactiveFlag = setInactiveFlag;
    this.self = self;
  }

  @Override
  public Response<?> apply(AccountResource rsrc, Input input)
      throws RestApiException, IOException, ConfigInvalidException {
    if (self.get().hasSameAccountId(rsrc.getUser())) {
      throw new ResourceConflictException("cannot deactivate own account");
    }
    return setInactiveFlag.deactivate(rsrc.getUser().getAccountId());
  }
}
