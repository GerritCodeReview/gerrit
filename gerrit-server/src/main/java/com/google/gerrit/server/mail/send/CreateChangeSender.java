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

package com.google.gerrit.server.mail.send;

import com.google.common.collect.Iterables;
import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountProjectWatch.NotifyType;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.mail.RecipientType;
import com.google.gerrit.server.mail.send.ProjectWatch.Watchers;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Notify interested parties of a brand new change. */
public class CreateChangeSender extends NewChangeSender {
  private static final Logger log = LoggerFactory.getLogger(CreateChangeSender.class);

  public interface Factory {
    CreateChangeSender create(Project.NameKey project, Change.Id id);
  }

  @Inject
  public CreateChangeSender(
      EmailArguments ea, @Assisted Project.NameKey project, @Assisted Change.Id id)
      throws OrmException {
    super(ea, newChangeData(ea, project, id));
  }

  @Override
  protected void init() throws EmailException {
    super.init();

    if (change.getStatus() == Change.Status.NEW) {
      try {
        // Try to mark interested owners with TO and CC or BCC line.
        Watchers matching = getWatchers(NotifyType.NEW_CHANGES);
        for (Account.Id user :
            Iterables.concat(matching.to.accounts, matching.cc.accounts, matching.bcc.accounts)) {
          if (isOwnerOfProjectOrBranch(user)) {
            add(RecipientType.TO, user);
          }
        }

        // Add everyone else. Owners added above will not be duplicated.
        add(RecipientType.TO, matching.to);
        add(RecipientType.CC, matching.cc);
        add(RecipientType.BCC, matching.bcc);
      } catch (OrmException err) {
        // Just don't CC everyone. Better to send a partial message to those
        // we already have queued up then to fail deliver entirely to people
        // who have a lower interest in the change.
        log.warn("Cannot notify watchers for new change", err);
      }

      includeWatchers(NotifyType.NEW_PATCHSETS);
    }
  }

  private boolean isOwnerOfProjectOrBranch(Account.Id user) {
    return projectState != null
        && projectState
            .controlFor(args.identifiedUserFactory.create(user))
            .controlForRef(change.getDest())
            .isOwner();
  }
}
