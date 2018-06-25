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

package com.google.gerrit.server.restapi.account;

import static com.google.gerrit.server.account.AccountResource.ACCOUNT_KIND;

import com.google.gerrit.extensions.restapi.RestApiEndpoint;
import com.google.gerrit.extensions.restapi.RestEndpointType;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.server.account.AccountResource;
import com.google.inject.TypeLiteral;

/** REST endpionts for accounts. */
public enum AccountRestApiEndpoint implements RestApiEndpoint<AccountResource> {
  CREATE_ACCOUNT(ACCOUNT_KIND, RestEndpointType.CREATE, CreateAccount.class);

  private TypeLiteral type;
  private RestEndpointType requestMethod;
  private String name;

  AccountRestApiEndpoint(
      TypeLiteral viewType, RestEndpointType requestMethod, Class endpointClass) {
    this.type = viewType;
    this.requestMethod = requestMethod;
    this.name = "/";
  }

  @Override
  public TypeLiteral<RestView<AccountResource>> getType() {
    return type;
  }

  @Override
  public RestEndpointType getMethod() {
    return requestMethod;
  }

  @Override
  public String getName() {
    return name;
  }
}
