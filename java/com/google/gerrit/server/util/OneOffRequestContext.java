// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.util;

import com.google.gerrit.entities.Account;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.InternalUser;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * Helper to create one-off request contexts.
 *
 * <p>The user in the request context is {@link InternalUser} or the {@link IdentifiedUser}
 * associated to the userId passed as parameter.
 */
@Singleton
public class OneOffRequestContext {
  private final InternalUser.Factory userFactory;
  private final ThreadLocalRequestContext requestContext;
  private final IdentifiedUser.GenericFactory identifiedUserFactory;

  @Inject
  OneOffRequestContext(
      InternalUser.Factory userFactory,
      ThreadLocalRequestContext requestContext,
      IdentifiedUser.GenericFactory identifiedUserFactory) {
    this.userFactory = userFactory;
    this.requestContext = requestContext;
    this.identifiedUserFactory = identifiedUserFactory;
  }

  public ManualRequestContext open() {
    return new ManualRequestContext(userFactory.create(), requestContext);
  }

  public ManualRequestContext openAs(Account.Id userId) {
    return new ManualRequestContext(identifiedUserFactory.create(userId), requestContext);
  }
}
