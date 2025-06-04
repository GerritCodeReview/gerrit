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

package com.google.gerrit.server.restapi.account.sshkeys;

import static com.google.gerrit.server.account.AccountResource.ACCOUNT_KIND;
import static com.google.gerrit.server.account.AccountResource.SSH_KEY_KIND;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestApiModule;

/** Guice module that binds all REST endpoints for {@code /accounts/<account-id>/sshkeys}. */
public class AccountSshKeysRestApiModule extends RestApiModule {
  @Override
  protected void configure() {
    DynamicMap.mapOf(binder(), SSH_KEY_KIND);

    /** List account SSH keys {@code GET /accounts/<account-id>/sshkeys}. */
    child(ACCOUNT_KIND, "sshkeys").to(SshKeys.class);
    /** Add account SSH key {@code POST /accounts/<account-id>/sshkeys}. */
    postOnCollection(SSH_KEY_KIND).to(AddSshKey.class);

    /** Get account SSH key {@code GET /accounts/<account-id>/sshkeys/<ssh-key-id>}. */
    get(SSH_KEY_KIND).to(GetSshKey.class);
    /** Delete account SSH key {@code DELETE /accounts/<account-id>/sshkeys/<ssh-key-id>}. */
    delete(SSH_KEY_KIND).to(DeleteSshKey.class);
  }
}
