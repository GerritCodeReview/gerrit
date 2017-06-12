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

import static javax.servlet.http.HttpServletResponse.SC_OK;

import com.google.gerrit.common.RawInputUtil;
import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.extensions.api.accounts.AccountApi;
import com.google.gerrit.extensions.api.accounts.EmailInput;
import com.google.gerrit.extensions.api.accounts.GpgKeyApi;
import com.google.gerrit.extensions.api.changes.StarsInput;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.EditPreferencesInfo;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.extensions.client.ProjectWatchInfo;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.AgreementInfo;
import com.google.gerrit.extensions.common.AgreementInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.GpgKeyInfo;
import com.google.gerrit.extensions.common.SshKeyInfo;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.server.GpgException;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.AddSshKey;
import com.google.gerrit.server.account.CreateEmail;
import com.google.gerrit.server.account.DeleteActive;
import com.google.gerrit.server.account.DeleteSshKey;
import com.google.gerrit.server.account.DeleteWatchedProjects;
import com.google.gerrit.server.account.GetActive;
import com.google.gerrit.server.account.GetAgreements;
import com.google.gerrit.server.account.GetAvatar;
import com.google.gerrit.server.account.GetDiffPreferences;
import com.google.gerrit.server.account.GetEditPreferences;
import com.google.gerrit.server.account.GetPreferences;
import com.google.gerrit.server.account.GetSshKeys;
import com.google.gerrit.server.account.GetWatchedProjects;
import com.google.gerrit.server.account.PostWatchedProjects;
import com.google.gerrit.server.account.PutActive;
import com.google.gerrit.server.account.PutAgreement;
import com.google.gerrit.server.account.SetDiffPreferences;
import com.google.gerrit.server.account.SetEditPreferences;
import com.google.gerrit.server.account.SetPreferences;
import com.google.gerrit.server.account.SshKeys;
import com.google.gerrit.server.account.StarredChanges;
import com.google.gerrit.server.account.Stars;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.ChangesCollection;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import org.eclipse.jgit.errors.ConfigInvalidException;

public class AccountApiImpl implements AccountApi {
  interface Factory {
    AccountApiImpl create(AccountResource account);
  }

  private final AccountResource account;
  private final ChangesCollection changes;
  private final AccountLoader.Factory accountLoaderFactory;
  private final GetAvatar getAvatar;
  private final GetPreferences getPreferences;
  private final SetPreferences setPreferences;
  private final GetDiffPreferences getDiffPreferences;
  private final SetDiffPreferences setDiffPreferences;
  private final GetEditPreferences getEditPreferences;
  private final SetEditPreferences setEditPreferences;
  private final GetWatchedProjects getWatchedProjects;
  private final PostWatchedProjects postWatchedProjects;
  private final DeleteWatchedProjects deleteWatchedProjects;
  private final StarredChanges.Create starredChangesCreate;
  private final StarredChanges.Delete starredChangesDelete;
  private final Stars stars;
  private final Stars.Get starsGet;
  private final Stars.Post starsPost;
  private final CreateEmail.Factory createEmailFactory;
  private final GpgApiAdapter gpgApiAdapter;
  private final GetSshKeys getSshKeys;
  private final AddSshKey addSshKey;
  private final DeleteSshKey deleteSshKey;
  private final SshKeys sshKeys;
  private final GetAgreements getAgreements;
  private final PutAgreement putAgreement;
  private final GetActive getActive;
  private final PutActive putActive;
  private final DeleteActive deleteActive;

