// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.gpg;

import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.util.MagicBranch;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.Collection;
import org.eclipse.jgit.transport.PreReceiveHook;
import org.eclipse.jgit.transport.PushCertificate;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;

/**
 * Pre-receive hook to check signed pushes.
 *
 * <p>If configured, prior to processing any push using {@link
 * com.google.gerrit.server.git.ReceiveCommits}, requires that any push certificate present must be
 * valid.
 */
@Singleton
public class SignedPushPreReceiveHook implements PreReceiveHook {
  public static class Required implements PreReceiveHook {
    public static final Required INSTANCE = new Required();

    @Override
    public void onPreReceive(ReceivePack rp, Collection<ReceiveCommand> commands) {
      if (rp.getPushCertificate() == null) {
        rp.sendMessage("ERROR: Signed push is required");
        reject(commands, "push cert error");
      }
    }

    private Required() {}
  }

  private final Provider<IdentifiedUser> user;
  private final GerritPushCertificateChecker.Factory checkerFactory;

  @Inject
  public SignedPushPreReceiveHook(
      Provider<IdentifiedUser> user, GerritPushCertificateChecker.Factory checkerFactory) {
    this.user = user;
    this.checkerFactory = checkerFactory;
  }

  @Override
  public void onPreReceive(ReceivePack rp, Collection<ReceiveCommand> commands) {
    PushCertificate cert = rp.getPushCertificate();
    if (cert == null) {
      return;
    }
    CheckResult result =
        checkerFactory.create(user.get()).setCheckNonce(true).check(cert).getCheckResult();
    if (!isAllowed(result, commands)) {
      for (String problem : result.getProblems()) {
        rp.sendMessage(problem);
      }
      reject(commands, "invalid push cert");
    }
  }

  private static boolean isAllowed(CheckResult result, Collection<ReceiveCommand> commands) {
    if (onlyMagicBranches(commands)) {
      // Only pushing magic branches: allow a valid push certificate even if the
      // key is not ultimately trusted. Assume anyone with Submit permission to
      // the branch is able to verify during review that the code is legitimate.
      return result.isOk();
    }
    // Directly updating one or more refs: require a trusted key.
    return result.isTrusted();
  }

  private static boolean onlyMagicBranches(Iterable<ReceiveCommand> commands) {
    for (ReceiveCommand c : commands) {
      if (!MagicBranch.isMagicBranch(c.getRefName())) {
        return false;
      }
    }
    return true;
  }

  private static void reject(Collection<ReceiveCommand> commands, String reason) {
    for (ReceiveCommand cmd : commands) {
      if (cmd.getResult() == ReceiveCommand.Result.NOT_ATTEMPTED) {
        cmd.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, reason);
      }
    }
  }
}
