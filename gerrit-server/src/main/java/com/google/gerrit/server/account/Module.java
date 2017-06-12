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

import static com.google.gerrit.server.account.AccountResource.ACCOUNT_KIND;
import static com.google.gerrit.server.account.AccountResource.CAPABILITY_KIND;
import static com.google.gerrit.server.account.AccountResource.EMAIL_KIND;
import static com.google.gerrit.server.account.AccountResource.SSH_KEY_KIND;
import static com.google.gerrit.server.account.AccountResource.STARRED_CHANGE_KIND;
import static com.google.gerrit.server.account.AccountResource.Star.STAR_KIND;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestApiModule;

public class Module extends RestApiModule {
  @Override
  protected void configure() {
    bind(AccountsCollection.class);
    bind(Capabilities.class);

    DynamicMap.mapOf(binder(), ACCOUNT_KIND);
    DynamicMap.mapOf(binder(), CAPABILITY_KIND);
    DynamicMap.mapOf(binder(), EMAIL_KIND);
    DynamicMap.mapOf(binder(), SSH_KEY_KIND);
    DynamicMap.mapOf(binder(), STARRED_CHANGE_KIND);
    DynamicMap.mapOf(binder(), STAR_KIND);

    put(ACCOUNT_KIND).to(PutAccount.class);
    get(ACCOUNT_KIND).to(GetAccount.class);
    get(ACCOUNT_KIND, "detail").to(GetDetail.class);
    get(ACCOUNT_KIND, "name").to(GetName.class);
    put(ACCOUNT_KIND, "name").to(PutName.class);
    delete(ACCOUNT_KIND, "name").to(PutName.class);
    get(ACCOUNT_KIND, "username").to(GetUsername.class);
    put(ACCOUNT_KIND, "username").to(PutUsername.class);
    get(ACCOUNT_KIND, "active").to(GetActive.class);
    put(ACCOUNT_KIND, "active").to(PutActive.class);
    delete(ACCOUNT_KIND, "active").to(DeleteActive.class);
    child(ACCOUNT_KIND, "emails").to(Emails.class);
    get(EMAIL_KIND).to(GetEmail.class);
    put(EMAIL_KIND).to(PutEmail.class);
    delete(EMAIL_KIND).to(DeleteEmail.class);
    put(EMAIL_KIND, "preferred").to(PutPreferred.class);
    get(ACCOUNT_KIND, "password.http").to(GetHttpPassword.class);
    put(ACCOUNT_KIND, "password.http").to(PutHttpPassword.class);
    delete(ACCOUNT_KIND, "password.http").to(PutHttpPassword.class);
    child(ACCOUNT_KIND, "sshkeys").to(SshKeys.class);
    post(ACCOUNT_KIND, "sshkeys").to(AddSshKey.class);
    get(ACCOUNT_KIND, "watched.projects").to(GetWatchedProjects.class);
    post(ACCOUNT_KIND, "watched.projects").to(PostWatchedProjects.class);
    post(ACCOUNT_KIND, "watched.projects:delete").to(DeleteWatchedProjects.class);

    get(SSH_KEY_KIND).to(GetSshKey.class);
    delete(SSH_KEY_KIND).to(DeleteSshKey.class);

    get(ACCOUNT_KIND, "oauthtoken").to(GetOAuthToken.class);

    get(ACCOUNT_KIND, "avatar").to(GetAvatar.class);
    get(ACCOUNT_KIND, "avatar.change.url").to(GetAvatarChangeUrl.class);

    child(ACCOUNT_KIND, "capabilities").to(Capabilities.class);

    get(ACCOUNT_KIND, "groups").to(GetGroups.class);
    get(ACCOUNT_KIND, "preferences").to(GetPreferences.class);
    put(ACCOUNT_KIND, "preferences").to(SetPreferences.class);
    get(ACCOUNT_KIND, "preferences.diff").to(GetDiffPreferences.class);
    put(ACCOUNT_KIND, "preferences.diff").to(SetDiffPreferences.class);
    get(ACCOUNT_KIND, "preferences.edit").to(GetEditPreferences.class);
    put(ACCOUNT_KIND, "preferences.edit").to(SetEditPreferences.class);
    get(CAPABILITY_KIND).to(GetCapabilities.CheckOne.class);

    get(ACCOUNT_KIND, "agreements").to(GetAgreements.class);
    put(ACCOUNT_KIND, "agreements").to(PutAgreement.class);

    child(ACCOUNT_KIND, "starred.changes").to(StarredChanges.class);
    put(STARRED_CHANGE_KIND).to(StarredChanges.Put.class);
    delete(STARRED_CHANGE_KIND).to(StarredChanges.Delete.class);
    bind(StarredChanges.Create.class);

    child(ACCOUNT_KIND, "stars.changes").to(Stars.class);
    get(STAR_KIND).to(Stars.Get.class);
    post(STAR_KIND).to(Stars.Post.class);

    factory(CreateAccount.Factory.class);
    factory(CreateEmail.Factory.class);
  }
}
