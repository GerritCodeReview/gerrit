// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.server.restapi.account.starred.changes;

import static com.google.gerrit.server.account.AccountResource.ACCOUNT_KIND;
import static com.google.gerrit.server.account.AccountResource.STARRED_CHANGE_KIND;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestApiModule;

/**
 * Guice module that binds all REST endpoints for {@code /accounts/<account-id>/starred.changes}.
 */
public class AccountStarredChangesRestApiModule extends RestApiModule {
  @Override
  protected void configure() {
    bind(StarredChanges.Create.class);

    DynamicMap.mapOf(binder(), STARRED_CHANGE_KIND);

    /** List changes starred by account {@code GET /accounts/<account-id>/starred.changes}. */
    child(ACCOUNT_KIND, "starred.changes").to(StarredChanges.class);

    /** Put star on change {@code PUT /accounts/<account-id>/starred.changes/<change-id>}. */
    create(STARRED_CHANGE_KIND).to(StarredChanges.Create.class);
    /** Update star on change {@code PUT /accounts/<account-id>/starred.changes/<change-id>}. */
    put(STARRED_CHANGE_KIND).to(StarredChanges.Put.class);
    /**
     * Remove star from change {@code DELETE /accounts/<account-id>/starred.changes/<change-id>}.
     */
    delete(STARRED_CHANGE_KIND).to(StarredChanges.Delete.class);
  }
}
