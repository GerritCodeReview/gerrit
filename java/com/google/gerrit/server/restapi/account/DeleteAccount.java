// Copyright (C) 2023 The Android Open Source Project
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

import static com.google.common.collect.Iterables.getOnlyElement;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.exceptions.EmailException;
import com.google.gerrit.extensions.api.accounts.AccountInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.StarredChangesUtil;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.AccountSshKey;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.account.VersionedAuthorizedKeys;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.change.AccountPatchReviewStore;
import com.google.gerrit.server.edit.ChangeEdit;
import com.google.gerrit.server.edit.ChangeEditUtil;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.mail.send.DeleteKeySender;
import com.google.gerrit.server.plugincontext.PluginItemContext;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.ssh.SshKeyCache;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * REST endpoint for deleting an account.
 *
 * <p>This REST endpoint handles {@code DELETE /accounts/<account-identifier>} requests. Currently,
 * only self deletions are allowed.
 */
@Singleton
public class DeleteAccount implements RestModifyView<AccountResource, AccountInput> {
  private final Provider<CurrentUser> self;
  private final ExternalIds externalIds;
  private final Provider<AccountsUpdate> accountsUpdateProvider;
  private final VersionedAuthorizedKeys.Accessor authorizedKeys;
  private final SshKeyCache sshKeyCache;
  private final DeleteKeySender.Factory deleteKeySenderFactory;
  private final StarredChangesUtil starredChangesUtil;
  private final DeleteDraftCommentsUtil deleteDraftCommentsUtil;
  private final GitRepositoryManager gitManager;
  private final Provider<InternalChangeQuery> queryProvider;
  private final ChangeEditUtil changeEditUtil;
  private final PluginItemContext<AccountPatchReviewStore> accountPatchReviewStore;

  @Inject
  public DeleteAccount(
      Provider<CurrentUser> self,
      ExternalIds externalIds,
      Provider<AccountsUpdate> accountsUpdateProvider,
      VersionedAuthorizedKeys.Accessor authorizedKeys,
      SshKeyCache sshKeyCache,
      DeleteKeySender.Factory deleteKeySenderFactory,
      StarredChangesUtil starredChangesUtil,
      DeleteDraftCommentsUtil deleteDraftCommentsUtil,
      GitRepositoryManager gitManager,
      Provider<InternalChangeQuery> queryProvider,
      ChangeEditUtil changeEditUtil,
      PluginItemContext<AccountPatchReviewStore> accountPatchReviewStore) {
    this.self = self;
    this.externalIds = externalIds;
    this.accountsUpdateProvider = accountsUpdateProvider;
    this.authorizedKeys = authorizedKeys;
    this.sshKeyCache = sshKeyCache;
    this.deleteKeySenderFactory = deleteKeySenderFactory;
    this.starredChangesUtil = starredChangesUtil;
    this.deleteDraftCommentsUtil = deleteDraftCommentsUtil;
    this.gitManager = gitManager;
    this.queryProvider = queryProvider;
    this.changeEditUtil = changeEditUtil;
    this.accountPatchReviewStore = accountPatchReviewStore;
  }

  @Override
  public Response<?> apply(AccountResource rsrc, AccountInput input)
      throws AuthException, AccountException {
    IdentifiedUser user = rsrc.getUser();
    if (!self.get().hasSameAccountId(rsrc.getUser())) {
      throw new AuthException("Delete account is only permitted for self");
    }

    Account.Id userId = user.getAccountId();
    try {
      deleteSShKeys(user);
      deleteStarChanges(userId);
      deleteChangeEdits(userId);
      deleteDraftCommentsUtil.deleteDraftComments(user, "");
      accountPatchReviewStore.run(a -> a.clearReviewedBy(userId));
      deleteAccountIdentifiersAndPreferences(user);
    } catch (Exception e) {
      throw new AccountException("Could not delete account", e);
    }
    return Response.none();
  }

  private void deleteAccountIdentifiersAndPreferences(IdentifiedUser user)
      throws IOException, ConfigInvalidException {
    ImmutableSet<ExternalId> accountExternalIds = externalIds.byAccount(user.getAccountId());
    accountsUpdateProvider
        .get()
        .update(
            "Deleting user",
            user.getAccountId(),
            u ->
                u.setActive(false)
                    .setFullName(null)
                    .setDisplayName(null)
                    .setDiffPreferences(null)
                    .setPreferredEmail(null)
                    .setStatus(null)
                    .setGeneralPreferences(null)
                    .setDiffPreferences(null)
                    .setEditPreferences(null)
                    .deleteExternalIds(accountExternalIds)
                    .deleteProjectWatches(user.state().projectWatches().keySet()));
  }

  private void deleteSShKeys(IdentifiedUser user)
      throws ConfigInvalidException, IOException, EmailException {
    List<AccountSshKey> keys = authorizedKeys.getKeys(user.getAccountId());
    for (AccountSshKey key : keys) {
      deleteKeySenderFactory.create(user, key).send();
    }
    user.getUserName().ifPresent(sshKeyCache::evict);
  }

  private void deleteStarChanges(Account.Id accountId)
      throws StarredChangesUtil.IllegalLabelException {
    ImmutableSet<Change.Id> staredChanges =
        starredChangesUtil.byAccountIdIncludingInvalid(accountId);
    for (Change.Id change : staredChanges) {
      starredChangesUtil.star(
          self.get().getAccountId(), change, StarredChangesUtil.Operation.REMOVE);
    }
  }

  private void deleteChangeEdits(Account.Id accountId) throws IOException {
    for (Project.NameKey project : gitManager.list()) {
      for (ChangeEdit edit : getAccountChangeEditsInProject(accountId, project)) {
        changeEditUtil.delete(edit);
      }
    }
  }

  private List<ChangeEdit> getAccountChangeEditsInProject(
      Account.Id accountId, Project.NameKey project) throws IOException {
    List<ChangeEdit> res = new ArrayList<>();
    try (Repository repo = gitManager.openRepository(project)) {
      List<Ref> refs = repo.getRefDatabase().getRefsByPrefix(RefNames.refsEditPrefix(accountId));
      try (RevWalk rw = new RevWalk(repo)) {
        for (Ref ref : refs) {
          RevCommit commit = rw.parseCommit(ref.getObjectId());
          ChangeData changeData =
              getOnlyElement(queryProvider.get().byProjectCommit(project, commit));
          // The basePatchSet is not needed for deleting the edit.
          res.add(new ChangeEdit(changeData.change(), ref.getName(), commit, null));
        }
      }
    }
    return res;
  }
}