  @Inject
  AccountApiImpl(
      AccountLoader.Factory ailf,
      ChangesCollection changes,
      GetAvatar getAvatar,
      GetPreferences getPreferences,
      SetPreferences setPreferences,
      GetDiffPreferences getDiffPreferences,
      SetDiffPreferences setDiffPreferences,
      GetEditPreferences getEditPreferences,
      SetEditPreferences setEditPreferences,
      GetWatchedProjects getWatchedProjects,
      PostWatchedProjects postWatchedProjects,
      DeleteWatchedProjects deleteWatchedProjects,
      StarredChanges.Create starredChangesCreate,
      StarredChanges.Delete starredChangesDelete,
      Stars stars,
      Stars.Get starsGet,
      Stars.Post starsPost,
      CreateEmail.Factory createEmailFactory,
      GpgApiAdapter gpgApiAdapter,
      GetSshKeys getSshKeys,
      AddSshKey addSshKey,
      DeleteSshKey deleteSshKey,
      SshKeys sshKeys,
      GetAgreements getAgreements,
      PutAgreement putAgreement,
      GetActive getActive,
      PutActive putActive,
      DeleteActive deleteActive,
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
    this.getWatchedProjects = getWatchedProjects;
    this.postWatchedProjects = postWatchedProjects;
    this.deleteWatchedProjects = deleteWatchedProjects;
    this.starredChangesCreate = starredChangesCreate;
    this.starredChangesDelete = starredChangesDelete;
    this.stars = stars;
    this.starsGet = starsGet;
    this.starsPost = starsPost;
    this.createEmailFactory = createEmailFactory;
    this.getSshKeys = getSshKeys;
    this.addSshKey = addSshKey;
    this.deleteSshKey = deleteSshKey;
    this.sshKeys = sshKeys;
    this.gpgApiAdapter = gpgApiAdapter;
    this.getAgreements = getAgreements;
    this.putAgreement = putAgreement;
    this.getActive = getActive;
    this.putActive = putActive;
    this.deleteActive = deleteActive;
  }

