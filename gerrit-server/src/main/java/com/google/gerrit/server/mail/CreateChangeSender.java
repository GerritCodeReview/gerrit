// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.mail;

import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.AccountGroupMember;
import com.google.gerrit.reviewdb.AccountProjectWatch;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.AccountProjectWatch.NotifyType;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.ssh.SshInfo;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.HashSet;
import java.util.Set;

/** Notify interested parties of a brand new change. */
public class CreateChangeSender extends NewChangeSender {
  public static interface Factory {
    public CreateChangeSender create(Change change);
  }

  private final GroupCache groupCache;

  @Inject
  public CreateChangeSender(EmailArguments ea, SshInfo sshInfo,
      GroupCache groupCache, @Assisted Change c) {
    super(ea, sshInfo, c);
    this.groupCache = groupCache;
  }

  @Override
  protected void init() throws EmailException {
    super.init();

    bccWatchers();
  }

  private void bccWatchers() {
    try {
      // Try to mark interested owners with a TO and not a BCC line.
      //
      final Set<Account.Id> owners = new HashSet<Account.Id>();
      for (AccountGroup.UUID uuid : getProjectOwners()) {
        AccountGroup group = groupCache.get(uuid);
        if (group != null) {
          for (AccountGroupMember m : args.db.get().accountGroupMembers()
              .byGroup(group.getId())) {
            owners.add(m.getAccountId());
          }
        }
      }

      // BCC anyone who has interest in this project's changes
      //
      for (final AccountProjectWatch w : getWatches()) {
        if (w.isNotify(NotifyType.NEW_CHANGES)) {
          if (owners.contains(w.getAccountId())) {
            add(RecipientType.TO, w.getAccountId());
          } else {
            add(RecipientType.BCC, w.getAccountId());
          }
        }
      }
    } catch (OrmException err) {
      // Just don't CC everyone. Better to send a partial message to those
      // we already have queued up then to fail deliver entirely to people
      // who have a lower interest in the change.
    }
  }
}
