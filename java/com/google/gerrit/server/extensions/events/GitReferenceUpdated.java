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
import com.google.gerrit.extensions.events.GitBatchRefUpdateListener;
import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.LinkedHashSet;
import java.util.Set;
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

  private final PluginSetContext<GitBatchRefUpdateListener> batchRefUpdateListeners;
  private final PluginSetContext<GitReferenceUpdatedListener> refUpdatedListeners;
  private final EventUtil util;

  @Inject
  GitReferenceUpdated(
      PluginSetContext<GitBatchRefUpdateListener> batchRefUpdateListeners,
      PluginSetContext<GitReferenceUpdatedListener> refUpdatedListeners,
      EventUtil util) {
    this.batchRefUpdateListeners = batchRefUpdateListeners;
    this.refUpdatedListeners = refUpdatedListeners;
    this.util = util;
  }

  private GitReferenceUpdated() {
    this.batchRefUpdateListeners = null;
    this.refUpdatedListeners = null;
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
    if (batchRefUpdateListeners.isEmpty() && refUpdatedListeners.isEmpty()) {
      return;
    }
    Set<GitBatchRefUpdateListener.UpdatedRef> updates = new LinkedHashSet<>();
    for (ReceiveCommand cmd : batchRefUpdate.getCommands()) {
      if (cmd.getResult() == ReceiveCommand.Result.OK) {
        updates.add(
            new UpdatedRef(cmd.getRefName(), cmd.getOldId(), cmd.getNewId(), cmd.getType()));
      }
    }
    fireBatchRefUpdateEvent(project, updates, util.accountInfo(updater));
    fireRefUpdatedEvents(project, updates, util.accountInfo(updater));
  }

  private void fire(Project.NameKey project, UpdatedRef updatedRef, AccountInfo updater) {
    fireBatchRefUpdateEvent(project, Set.of(updatedRef), updater);
    fireRefUpdatedEvent(project, updatedRef, updater);
  }

  private void fireBatchRefUpdateEvent(
      Project.NameKey project,
      Set<GitBatchRefUpdateListener.UpdatedRef> updatedRefs,
      AccountInfo updater) {
    if (batchRefUpdateListeners.isEmpty()) {
      return;
    }
    GitBatchRefUpdateEvent event = new GitBatchRefUpdateEvent(project, updatedRefs, updater);
    batchRefUpdateListeners.runEach(l -> l.onGitBatchRefUpdate(event));
  }

  private void fireRefUpdatedEvents(
      Project.NameKey project,
      Set<GitBatchRefUpdateListener.UpdatedRef> updatedRefs,
      AccountInfo updater) {
    for (GitBatchRefUpdateListener.UpdatedRef updatedRef : updatedRefs) {
      fireRefUpdatedEvent(project, updatedRef, updater);
    }
  }

  private void fireRefUpdatedEvent(
      Project.NameKey project,
      GitBatchRefUpdateListener.UpdatedRef updatedRef,
      AccountInfo updater) {
    if (refUpdatedListeners.isEmpty()) {
      return;
    }
    GitReferenceUpdatedEvent event = new GitReferenceUpdatedEvent(project, updatedRef, updater);
    refUpdatedListeners.runEach(l -> l.onGitReferenceUpdated(event));
  }

  public static class UpdatedRef implements GitBatchRefUpdateListener.UpdatedRef {
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
  public static class GitBatchRefUpdateEvent implements GitBatchRefUpdateListener.Event {
    private final String projectName;
    private final Set<GitBatchRefUpdateListener.UpdatedRef> updatedRefs;
    private final AccountInfo updater;

    public GitBatchRefUpdateEvent(
        Project.NameKey project,
        Set<GitBatchRefUpdateListener.UpdatedRef> updatedRefs,
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
    public Set<GitBatchRefUpdateListener.UpdatedRef> getUpdatedRefs() {
      return updatedRefs;
    }

    @Override
    public Set<String> getRefNames() {
      return updatedRefs.stream()
          .map(GitBatchRefUpdateListener.UpdatedRef::getRefName)
          .collect(Collectors.toCollection(LinkedHashSet::new));
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

  public static class GitReferenceUpdatedEvent implements GitReferenceUpdatedListener.Event {

    private final String projectName;
    private final GitBatchRefUpdateListener.UpdatedRef updatedRef;
    private final AccountInfo updater;

    public GitReferenceUpdatedEvent(
        Project.NameKey project,
        GitBatchRefUpdateListener.UpdatedRef updatedRef,
        AccountInfo updater) {
      this.projectName = project.get();
      this.updatedRef = updatedRef;
      this.updater = updater;
    }

    @Override
    public String getProjectName() {
      return projectName;
    }

    @Override
    public NotifyHandling getNotify() {
      return NotifyHandling.ALL;
    }

    @Override
    public String getRefName() {
      return updatedRef.getRefName();
    }

    @Override
    public String getOldObjectId() {
      return updatedRef.getOldObjectId();
    }

    @Override
    public String getNewObjectId() {
      return updatedRef.getNewObjectId();
    }

    @Override
    public boolean isCreate() {
      return updatedRef.isCreate();
    }

    @Override
    public boolean isDelete() {
      return updatedRef.isDelete();
    }

    @Override
    public boolean isNonFastForward() {
      return updatedRef.isNonFastForward();
    }

    @Override
    public AccountInfo getUpdater() {
      return updater;
    }
  }
}
