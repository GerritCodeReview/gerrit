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

import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.events.GitReferencesUpdatedListener;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.transport.ReceiveCommand;

/** Helper class to fire an event when a Git reference has been updated. */
@Singleton
public class GitReferenceUpdated {
  public static final GitReferenceUpdated DISABLED =
      new GitReferenceUpdated() {
        @Override
        public void fire(
            Project.NameKey project,
            RefUpdate refUpdate,
            ReceiveCommand.Type type,
            AccountState updater) {}

        @Override
        public void fire(Project.NameKey project, RefUpdate refUpdate, AccountState updater) {}

        @Override
        public void fire(
            Project.NameKey project,
            String ref,
            ObjectId oldObjectId,
            ObjectId newObjectId,
            AccountState updater) {}

        @Override
        public void fire(Project.NameKey project, ReceiveCommand cmd, AccountState updater) {}

        @Override
        public void fire(
            Project.NameKey project, BatchRefUpdate batchRefUpdate, AccountState updater) {}
      };

  private final PluginSetContext<GitReferencesUpdatedListener> listeners;
  private final EventUtil util;

  @Inject
  GitReferenceUpdated(PluginSetContext<GitReferencesUpdatedListener> listeners, EventUtil util) {
    this.listeners = listeners;
    this.util = util;
  }

  private GitReferenceUpdated() {
    this.listeners = null;
    this.util = null;
  }

  public void fire(
      Project.NameKey project,
      RefUpdate refUpdate,
      ReceiveCommand.Type type,
      AccountState updater) {
    fire(
        project,
        new UpdatedRef(
            refUpdate.getName(), refUpdate.getOldObjectId(), refUpdate.getNewObjectId(), type),
        util.accountInfo(updater));
  }

  public void fire(Project.NameKey project, RefUpdate refUpdate, AccountState updater) {
    fire(
        project,
        new UpdatedRef(
            refUpdate.getName(),
            refUpdate.getOldObjectId(),
            refUpdate.getNewObjectId(),
            ReceiveCommand.Type.UPDATE),
        util.accountInfo(updater));
  }

  public void fire(
      Project.NameKey project,
      String ref,
      ObjectId oldObjectId,
      ObjectId newObjectId,
      AccountState updater) {
    fire(
        project,
        new UpdatedRef(ref, oldObjectId, newObjectId, ReceiveCommand.Type.UPDATE),
        util.accountInfo(updater));
  }

  public void fire(Project.NameKey project, ReceiveCommand cmd, AccountState updater) {
    fire(
        project,
        new UpdatedRef(cmd.getRefName(), cmd.getOldId(), cmd.getNewId(), cmd.getType()),
        util.accountInfo(updater));
  }

  public void fire(Project.NameKey project, BatchRefUpdate batchRefUpdate, AccountState updater) {
    if (listeners.isEmpty()) {
      return;
    }
    ArrayList<GitReferencesUpdatedListener.UpdatedRef> updates = new ArrayList<>();
    for (ReceiveCommand cmd : batchRefUpdate.getCommands()) {
      if (cmd.getResult() == ReceiveCommand.Result.OK) {
        updates.add(
            new UpdatedRef(cmd.getRefName(), cmd.getOldId(), cmd.getNewId(), cmd.getType()));
      }
    }
    fire(project, updates, util.accountInfo(updater));
  }

  private void fire(Project.NameKey project, UpdatedRef updatedRef, AccountInfo updater) {
    fire(project, List.of(updatedRef), updater);
  }

  private void fire(
      Project.NameKey project,
      List<GitReferencesUpdatedListener.UpdatedRef> updatedRefs,
      AccountInfo updater) {
    if (listeners.isEmpty()) {
      return;
    }
    Event event = new Event(project, updatedRefs, updater);
    listeners.runEach(l -> l.onGitReferencesUpdated(event));
  }

  public static class UpdatedRef implements GitReferencesUpdatedListener.UpdatedRef {
    private final String ref;
    private final ObjectId oldObjectId;
    private final ObjectId newObjectId;
    private final ReceiveCommand.Type type;

    public UpdatedRef(
        String ref, ObjectId oldObjectId, ObjectId newObjectId, ReceiveCommand.Type type) {
      this.ref = ref;
      this.oldObjectId = oldObjectId != null ? oldObjectId : ObjectId.zeroId();
      this.newObjectId = newObjectId != null ? newObjectId : ObjectId.zeroId();
      this.type = type;
    }

    @Override
    public String getRefName() {
      return ref;
    }

    @Override
    public String getOldObjectId() {
      return oldObjectId.name();
    }

    @Override
    public String getNewObjectId() {
      return newObjectId.name();
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
    public String toString() {
      return String.format("{%s: %s -> %s}", ref, oldObjectId, newObjectId);
    }
  }

  /** Event to be fired when a Git reference has been updated. */
  public static class Event implements GitReferencesUpdatedListener.Event {
    private final String projectName;
    private final List<GitReferencesUpdatedListener.UpdatedRef> updatedRefs;
    private final AccountInfo updater;

    public Event(
        Project.NameKey project,
        List<GitReferencesUpdatedListener.UpdatedRef> updatedRefs,
        AccountInfo updater) {
      this.projectName = project.get();
      this.updatedRefs = updatedRefs;
      this.updater = updater;
    }

    @Override
    public String getProjectName() {
      return projectName;
    }

    @Override
    public List<GitReferencesUpdatedListener.UpdatedRef> getUpdatedRefs() {
      return updatedRefs;
    }

    @Override
    public List<String> getRefNames() {
      return updatedRefs.stream()
          .map(GitReferencesUpdatedListener.UpdatedRef::getRefName)
          .collect(Collectors.toList());
    }

    @Override
    public AccountInfo getUpdater() {
      return updater;
    }

    @Override
    public String toString() {
      return String.format("%s[%s,%s]", getClass().getSimpleName(), projectName, updatedRefs);
    }

    @Override
    public NotifyHandling getNotify() {
      return NotifyHandling.ALL;
    }
  }
}
