// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.sshd.commands;

import com.google.gerrit.common.CollectionsUtil;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.VisibleRefFilter;
import com.google.gerrit.sshd.AbstractGitCommand;
import com.google.gerrit.sshd.TransferConfig;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.transport.UploadPack;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Set;

/** Publishes Git repositories over SSH using the Git upload-pack protocol. */
final class Upload extends AbstractGitCommand {
  @Inject
  private Provider<ReviewDb> db;

  @Inject
  private TransferConfig config;

  @Inject
  private IdentifiedUser currentUser;

  @Override
  protected void runImpl() throws IOException, Failure {
    final Set<AccountGroup.Id> uploadGroup =
        getServerCommandConfig().getUploadGroup();

    if (uploadGroup != null && !uploadGroup.isEmpty()) {
      if (!CollectionsUtil.isAnyIncludedIn(currentUser.getEffectiveGroups(),
          uploadGroup)) {
        throw new Failure(1, "User: " + currentUser.getUserName()
            + " not allowed to execute this command on this server");
      }
    }

    final UploadPack up = new UploadPack(repo);
    if (!projectControl.allRefsAreVisible()) {
      up.setRefFilter(new VisibleRefFilter(repo, projectControl, db.get()));
    }
    up.setTimeout(config.getTimeout());
    try {
      up.upload(in, out, err);
    } catch (InterruptedIOException err) {
      throw new Failure(128, "fatal: client IO read/write timeout", err);
    }
  }
}
