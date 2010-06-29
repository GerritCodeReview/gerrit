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
import com.google.gerrit.reviewdb.Patch;
import com.google.gerrit.server.project.ProjectState;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Notify interested parties of a brand new change. */
public class CreateChangeSender extends NewChangeSender {
  public static interface Factory {
    public CreateChangeSender create(Change change);
  }

  @Inject
  public CreateChangeSender(@Assisted Change c) {
    super(c);
  }

  @Override
  protected void init() {
    super.init();

    bccWatchers();
  }

  private void bccWatchers() {
    if (db != null) {
      try {
        // BCC anyone else who has interest in this project's changes
        //
        final ProjectState ps = getProjectState();
        if (ps != null) {
          // Try to mark interested owners with a TO and not a BCC line.
          //
          final Set<Account.Id> owners = new HashSet<Account.Id>();
          for (AccountGroup.Id g : getProjectOwners()) {
            for (AccountGroupMember m : db.accountGroupMembers().byGroup(g)) {
              owners.add(m.getAccountId());
            }
          }

          final List<Patch> patches = getPatches(patchSet.getId());
          final List<String> patchFileNames = getPatchesFileNames(patches);

          // BCC anyone who has interest in this project's changes
          //
          for (AccountProjectWatch w : db.accountProjectWatches()
              .notifyNewChanges(ps.getProject().getNameKey())) {
            if (canAddRecipient(w, patchFileNames)) {
              if (owners.contains(w.getAccountId())) {
                add(RecipientType.TO, w.getAccountId());
              } else {
                add(RecipientType.BCC, w.getAccountId());
              }
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
}
