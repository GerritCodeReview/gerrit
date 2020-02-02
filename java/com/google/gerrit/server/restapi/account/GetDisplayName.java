// Copyright (C) 2017 The Android Open Source Project
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

package java.com.google.gerrit.server.restapi.account;

import com.google.common.base.Strings;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.account.AccountResource;
import com.google.inject.Singleton;

/**
 * REST endpoint to get the status of an account.
 *
 * <p>This REST endpoint handles {@code GET /accounts/<account-identifier>/displayname} requests.
 *
 * <p>The display name is a free-form text that a user can set for the own account. It defines how
 * the user's name will be rendered in the UI in most screens. It is optional, and if not set, then
 * the UI falls back to whatever is configured as the default display name, e.g. the full name.
 */
@Singleton
public class GetDisplayName implements RestReadView<AccountResource> {
  @Override
  public Response<String> apply(AccountResource rsrc) {
    return Response.ok(Strings.nullToEmpty(rsrc.getUser().getAccount().displayName()));
  }
}
