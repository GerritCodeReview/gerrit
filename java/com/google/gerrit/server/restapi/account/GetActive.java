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

import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.account.AccountResource;
import com.google.inject.Singleton;

/**
 * REST endpoint to get the active state of an account.
 *
 * <p>This REST endpoint handles {@code GET /accounts/<account-identifier>/active} requests.
 *
 * <p>Only active accounts can login into Gerrit.
 */
@Singleton
public class GetActive implements RestReadView<AccountResource> {
  @Override
  public Response<String> apply(AccountResource rsrc) {
    if (rsrc.getUser().getAccount().isActive()) {
      return Response.ok("ok");
    }
    return Response.none();
  }
}
