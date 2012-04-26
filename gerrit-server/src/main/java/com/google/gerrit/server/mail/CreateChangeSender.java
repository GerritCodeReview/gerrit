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

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountProjectWatch.NotifyType;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.ssh.SshInfo;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Notify interested parties of a brand new change. */
public class CreateChangeSender extends NewChangeSender {
  private static final Logger log =
      LoggerFactory.getLogger(CreateChangeSender.class);

  public static interface Factory {
    public CreateChangeSender create(Change change);
  }

  @Inject
  public CreateChangeSender(EmailArguments ea,
      @AnonymousCowardName String anonymousCowardName, SshInfo sshInfo,
      @Assisted Change c) {
    super(ea, anonymousCowardName, sshInfo, c);
  }

  @Override
  protected void init() throws EmailException {
    super.init();

    try {
      // BCC anyone who has interest in this project's changes
      // Try to mark interested owners with a TO and not a BCC line.
      //
      Watchers matching = getWatches(NotifyType.NEW_CHANGES);
      for (Account.Id user : matching.accounts) {
        if (isOwnerOfProjectOrBranch(user)) {
          add(RecipientType.TO, user);
        } else {
          add(RecipientType.BCC, user);
        }
      }
      for (Address addr : matching.emails) {
        add(RecipientType.BCC, addr);
      }
    } catch (OrmException err) {
      // Just don't CC everyone. Better to send a partial message to those
      // we already have queued up then to fail deliver entirely to people
      // who have a lower interest in the change.
      log.warn("Cannot BCC watchers for new change", err);
    }
  }

  private boolean isOwnerOfProjectOrBranch(Account.Id user) {
    return projectState != null
        && change != null
        && projectState.controlFor(args.identifiedUserFactory.create(user))
          .controlForRef(change.getDest())
          .isOwner();
  }
}
