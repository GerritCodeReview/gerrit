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
import static com.google.gerrit.server.account.AccountResource.CAPABILITY_KIND;
import static com.google.gerrit.server.account.AccountResource.EMAIL_KIND;
import static com.google.gerrit.server.account.AccountResource.SSH_KEY_KIND;
import static com.google.gerrit.server.account.AccountResource.STARRED_CHANGE_KIND;
import static com.google.gerrit.server.account.AccountResource.Star.STAR_KIND;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestApiModule;
import com.google.gerrit.gpg.PublicKeyStore;
import com.google.gerrit.gpg.UnimplementedPublicKeyStoreProvider;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.UserInitiated;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.inject.Provides;
import com.google.inject.multibindings.OptionalBinder;

public class AccountRestApiModule extends RestApiModule {
  @Override
  protected void configure() {
    bind(AccountsCollection.class);
    bind(Capabilities.class);
    bind(StarredChanges.Create.class);

    OptionalBinder.newOptionalBinder(binder(), PublicKeyStore.class)
        .setDefault()
        .toProvider(UnimplementedPublicKeyStoreProvider.class);

    DynamicMap.mapOf(binder(), ACCOUNT_KIND);
    DynamicMap.mapOf(binder(), CAPABILITY_KIND);
    DynamicMap.mapOf(binder(), EMAIL_KIND);
    DynamicMap.mapOf(binder(), SSH_KEY_KIND);
    DynamicMap.mapOf(binder(), STAR_KIND);
    DynamicMap.mapOf(binder(), STARRED_CHANGE_KIND);

    create(ACCOUNT_KIND).to(CreateAccount.class);
    put(ACCOUNT_KIND).to(PutAccount.class);
    delete(ACCOUNT_KIND).to(DeleteAccount.class);
    get(ACCOUNT_KIND).to(GetAccount.class);
    get(ACCOUNT_KIND, "active").to(GetActive.class);
    put(ACCOUNT_KIND, "active").to(PutActive.class);
    delete(ACCOUNT_KIND, "active").to(DeleteActive.class);
    get(ACCOUNT_KIND, "agreements").to(GetAgreements.class);
    put(ACCOUNT_KIND, "agreements").to(PutAgreement.class);
    get(ACCOUNT_KIND, "avatar").to(GetAvatar.class);
    get(ACCOUNT_KIND, "avatar.change.url").to(GetAvatarChangeUrl.class);

    child(ACCOUNT_KIND, "capabilities").to(Capabilities.class);
    get(CAPABILITY_KIND).to(GetCapabilities.CheckOne.class);

    put(ACCOUNT_KIND, "displayname").to(PutDisplayName.class);
    get(ACCOUNT_KIND, "detail").to(GetDetail.class);
    post(ACCOUNT_KIND, "drafts:delete").to(DeleteDraftComments.class);

    child(ACCOUNT_KIND, "emails").to(EmailsCollection.class);
    create(EMAIL_KIND).to(CreateEmail.class);
    delete(EMAIL_KIND).to(DeleteEmail.class);
    get(EMAIL_KIND).to(GetEmail.class);
    put(EMAIL_KIND).to(PutEmail.class);
    put(EMAIL_KIND, "preferred").to(PutPreferred.class);

    get(ACCOUNT_KIND, "external.ids").to(GetExternalIds.class);
    post(ACCOUNT_KIND, "external.ids:delete").to(DeleteExternalIds.class);
    get(ACCOUNT_KIND, "groups").to(GetGroups.class);
    post(ACCOUNT_KIND, "index").to(Index.class);
    get(ACCOUNT_KIND, "name").to(GetName.class);
    put(ACCOUNT_KIND, "name").to(PutName.class);
    delete(ACCOUNT_KIND, "name").to(PutName.class);
    put(ACCOUNT_KIND, "password.http").to(PutHttpPassword.class);
    delete(ACCOUNT_KIND, "password.http").to(PutHttpPassword.class);
    get(ACCOUNT_KIND, "preferences").to(GetPreferences.class);
    put(ACCOUNT_KIND, "preferences").to(SetPreferences.class);
    get(ACCOUNT_KIND, "preferences.diff").to(GetDiffPreferences.class);
    put(ACCOUNT_KIND, "preferences.diff").to(SetDiffPreferences.class);
    get(ACCOUNT_KIND, "preferences.edit").to(GetEditPreferences.class);
    put(ACCOUNT_KIND, "preferences.edit").to(SetEditPreferences.class);

    child(ACCOUNT_KIND, "sshkeys").to(SshKeys.class);
    postOnCollection(SSH_KEY_KIND).to(AddSshKey.class);
    get(SSH_KEY_KIND).to(GetSshKey.class);
    delete(SSH_KEY_KIND).to(DeleteSshKey.class);

    child(ACCOUNT_KIND, "starred.changes").to(StarredChanges.class);
    create(STARRED_CHANGE_KIND).to(StarredChanges.Create.class);
    put(STARRED_CHANGE_KIND).to(StarredChanges.Put.class);
    delete(STARRED_CHANGE_KIND).to(StarredChanges.Delete.class);

    get(ACCOUNT_KIND, "status").to(GetStatus.class);
    put(ACCOUNT_KIND, "status").to(PutStatus.class);
    get(ACCOUNT_KIND, "username").to(GetUsername.class);
    put(ACCOUNT_KIND, "username").to(PutUsername.class);
    get(ACCOUNT_KIND, "watched.projects").to(GetWatchedProjects.class);
    post(ACCOUNT_KIND, "watched.projects").to(PostWatchedProjects.class);
    post(ACCOUNT_KIND, "watched.projects:delete").to(DeleteWatchedProjects.class);

    // The gpgkeys REST endpoints are bound via GpgApiModule.
    // The oauthtoken REST endpoint is bound via OAuthRestModule.
  }

  @Provides
  @ServerInitiated
  AccountsUpdate provideServerInitiatedAccountsUpdate(
      @AccountsUpdate.AccountsUpdateLoader.WithReindex
          AccountsUpdate.AccountsUpdateLoader accountsUpdateFactory) {
    return accountsUpdateFactory.createWithServerIdent();
  }

  @Provides
  @UserInitiated
  AccountsUpdate provideUserInitiatedAccountsUpdate(
      @AccountsUpdate.AccountsUpdateLoader.WithReindex
          AccountsUpdate.AccountsUpdateLoader accountsUpdateFactory,
      IdentifiedUser currentUser) {
    return accountsUpdateFactory.create(currentUser);
  }
}