  @Override
  public com.google.gerrit.extensions.common.AccountInfo get() throws RestApiException {
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
  public boolean getActive() throws RestApiException {
    Response<String> result = getActive.apply(account);
    return result.statusCode() == SC_OK && result.value().equals("ok");
  }

  @Override
  public void setActive(boolean active) throws RestApiException {
    try {
      if (active) {
        putActive.apply(account, new PutActive.Input());
      } else {
        deleteActive.apply(account, new DeleteActive.Input());
      }
    } catch (OrmException | IOException e) {
      throw new RestApiException("Cannot set active", e);
    }
  }

  @Override
  public String getAvatarUrl(int size) throws RestApiException {
    getAvatar.setSize(size);
    return getAvatar.apply(account).location();
  }

  @Override
  public GeneralPreferencesInfo getPreferences() throws RestApiException {
    return getPreferences.apply(account);
  }

  @Override
  public GeneralPreferencesInfo setPreferences(GeneralPreferencesInfo in) throws RestApiException {
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
  public DiffPreferencesInfo setDiffPreferences(DiffPreferencesInfo in) throws RestApiException {
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
  public EditPreferencesInfo setEditPreferences(EditPreferencesInfo in) throws RestApiException {
    try {
      return setEditPreferences.apply(account, in);
    } catch (IOException | ConfigInvalidException e) {
      throw new RestApiException("Cannot set edit preferences", e);
    }
  }

  @Override
  public List<ProjectWatchInfo> getWatchedProjects() throws RestApiException {
    try {
      return getWatchedProjects.apply(account);
    } catch (OrmException | IOException | ConfigInvalidException e) {
      throw new RestApiException("Cannot get watched projects", e);
    }
  }

  @Override
  public List<ProjectWatchInfo> setWatchedProjects(List<ProjectWatchInfo> in)
      throws RestApiException {
    try {
      return postWatchedProjects.apply(account, in);
    } catch (OrmException | IOException | ConfigInvalidException e) {
      throw new RestApiException("Cannot update watched projects", e);
    }
  }

  @Override
  public void deleteWatchedProjects(List<ProjectWatchInfo> in) throws RestApiException {
    try {
      deleteWatchedProjects.apply(account, in);
    } catch (OrmException | IOException | ConfigInvalidException e) {
      throw new RestApiException("Cannot delete watched projects", e);
    }
  }

  @Override
  public void starChange(String changeId) throws RestApiException {
    try {
      ChangeResource rsrc = changes.parse(TopLevelResource.INSTANCE, IdString.fromUrl(changeId));
      starredChangesCreate.setChange(rsrc);
      starredChangesCreate.apply(account, new StarredChanges.EmptyInput());
    } catch (OrmException | IOException e) {
      throw new RestApiException("Cannot star change", e);
    }
  }

  @Override
  public void unstarChange(String changeId) throws RestApiException {
    try {
      ChangeResource rsrc = changes.parse(TopLevelResource.INSTANCE, IdString.fromUrl(changeId));
      AccountResource.StarredChange starredChange =
          new AccountResource.StarredChange(account.getUser(), rsrc);
      starredChangesDelete.apply(starredChange, new StarredChanges.EmptyInput());
    } catch (OrmException | IOException e) {
      throw new RestApiException("Cannot unstar change", e);
    }
  }

  @Override
  public void setStars(String changeId, StarsInput input) throws RestApiException {
    try {
      AccountResource.Star rsrc = stars.parse(account, IdString.fromUrl(changeId));
      starsPost.apply(rsrc, input);
    } catch (OrmException e) {
      throw new RestApiException("Cannot post stars", e);
    }
  }

  @Override
  public SortedSet<String> getStars(String changeId) throws RestApiException {
    try {
      AccountResource.Star rsrc = stars.parse(account, IdString.fromUrl(changeId));
      return starsGet.apply(rsrc);
    } catch (OrmException e) {
      throw new RestApiException("Cannot get stars", e);
    }
  }

  @Override
  public List<ChangeInfo> getStarredChanges() throws RestApiException {
    try {
      return stars.list().apply(account);
    } catch (OrmException e) {
      throw new RestApiException("Cannot get starred changes", e);
    }
  }

  @Override
  public void addEmail(EmailInput input) throws RestApiException {
    AccountResource.Email rsrc = new AccountResource.Email(account.getUser(), input.email);
    try {
      createEmailFactory.create(input.email).apply(rsrc, input);
    } catch (EmailException | OrmException | IOException e) {
      throw new RestApiException("Cannot add email", e);
    }
  }

  @Override
  public List<SshKeyInfo> listSshKeys() throws RestApiException {
    try {
      return getSshKeys.apply(account);
    } catch (OrmException | IOException | ConfigInvalidException e) {
      throw new RestApiException("Cannot list SSH keys", e);
    }
  }

  @Override
  public SshKeyInfo addSshKey(String key) throws RestApiException {
    AddSshKey.Input in = new AddSshKey.Input();
    in.raw = RawInputUtil.create(key);
    try {
      return addSshKey.apply(account, in).value();
    } catch (OrmException | IOException | ConfigInvalidException e) {
      throw new RestApiException("Cannot add SSH key", e);
    }
  }

  @Override
  public void deleteSshKey(int seq) throws RestApiException {
    try {
      AccountResource.SshKey sshKeyRes =
          sshKeys.parse(account, IdString.fromDecoded(Integer.toString(seq)));
      deleteSshKey.apply(sshKeyRes, null);
    } catch (OrmException | IOException | ConfigInvalidException e) {
      throw new RestApiException("Cannot delete SSH key", e);
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
  public Map<String, GpgKeyInfo> putGpgKeys(List<String> add, List<String> delete)
      throws RestApiException {
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

  @Override
  public List<AgreementInfo> listAgreements() throws RestApiException {
    return getAgreements.apply(account);
  }

  @Override
  public void signAgreement(String agreementName) throws RestApiException {
    try {
      AgreementInput input = new AgreementInput();
      input.name = agreementName;
      putAgreement.apply(account, input);
    } catch (IOException | OrmException e) {
      throw new RestApiException("Cannot sign agreement", e);
    }
  }
}
