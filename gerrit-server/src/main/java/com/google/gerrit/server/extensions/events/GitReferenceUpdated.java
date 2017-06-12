// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.extensions.events;

import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Project;
import com.google.inject.Inject;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.transport.ReceiveCommand;

public class GitReferenceUpdated {
  public static final GitReferenceUpdated DISABLED =
      new GitReferenceUpdated() {
        @Override
        public void fire(
            Project.NameKey project,
            RefUpdate refUpdate,
            ReceiveCommand.Type type,
            Account updater) {}

        @Override
        public void fire(Project.NameKey project, RefUpdate refUpdate, Account updater) {}

        @Override
        public void fire(
            Project.NameKey project,
            String ref,
            ObjectId oldObjectId,
            ObjectId newObjectId,
            Account updater) {}

        @Override
        public void fire(Project.NameKey project, ReceiveCommand cmd, Account updater) {}

        @Override
        public void fire(Project.NameKey project, BatchRefUpdate batchRefUpdate, Account updater) {}
      };

  private final DynamicSet<GitReferenceUpdatedListener> listeners;
  private final EventUtil util;

  @Inject
  GitReferenceUpdated(DynamicSet<GitReferenceUpdatedListener> listeners, EventUtil util) {
    this.listeners = listeners;
    this.util = util;
  }

  private GitReferenceUpdated() {
    this.listeners = null;
    this.util = null;
  }

  public void fire(
      Project.NameKey project, RefUpdate refUpdate, ReceiveCommand.Type type, Account updater) {
    fire(
        project,
        refUpdate.getName(),
        refUpdate.getOldObjectId(),
        refUpdate.getNewObjectId(),
        type,
        util.accountInfo(updater));
  }

  public void fire(Project.NameKey project, RefUpdate refUpdate, Account updater) {
    fire(
        project,
        refUpdate.getName(),
        refUpdate.getOldObjectId(),
        refUpdate.getNewObjectId(),
        ReceiveCommand.Type.UPDATE,
        util.accountInfo(updater));
  }

  public void fire(
      Project.NameKey project,
      String ref,
      ObjectId oldObjectId,
      ObjectId newObjectId,
      Account updater) {
    fire(
        project,
        ref,
        oldObjectId,
        newObjectId,
        ReceiveCommand.Type.UPDATE,
        util.accountInfo(updater));
  }

  public void fire(Project.NameKey project, ReceiveCommand cmd, Account updater) {
    fire(
        project,
        cmd.getRefName(),
        cmd.getOldId(),
        cmd.getNewId(),
        cmd.getType(),
        util.accountInfo(updater));
  }

  public void fire(Project.NameKey project, BatchRefUpdate batchRefUpdate, Account updater) {
    if (!listeners.iterator().hasNext()) {
      return;
    }
    for (ReceiveCommand cmd : batchRefUpdate.getCommands()) {
      if (cmd.getResult() == ReceiveCommand.Result.OK) {
        fire(
            project,
            cmd.getRefName(),
            cmd.getOldId(),
            cmd.getNewId(),
            cmd.getType(),
            util.accountInfo(updater));
      }
    }
  }

  private void fire(
      Project.NameKey project,
      String ref,
      ObjectId oldObjectId,
      ObjectId newObjectId,
      ReceiveCommand.Type type,
      AccountInfo updater) {
    if (!listeners.iterator().hasNext()) {
      return;
    }
    ObjectId o = oldObjectId != null ? oldObjectId : ObjectId.zeroId();
    ObjectId n = newObjectId != null ? newObjectId : ObjectId.zeroId();
    Event event = new Event(project, ref, o.name(), n.name(), type, updater);
    for (GitReferenceUpdatedListener l : listeners) {
      try {
        l.onGitReferenceUpdated(event);
      } catch (Exception e) {
        util.logEventListenerError(this, l, e);
      }
    }
  }

  private static class Event implements GitReferenceUpdatedListener.Event {
    private final String projectName;
    private final String ref;
    private final String oldObjectId;
    private final String newObjectId;
    private final ReceiveCommand.Type type;
    private final AccountInfo updater;

    Event(
        Project.NameKey project,
        String ref,
        String oldObjectId,
        String newObjectId,
        ReceiveCommand.Type type,
        AccountInfo updater) {
      this.projectName = project.get();
      this.ref = ref;
      this.oldObjectId = oldObjectId;
      this.newObjectId = newObjectId;
      this.type = type;
      this.updater = updater;
    }

    @Override
    public String getProjectName() {
      return projectName;
    }

    @Override
    public String getRefName() {
      return ref;
    }

    @Override
    public String getOldObjectId() {
      return oldObjectId;
    }

    @Override
    public String getNewObjectId() {
      return newObjectId;
    }

    @Override
    public boolean isCreate() {
      return type == ReceiveCommand.Type.CREATE;
    }

    @Override
    public boolean isDelete() {
      return type == ReceiveCommand.Type.DELETE;
    }

    @Override
    public boolean isNonFastForward() {
      return type == ReceiveCommand.Type.UPDATE_NONFASTFORWARD;
    }

    @Override
    public AccountInfo getUpdater() {
      return updater;
    }

    @Override
    public String toString() {
      return String.format(
          "%s[%s,%s: %s -> %s]",
          getClass().getSimpleName(), projectName, ref, oldObjectId, newObjectId);
    }

    @Override
    public NotifyHandling getNotify() {
      return NotifyHandling.ALL;
    }
  }
}
