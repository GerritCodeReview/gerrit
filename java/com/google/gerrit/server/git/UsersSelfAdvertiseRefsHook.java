// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.server.git;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.server.CurrentUser;
import java.io.IOException;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.SymbolicRef;
import org.eclipse.jgit.transport.AdvertiseRefsHook;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.ServiceMayNotContinueException;
import org.eclipse.jgit.transport.UploadPack;

/**
 * Advertises {@code refs/users/self} for authenticated users when interacting with the {@code
 * All-Users} repository.
 */
@Singleton
public class UsersSelfAdvertiseRefsHook implements AdvertiseRefsHook {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Provider<CurrentUser> userProvider;

  @Inject
  public UsersSelfAdvertiseRefsHook(Provider<CurrentUser> userProvider) {
    this.userProvider = userProvider;
  }

  @Override
  public void advertiseRefs(UploadPack uploadPack) throws ServiceMayNotContinueException {
    CurrentUser user = userProvider.get();
    if (!user.isIdentifiedUser()) {
      return;
    }

    addSelfSymlinkIfNecessary(
        uploadPack.getRepository().getRefDatabase(),
        HookUtil.ensureAllRefsAdvertised(uploadPack),
        user.getAccountId());
  }

  @Override
  public void advertiseRefs(ReceivePack receivePack) throws ServiceMayNotContinueException {
    CurrentUser user = userProvider.get();
    if (!user.isIdentifiedUser()) {
      return;
    }

    addSelfSymlinkIfNecessary(
        receivePack.getRepository().getRefDatabase(),
        HookUtil.ensureAllRefsAdvertised(receivePack),
        user.getAccountId());
  }

  private static void addSelfSymlinkIfNecessary(
      RefDatabase refDatabase, Map<String, Ref> advertisedRefs, Account.Id accountId)
      throws ServiceMayNotContinueException {
    String refName = RefNames.refsUsers(accountId);
    try {
      Ref r = refDatabase.exactRef(refName);
      if (r == null) {
        logger.atWarning().log("User ref %s not found", refName);
        return;
      }

      SymbolicRef s = new SymbolicRef(RefNames.REFS_USERS_SELF, r);
      advertisedRefs.put(s.getName(), s);
      logger.atFinest().log("Added %s as alias for user ref %s", RefNames.REFS_USERS_SELF, refName);
    } catch (IOException e) {
      throw new ServiceMayNotContinueException(e);
    }
  }
}
