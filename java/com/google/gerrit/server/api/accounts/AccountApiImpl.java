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

import static com.google.gerrit.server.api.ApiUtil.asRestApiException;
import static javax.servlet.http.HttpServletResponse.SC_OK;

import com.google.gerrit.common.RawInputUtil;
import com.google.gerrit.extensions.api.accounts.AccountApi;
import com.google.gerrit.extensions.api.accounts.EmailApi;
import com.google.gerrit.extensions.api.accounts.EmailInput;
import com.google.gerrit.extensions.api.accounts.GpgKeyApi;
import com.google.gerrit.extensions.api.accounts.SshKeyInput;
import com.google.gerrit.extensions.api.accounts.StatusInput;
import com.google.gerrit.extensions.api.changes.StarsInput;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.EditPreferencesInfo;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.extensions.client.ProjectWatchInfo;
import com.google.gerrit.extensions.common.AccountExternalIdInfo;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.AgreementInfo;
import com.google.gerrit.extensions.common.AgreementInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.EmailInfo;
import com.google.gerrit.extensions.common.GpgKeyInfo;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.common.Input;
import com.google.gerrit.extensions.common.SshKeyInfo;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.GpgApiAdapter;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.restapi.account.AddSshKey;
import com.google.gerrit.server.restapi.account.CreateEmail;
import com.google.gerrit.server.restapi.account.DeleteActive;
import com.google.gerrit.server.restapi.account.DeleteEmail;
import com.google.gerrit.server.restapi.account.DeleteExternalIds;
import com.google.gerrit.server.restapi.account.DeleteSshKey;
import com.google.gerrit.server.restapi.account.DeleteWatchedProjects;
import com.google.gerrit.server.restapi.account.GetActive;
import com.google.gerrit.server.restapi.account.GetAgreements;
import com.google.gerrit.server.restapi.account.GetAvatar;
import com.google.gerrit.server.restapi.account.GetDiffPreferences;
import com.google.gerrit.server.restapi.account.GetEditPreferences;
import com.google.gerrit.server.restapi.account.GetEmails;
import com.google.gerrit.server.restapi.account.GetExternalIds;
import com.google.gerrit.server.restapi.account.GetGroups;
import com.google.gerrit.server.restapi.account.GetPreferences;
import com.google.gerrit.server.restapi.account.GetSshKeys;
import com.google.gerrit.server.restapi.account.GetWatchedProjects;
import com.google.gerrit.server.restapi.account.Index;
import com.google.gerrit.server.restapi.account.PostWatchedProjects;
import com.google.gerrit.server.restapi.account.PutActive;
import com.google.gerrit.server.restapi.account.PutAgreement;
import com.google.gerrit.server.restapi.account.PutStatus;
import com.google.gerrit.server.restapi.account.SetDiffPreferences;
import com.google.gerrit.server.restapi.account.SetEditPreferences;
import com.google.gerrit.server.restapi.account.SetPreferences;
import com.google.gerrit.server.restapi.account.SshKeys;
import com.google.gerrit.server.restapi.account.StarredChanges;
import com.google.gerrit.server.restapi.account.Stars;
import com.google.gerrit.server.restapi.change.ChangesCollection;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

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
  private final GetEmails getEmails;
  private final CreateEmail.Factory createEmailFactory;
  private final DeleteEmail deleteEmail;
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
  private final Index index;
  private final GetExternalIds getExternalIds;
  private final DeleteExternalIds deleteExternalIds;
  private final PutStatus putStatus;
  private final GetGroups getGroups;
  private final EmailApiImpl.Factory emailApi;

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
      GetEmails getEmails,
      CreateEmail.Factory createEmailFactory,
      DeleteEmail deleteEmail,
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
      Index index,
      GetExternalIds getExternalIds,
      DeleteExternalIds deleteExternalIds,
      PutStatus putStatus,
      GetGroups getGroups,
      EmailApiImpl.Factory emailApi,
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
    this.getEmails = getEmails;
    this.createEmailFactory = createEmailFactory;
    this.deleteEmail = deleteEmail;
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
    this.index = index;
    this.getExternalIds = getExternalIds;
    this.deleteExternalIds = deleteExternalIds;
    this.putStatus = putStatus;
    this.getGroups = getGroups;
    this.emailApi = emailApi;
  }

  @Override
  public com.google.gerrit.extensions.common.AccountInfo get() throws RestApiException {
    AccountLoader accountLoader = accountLoaderFactory.create(true);
    try {
      AccountInfo ai = accountLoader.get(account.getUser().getAccountId());
      accountLoader.fill();
      return ai;
    } catch (Exception e) {
      throw asRestApiException("Cannot parse change", e);
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
        putActive.apply(account, new Input());
      } else {
        deleteActive.apply(account, new Input());
      }
    } catch (Exception e) {
      throw asRestApiException("Cannot set active", e);
    }
  }

  @Override
  public String getAvatarUrl(int size) throws RestApiException {
    getAvatar.setSize(size);
    return getAvatar.apply(account).location();
  }

  @Override
  public GeneralPreferencesInfo getPreferences() throws RestApiException {
    try {
      return getPreferences.apply(account);
    } catch (Exception e) {
      throw asRestApiException("Cannot get preferences", e);
    }
  }

  @Override
  public GeneralPreferencesInfo setPreferences(GeneralPreferencesInfo in) throws RestApiException {
    try {
      return setPreferences.apply(account, in);
    } catch (Exception e) {
      throw asRestApiException("Cannot set preferences", e);
    }
  }

  @Override
  public DiffPreferencesInfo getDiffPreferences() throws RestApiException {
    try {
      return getDiffPreferences.apply(account);
    } catch (Exception e) {
      throw asRestApiException("Cannot query diff preferences", e);
    }
  }

  @Override
  public DiffPreferencesInfo setDiffPreferences(DiffPreferencesInfo in) throws RestApiException {
    try {
      return setDiffPreferences.apply(account, in);
    } catch (Exception e) {
      throw asRestApiException("Cannot set diff preferences", e);
    }
  }

  @Override
  public EditPreferencesInfo getEditPreferences() throws RestApiException {
    try {
      return getEditPreferences.apply(account);
    } catch (Exception e) {
      throw asRestApiException("Cannot query edit preferences", e);
    }
  }

  @Override
  public EditPreferencesInfo setEditPreferences(EditPreferencesInfo in) throws RestApiException {
    try {
      return setEditPreferences.apply(account, in);
    } catch (Exception e) {
      throw asRestApiException("Cannot set edit preferences", e);
    }
  }

  @Override
  public List<ProjectWatchInfo> getWatchedProjects() throws RestApiException {
    try {
      return getWatchedProjects.apply(account);
    } catch (Exception e) {
      throw asRestApiException("Cannot get watched projects", e);
    }
  }

  @Override
  public List<ProjectWatchInfo> setWatchedProjects(List<ProjectWatchInfo> in)
      throws RestApiException {
    try {
      return postWatchedProjects.apply(account, in);
    } catch (Exception e) {
      throw asRestApiException("Cannot update watched projects", e);
    }
  }

  @Override
  public void deleteWatchedProjects(List<ProjectWatchInfo> in) throws RestApiException {
    try {
      deleteWatchedProjects.apply(account, in);
    } catch (Exception e) {
      throw asRestApiException("Cannot delete watched projects", e);
    }
  }

  @Override
  public void starChange(String changeId) throws RestApiException {
    try {
      ChangeResource rsrc = changes.parse(TopLevelResource.INSTANCE, IdString.fromUrl(changeId));
      starredChangesCreate.setChange(rsrc);
      starredChangesCreate.apply(account, new StarredChanges.EmptyInput());
    } catch (Exception e) {
      throw asRestApiException("Cannot star change", e);
    }
  }

  @Override
  public void unstarChange(String changeId) throws RestApiException {
    try {
      ChangeResource rsrc = changes.parse(TopLevelResource.INSTANCE, IdString.fromUrl(changeId));
      AccountResource.StarredChange starredChange =
          new AccountResource.StarredChange(account.getUser(), rsrc);
      starredChangesDelete.apply(starredChange, new StarredChanges.EmptyInput());
    } catch (Exception e) {
      throw asRestApiException("Cannot unstar change", e);
    }
  }

  @Override
  public void setStars(String changeId, StarsInput input) throws RestApiException {
    try {
      AccountResource.Star rsrc = stars.parse(account, IdString.fromUrl(changeId));
      starsPost.apply(rsrc, input);
    } catch (Exception e) {
      throw asRestApiException("Cannot post stars", e);
    }
  }

  @Override
  public SortedSet<String> getStars(String changeId) throws RestApiException {
    try {
      AccountResource.Star rsrc = stars.parse(account, IdString.fromUrl(changeId));
      return starsGet.apply(rsrc);
    } catch (Exception e) {
      throw asRestApiException("Cannot get stars", e);
    }
  }

  @Override
  public List<ChangeInfo> getStarredChanges() throws RestApiException {
    try {
      return stars.list().apply(account);
    } catch (Exception e) {
      throw asRestApiException("Cannot get starred changes", e);
    }
  }

  @Override
  public List<GroupInfo> getGroups() throws RestApiException {
    try {
      return getGroups.apply(account);
    } catch (OrmException e) {
      throw asRestApiException("Cannot get groups", e);
    }
  }

  @Override
  public List<EmailInfo> getEmails() throws RestApiException {
    try {
      return getEmails.apply(account);
    } catch (Exception e) {
      throw asRestApiException("Cannot get emails", e);
    }
  }

  @Override
  public void addEmail(EmailInput input) throws RestApiException {
    AccountResource.Email rsrc = new AccountResource.Email(account.getUser(), input.email);
    try {
      createEmailFactory.create(input.email).apply(rsrc, input);
    } catch (Exception e) {
      throw asRestApiException("Cannot add email", e);
    }
  }

  @Override
  public void deleteEmail(String email) throws RestApiException {
    AccountResource.Email rsrc = new AccountResource.Email(account.getUser(), email);
    try {
      deleteEmail.apply(rsrc, null);
    } catch (Exception e) {
      throw asRestApiException("Cannot delete email", e);
    }
  }

  @Override
  public EmailApi createEmail(EmailInput input) throws RestApiException {
    AccountResource.Email rsrc = new AccountResource.Email(account.getUser(), input.email);
    try {
      createEmailFactory.create(input.email).apply(rsrc, input);
      return email(rsrc.getEmail());
    } catch (Exception e) {
      throw asRestApiException("Cannot create email", e);
    }
  }

  @Override
  public EmailApi email(String email) throws RestApiException {
    try {
      return emailApi.create(account, email);
    } catch (Exception e) {
      throw asRestApiException("Cannot parse email", e);
    }
  }

  @Override
  public void setStatus(String status) throws RestApiException {
    StatusInput in = new StatusInput(status);
    try {
      putStatus.apply(account, in);
    } catch (Exception e) {
      throw asRestApiException("Cannot set status", e);
    }
  }

  @Override
  public List<SshKeyInfo> listSshKeys() throws RestApiException {
    try {
      return getSshKeys.apply(account);
    } catch (Exception e) {
      throw asRestApiException("Cannot list SSH keys", e);
    }
  }

  @Override
  public SshKeyInfo addSshKey(String key) throws RestApiException {
    SshKeyInput in = new SshKeyInput();
    in.raw = RawInputUtil.create(key);
    try {
      return addSshKey.apply(account, in).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot add SSH key", e);
    }
  }

  @Override
  public void deleteSshKey(int seq) throws RestApiException {
    try {
      AccountResource.SshKey sshKeyRes =
          sshKeys.parse(account, IdString.fromDecoded(Integer.toString(seq)));
      deleteSshKey.apply(sshKeyRes, null);
    } catch (Exception e) {
      throw asRestApiException("Cannot delete SSH key", e);
    }
  }

  @Override
  public Map<String, GpgKeyInfo> listGpgKeys() throws RestApiException {
    try {
      return gpgApiAdapter.listGpgKeys(account);
    } catch (Exception e) {
      throw asRestApiException("Cannot list GPG keys", e);
    }
  }

  @Override
  public Map<String, GpgKeyInfo> putGpgKeys(List<String> add, List<String> delete)
      throws RestApiException {
    try {
      return gpgApiAdapter.putGpgKeys(account, add, delete);
    } catch (Exception e) {
      throw asRestApiException("Cannot add GPG key", e);
    }
  }

  @Override
  public GpgKeyApi gpgKey(String id) throws RestApiException {
    try {
      return gpgApiAdapter.gpgKey(account, IdString.fromDecoded(id));
    } catch (Exception e) {
      throw asRestApiException("Cannot get PGP key", e);
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
    } catch (Exception e) {
      throw asRestApiException("Cannot sign agreement", e);
    }
  }

  @Override
  public void index() throws RestApiException {
    try {
      index.apply(account, new Input());
    } catch (Exception e) {
      throw asRestApiException("Cannot index account", e);
    }
  }

  @Override
  public List<AccountExternalIdInfo> getExternalIds() throws RestApiException {
    try {
      return getExternalIds.apply(account);
    } catch (Exception e) {
      throw asRestApiException("Cannot get external IDs", e);
    }
  }

  @Override
  public void deleteExternalIds(List<String> externalIds) throws RestApiException {
    try {
      deleteExternalIds.apply(account, externalIds);
    } catch (Exception e) {
      throw asRestApiException("Cannot delete external IDs", e);
    }
  }
}
