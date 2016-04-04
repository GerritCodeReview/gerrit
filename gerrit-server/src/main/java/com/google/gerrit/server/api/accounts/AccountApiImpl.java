// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.api.accounts;

import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.extensions.api.accounts.AccountApi;
import com.google.gerrit.extensions.api.accounts.EmailInput;
import com.google.gerrit.extensions.api.accounts.GpgKeyApi;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.EditPreferencesInfo;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.GpgKeyInfo;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.server.GpgException;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.CreateEmail;
import com.google.gerrit.server.account.GetAvatar;
import com.google.gerrit.server.account.GetDiffPreferences;
import com.google.gerrit.server.account.GetEditPreferences;
import com.google.gerrit.server.account.GetPreferences;
import com.google.gerrit.server.account.SetDiffPreferences;
import com.google.gerrit.server.account.SetEditPreferences;
import com.google.gerrit.server.account.SetPreferences;
import com.google.gerrit.server.account.StarredChanges;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.ChangesCollection;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.errors.ConfigInvalidException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class AccountApiImpl implements AccountApi {
  interface Factory {
    AccountApiImpl create(AccountResource account);
  }

  private final AccountResource account;
  private final ChangesCollection changes;
  private final AccountLoader.Factory accountLoaderFactory;
  private final Provider<GetAvatar> getAvatar;
  private final GetPreferences getPreferences;
  private final SetPreferences setPreferences;
  private final GetDiffPreferences getDiffPreferences;
  private final SetDiffPreferences setDiffPreferences;
  private final GetEditPreferences getEditPreferences;
  private final SetEditPreferences setEditPreferences;
  private final StarredChanges.Create starredChangesCreate;
  private final StarredChanges.Delete starredChangesDelete;
  private final CreateEmail.Factory createEmailFactory;
  private final GpgApiAdapter gpgApiAdapter;

  @Inject
  AccountApiImpl(AccountLoader.Factory ailf,
      ChangesCollection changes,
      Provider<GetAvatar> getAvatar,
      GetPreferences getPreferences,
      SetPreferences setPreferences,
      GetDiffPreferences getDiffPreferences,
      SetDiffPreferences setDiffPreferences,
      GetEditPreferences getEditPreferences,
      SetEditPreferences setEditPreferences,
      StarredChanges.Create starredChangesCreate,
      StarredChanges.Delete starredChangesDelete,
      CreateEmail.Factory createEmailFactory,
      GpgApiAdapter gpgApiAdapter,
      @Assisted AccountResource account) {
    this.account = account;
    this.accountLoaderFactory = ailf;
    this.changes = changes;
    this.getAvatar = getAvatar;
    this.getPreferences = getPreferences;
    this.setPreferences = setPreferences;
    this.getDiffPreferences = getDiffPreferences;
    this.setDiffPreferences = setDiffPreferences;
    this.getEditPreferences = getEditPreferences;
    this.setEditPreferences = setEditPreferences;
    this.starredChangesCreate = starredChangesCreate;
    this.starredChangesDelete = starredChangesDelete;
    this.createEmailFactory = createEmailFactory;
    this.gpgApiAdapter = gpgApiAdapter;
  }

  @Override
  public com.google.gerrit.extensions.common.AccountInfo get()
      throws RestApiException {
    AccountLoader accountLoader = accountLoaderFactory.create(true);
    try {
      AccountInfo ai = accountLoader.get(account.getUser().getAccountId());
      accountLoader.fill();
      return ai;
    } catch (OrmException e) {
      throw new RestApiException("Cannot parse change", e);
    }
  }

  @Override
  public String getAvatarUrl(int size) throws RestApiException {
    GetAvatar myGetAvatar = getAvatar.get();
    myGetAvatar.setSize(size);
    return myGetAvatar.apply(account).location();
  }

  @Override
  public GeneralPreferencesInfo getPreferences() throws RestApiException {
    return getPreferences.apply(account);
  }

  @Override
  public GeneralPreferencesInfo setPreferences(GeneralPreferencesInfo in)
      throws RestApiException {
    try {
      return setPreferences.apply(account, in);
    } catch (IOException | ConfigInvalidException e) {
      throw new RestApiException("Cannot set preferences", e);
    }
  }

  @Override
  public DiffPreferencesInfo getDiffPreferences() throws RestApiException {
    try {
      return getDiffPreferences.apply(account);
    } catch (IOException | ConfigInvalidException e) {
      throw new RestApiException("Cannot query diff preferences", e);
    }
  }

  @Override
  public DiffPreferencesInfo setDiffPreferences(DiffPreferencesInfo in)
      throws RestApiException {
    try {
      return setDiffPreferences.apply(account, in);
    } catch (IOException | ConfigInvalidException e) {
      throw new RestApiException("Cannot set diff preferences", e);
    }
  }

  @Override
  public EditPreferencesInfo getEditPreferences() throws RestApiException {
    try {
      return getEditPreferences.apply(account);
    } catch (IOException | ConfigInvalidException e) {
      throw new RestApiException("Cannot query edit preferences", e);
    }
  }

  @Override
  public EditPreferencesInfo setEditPreferences(EditPreferencesInfo in)
      throws RestApiException {
    try {
      return setEditPreferences.apply(account, in);
    } catch (IOException | ConfigInvalidException e) {
      throw new RestApiException("Cannot set edit preferences", e);
    }
  }

  @Override
  public void starChange(String id) throws RestApiException {
    try {
      ChangeResource rsrc = changes.parse(
        TopLevelResource.INSTANCE,
        IdString.fromUrl(id));
      starredChangesCreate.setChange(rsrc);
      starredChangesCreate.apply(account, new StarredChanges.EmptyInput());
    } catch (OrmException e) {
      throw new RestApiException("Cannot star change", e);
    }
  }

  @Override
  public void unstarChange(String id) throws RestApiException {
    try {
      ChangeResource rsrc =
          changes.parse(TopLevelResource.INSTANCE, IdString.fromUrl(id));
      AccountResource.StarredChange starredChange =
          new AccountResource.StarredChange(account.getUser(), rsrc);
      starredChangesDelete.apply(starredChange,
          new StarredChanges.EmptyInput());
    } catch (OrmException e) {
      throw new RestApiException("Cannot unstar change", e);
    }
  }

  @Override
  public void addEmail(EmailInput input) throws RestApiException {
    AccountResource.Email rsrc =
        new AccountResource.Email(account.getUser(), input.email);
    try {
      createEmailFactory.create(input.email).apply(rsrc, input);
    } catch (EmailException | OrmException e) {
      throw new RestApiException("Cannot add email", e);
    }
  }

  @Override
  public Map<String, GpgKeyInfo> listGpgKeys() throws RestApiException {
    try {
      return gpgApiAdapter.listGpgKeys(account);
    } catch (GpgException e) {
      throw new RestApiException("Cannot list GPG keys", e);
    }
  }

  @Override
  public Map<String, GpgKeyInfo> putGpgKeys(List<String> add,
      List<String> delete) throws RestApiException {
    try {
      return gpgApiAdapter.putGpgKeys(account, add, delete);
    } catch (GpgException e) {
      throw new RestApiException("Cannot add GPG key", e);
    }
  }

  @Override
  public GpgKeyApi gpgKey(String id) throws RestApiException {
    try {
      return gpgApiAdapter.gpgKey(account, IdString.fromDecoded(id));
    } catch (GpgException e) {
      throw new RestApiException("Cannot get PGP key", e);
    }
  }
}
