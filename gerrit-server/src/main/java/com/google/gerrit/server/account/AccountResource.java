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
import com.google.gerrit.server.IdentifiedUser;
import com.google.inject.TypeLiteral;

public class AccountResource implements RestResource {
  public static final TypeLiteral<RestView<AccountResource>> ACCOUNT_KIND =
      new TypeLiteral<RestView<AccountResource>>() {};

  private final IdentifiedUser user;

  AccountResource(IdentifiedUser user) {
    this.user = user;
  }

  public IdentifiedUser getUser() {
    return user;
  }
}
