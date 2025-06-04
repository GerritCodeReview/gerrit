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

package com.google.gerrit.server.restapi.account;

import static com.google.gerrit.server.account.AccountResource.ACCOUNT_KIND;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestApiModule;
import com.google.gerrit.server.restapi.account.capabilities.AccountCapabilitiesRestApiModule;
import com.google.gerrit.server.restapi.account.emails.AccountEmailsRestApiModule;
import com.google.gerrit.server.restapi.account.sshkeys.AccountSshKeysRestApiModule;
import com.google.gerrit.server.restapi.account.starred.changes.AccountStarredChangesRestApiModule;

/** Guice module that binds all REST endpoints for {@code /accounts/}. */
public class AccountRestApiModule extends RestApiModule {
  @Override
  protected void configure() {
    bind(AccountsCollection.class).to(AccountsCollectionImpl.class);

    DynamicMap.mapOf(binder(), ACCOUNT_KIND);

    /** Create account {@code PUT /accounts/<username>}. */
    create(ACCOUNT_KIND).to(CreateAccount.class);
    /** Get account {@code GET /accounts/<account-id>}. */
    get(ACCOUNT_KIND).to(GetAccount.class);
    /** Put account {@code PUT /accounts/<account-id>}. */
    put(ACCOUNT_KIND).to(PutAccount.class);
    /** Delete account {@code DELETE /accounts/<account-id>}. */
    delete(ACCOUNT_KIND).to(DeleteAccount.class);

    /** Get account active status {@code GET /accounts/<account-id>/active}. */
    get(ACCOUNT_KIND, "active").to(GetActive.class);
    /** Activate account {@code PUT /accounts/<account-id>/active}. */
    put(ACCOUNT_KIND, "active").to(PutActive.class);
    /** Inactivate account {@code DELETE /accounts/<account-id>/active}. */
    delete(ACCOUNT_KIND, "active").to(DeleteActive.class);

    /** List account contributor agreements {@code GET /accounts/<account-id>/agreements}. */
    get(ACCOUNT_KIND, "agreements").to(GetAgreements.class);
    /** Sign account contributor agreement {@code PUT /accounts/<account-id>/agreements}. */
    put(ACCOUNT_KIND, "agreements").to(PutAgreement.class);

    /** Get account avatar {@code GET /accounts/<account-id>/avatar}. */
    get(ACCOUNT_KIND, "avatar").to(GetAvatar.class);
    /** Get URL to change account's avatar {@code GET /accounts/<account-id>/avatar.change.url}. */
    get(ACCOUNT_KIND, "avatar.change.url").to(GetAvatarChangeUrl.class);

    /** Get account details {@code GET /accounts/<account-id>/detail}. */
    get(ACCOUNT_KIND, "detail").to(GetDetail.class);

    /** Update account displayname {@code PUT /accounts/<account-id>/displayname}. */
    put(ACCOUNT_KIND, "displayname").to(PutDisplayName.class);

    /** Delete account's draft comments {@code POST /accounts/<account-id>/drafts:delete}. */
    post(ACCOUNT_KIND, "drafts:delete").to(DeleteDraftComments.class);

    /** Get external IDs of account {@code GET /accounts/<account-id>/external.ids}. */
    get(ACCOUNT_KIND, "external.ids").to(GetExternalIds.class);
    /** Delete external IDs of account {@code POST /accounts/<account-id>/external.ids:delete}. */
    post(ACCOUNT_KIND, "external.ids:delete").to(DeleteExternalIds.class);

    /** Get groups in which the account is a member {@code GET /accounts/<account-id>/groups}. */
    get(ACCOUNT_KIND, "groups").to(GetGroups.class);

    /** Add/Update index entry for account {@code POST /accounts/<account-id>/index}. */
    post(ACCOUNT_KIND, "index").to(Index.class);

    /** Get full name of account {@code GET /accounts/<account-id>/name}. */
    get(ACCOUNT_KIND, "name").to(GetName.class);
    /** Set full name of account {@code PUT /accounts/<account-id>/name}. */
    put(ACCOUNT_KIND, "name").to(PutName.class);
    /** Delete full name of account {@code DELETE /accounts/<account-id>/name}. */
    delete(ACCOUNT_KIND, "name").to(PutName.class);

    /** Generate/Set HTTP password for account {@code PUT /accounts/<account-id>/password.http}. */
    put(ACCOUNT_KIND, "password.http").to(PutHttpPassword.class);
    /** Unset HTTP password for account {@code DELETE /accounts/<account-id>/password.http}. */
    delete(ACCOUNT_KIND, "password.http").to(PutHttpPassword.class);

    /** Get account preferences {@code GET /accounts/<account-id>/preferences}. */
    get(ACCOUNT_KIND, "preferences").to(GetPreferences.class);
    /** Update account preferences {@code PUT /accounts/<account-id>/preferences}. */
    put(ACCOUNT_KIND, "preferences").to(SetPreferences.class);

    /** Get account diff preferences {@code GET /accounts/<account-id>/preferences.diff}. */
    get(ACCOUNT_KIND, "preferences.diff").to(GetDiffPreferences.class);
    /** Update account diff preferences {@code PUT /accounts/<account-id>/preferences.diff}. */
    put(ACCOUNT_KIND, "preferences.diff").to(SetDiffPreferences.class);
    /** Get account edit preferences {@code GET /accounts/<account-id>/preferences.edit}. */
    get(ACCOUNT_KIND, "preferences.edit").to(GetEditPreferences.class);
    /** Update account edit preferences {@code PUT /accounts/<account-id>/preferences.edit}. */
    put(ACCOUNT_KIND, "preferences.edit").to(SetEditPreferences.class);

    /**
     * Get account state (complete account information) {@code GET /accounts/<account-id>/state}.
     */
    get(ACCOUNT_KIND, "state").to(GetState.class);

    /** Get account status message {@code GET /accounts/<account-id>/status}. */
    get(ACCOUNT_KIND, "status").to(GetStatus.class);
    /** Update account status message {@code PUT /accounts/<account-id>/status}. */
    put(ACCOUNT_KIND, "status").to(PutStatus.class);

    /** Get account username {@code GET /accounts/<account-id>/username}. */
    get(ACCOUNT_KIND, "username").to(GetUsername.class);
    /** Update account username {@code PUT /accounts/<account-id>/username}. */
    put(ACCOUNT_KIND, "username").to(PutUsername.class);

    /** Get projects watched by account {@code GET /accounts/<account-id>/watched.projects}. */
    get(ACCOUNT_KIND, "watched.projects").to(GetWatchedProjects.class);
    /**
     * Add/Update projects watched by account {@code POST /accounts/<account-id>/watched.projects}.
     */
    post(ACCOUNT_KIND, "watched.projects").to(PostWatchedProjects.class);
    /**
     * Delete projects watched by account {@code POST
     * /accounts/<account-id>/watched.projects:delete}.
     */
    post(ACCOUNT_KIND, "watched.projects:delete").to(DeleteWatchedProjects.class);

    // The gpgkeys REST endpoints are bound via GpgApiModule.
    // The oauthtoken REST endpoint is bound via OAuthRestModule.

    /** Module for {@code /accounts/<account-id>/capabilities}. */
    install(new AccountCapabilitiesRestApiModule());
    /** Module for {@code /accounts/<account-id>/emails}. */
    install(new AccountEmailsRestApiModule());
    /** Module for {@code /accounts/<account-id>/sshkeys}. */
    install(new AccountSshKeysRestApiModule());
    /** Module for {@code /accounts/<account-id>/starred.changes}. */
    install(new AccountStarredChangesRestApiModule());
  }
}
