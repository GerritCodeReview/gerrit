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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Table;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.common.Input;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.gpg.PublicKeyStoreUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.StarredChangesUtil;
import com.google.gerrit.server.UserInitiated;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.AccountSshKey;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.account.VersionedAuthorizedKeys;
import com.google.gerrit.server.change.AccountPatchReviewStore;
import com.google.gerrit.server.edit.ChangeEdit;
import com.google.gerrit.server.edit.ChangeEditUtil;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.plugincontext.PluginItemContext;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.ssh.SshKeyCache;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
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
public class DeleteAccount implements RestModifyView<AccountResource, Input> {
  private final Provider<CurrentUser> self;
  private final Provider<PersonIdent> serverIdent;
  private final Provider<AccountsUpdate> accountsUpdateProvider;
  private final VersionedAuthorizedKeys.Accessor authorizedKeys;
  private final SshKeyCache sshKeyCache;
  private final StarredChangesUtil starredChangesUtil;
  private final DeleteDraftCommentsUtil deleteDraftCommentsUtil;
  private final GitRepositoryManager gitManager;
  private final Provider<InternalChangeQuery> queryProvider;
  private final ChangeEditUtil changeEditUtil;
  private final PluginItemContext<AccountPatchReviewStore> accountPatchReviewStore;
  private final PublicKeyStoreUtil publicKeyStoreUtil;

  @Inject
  public DeleteAccount(
      Provider<CurrentUser> self,
      @GerritPersonIdent Provider<PersonIdent> serverIdent,
      @UserInitiated Provider<AccountsUpdate> accountsUpdateProvider,
      VersionedAuthorizedKeys.Accessor authorizedKeys,
      SshKeyCache sshKeyCache,
      StarredChangesUtil starredChangesUtil,
      DeleteDraftCommentsUtil deleteDraftCommentsUtil,
      GitRepositoryManager gitManager,
      Provider<InternalChangeQuery> queryProvider,
      ChangeEditUtil changeEditUtil,
      PluginItemContext<AccountPatchReviewStore> accountPatchReviewStore,
      PublicKeyStoreUtil publicKeyStoreUtil) {
    this.self = self;
    this.serverIdent = serverIdent;
    this.accountsUpdateProvider = accountsUpdateProvider;
    this.authorizedKeys = authorizedKeys;
    this.sshKeyCache = sshKeyCache;
    this.starredChangesUtil = starredChangesUtil;
    this.deleteDraftCommentsUtil = deleteDraftCommentsUtil;
    this.gitManager = gitManager;
    this.queryProvider = queryProvider;
    this.changeEditUtil = changeEditUtil;
    this.accountPatchReviewStore = accountPatchReviewStore;
    this.publicKeyStoreUtil = publicKeyStoreUtil;
  }

  @Override
  @CanIgnoreReturnValue
  public Response<?> apply(AccountResource rsrc, Input unusedInput)
      throws AuthException, AccountException {
    IdentifiedUser user = rsrc.getUser();
    if (!self.get().hasSameAccountId(user)) {
      throw new AuthException("Delete account is only permitted for self");
    }

    Account.Id userId = user.getAccountId();
    try {
      deletePgpKeys(user);
      deleteSshKeys(user);
      deleteStarredChanges(userId);
      deleteChangeEdits(userId);
      deleteDraftCommentsUtil.deleteDraftComments(user, null);
      accountPatchReviewStore.run(a -> a.clearReviewedBy(userId));
      accountsUpdateProvider
          .get()
          .delete("Deleting user through `DELETE /accounts/{ID}`", user.getAccountId());
    } catch (Exception e) {
      throw new AccountException("Could not delete account", e);
    }
    return Response.none();
  }

  private void deletePgpKeys(IdentifiedUser user) {
    if (!publicKeyStoreUtil.hasInitializedPublicKeyStore()) {
      return;
    }
    try {
      List<RefUpdate.Result> deletedKeyResults =
          publicKeyStoreUtil.deleteAllPgpKeysForUser(
              user.getAccountId(), serverIdent.get(), serverIdent.get());
      for (RefUpdate.Result saveResult : deletedKeyResults) {
        if (saveResult != RefUpdate.Result.NO_CHANGE
            && saveResult != RefUpdate.Result.FAST_FORWARD) {
          throw new StorageException(String.format("Failed to delete PGP key: %s", saveResult));
        }
      }
    } catch (Exception e) {
      throw new StorageException("Failed to delete PGP keys.", e);
    }
  }

  private void deleteSshKeys(IdentifiedUser user) throws ConfigInvalidException, IOException {
    List<AccountSshKey> keys = authorizedKeys.getKeys(user.getAccountId());
    for (AccountSshKey key : keys) {
      authorizedKeys.deleteKey(user.getAccountId(), key.seq());
    }
    user.getUserName().ifPresent(sshKeyCache::evict);
  }

  private void deleteStarredChanges(Account.Id accountId) {
    ImmutableSet<Change.Id> staredChanges = starredChangesUtil.byAccountId(accountId, false);
    for (Change.Id change : staredChanges) {
      starredChangesUtil.unstar(self.get().getAccountId(), change);
    }
  }

  private void deleteChangeEdits(Account.Id accountId) throws IOException {
    // Note: in case of a stale index, the results of this query might be incomplete.
    List<ChangeData> changesWithEdits = queryProvider.get().byOpenEditByUser(accountId);

    for (ChangeData cd : changesWithEdits) {
      for (Table.Cell<Account.Id, PatchSet.Id, Ref> edit : cd.editRefs().cellSet()) {
        if (!accountId.equals(edit.getRowKey())) {
          continue;
        }
        try (Repository repo = gitManager.openRepository(cd.project());
            RevWalk rw = new RevWalk(repo)) {
          RevCommit commit = rw.parseCommit(edit.getValue().getObjectId());
          changeEditUtil.delete(
              new ChangeEdit(
                  cd.change(),
                  edit.getValue().getName(),
                  commit,
                  cd.patchSet(edit.getColumnKey())));
        }
      }
    }
  }
}
