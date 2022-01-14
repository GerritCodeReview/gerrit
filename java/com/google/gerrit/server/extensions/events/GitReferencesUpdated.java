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
import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.extensions.events.GitReferencesUpdatedListener;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.transport.ReceiveCommand;

/** Helper class to fire an event when a Git reference has been updated. */
@Singleton
public class GitReferencesUpdated {
  public static final GitReferencesUpdated DISABLED =
      new GitReferencesUpdated() {
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

  private final PluginSetContext<GitReferencesUpdatedListener> refsUpdatedListeners;
  private final PluginSetContext<GitReferenceUpdatedListener> refUpdatedListeners;
  private final EventUtil util;

  @Inject
  GitReferencesUpdated(
      PluginSetContext<GitReferencesUpdatedListener> refsUpdatedListeners,
      PluginSetContext<GitReferenceUpdatedListener> refUpdatedListeners,
      EventUtil util) {
    this.refsUpdatedListeners = refsUpdatedListeners;
    this.refUpdatedListeners = refUpdatedListeners;
    this.util = util;
  }

  private GitReferencesUpdated() {
    this.refsUpdatedListeners = null;
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
    if (refsUpdatedListeners.isEmpty()) {
      return;
    }
    Set<GitReferencesUpdatedListener.UpdatedRef> updates = new HashSet<>();
    for (ReceiveCommand cmd : batchRefUpdate.getCommands()) {
      if (cmd.getResult() == ReceiveCommand.Result.OK) {
        updates.add(
            new UpdatedRef(cmd.getRefName(), cmd.getOldId(), cmd.getNewId(), cmd.getType()));
      }
    }
    fireRefsUpdatedEvent(project, updates, util.accountInfo(updater));
    fireRefUpdatedEvents(project, updates, util.accountInfo(updater));
  }

  private void fire(Project.NameKey project, UpdatedRef updatedRef, AccountInfo updater) {
    fireRefsUpdatedEvent(project, Set.of(updatedRef), updater);
    fireRefUpdatedEvent(project, updatedRef, updater);
  }

  private void fireRefsUpdatedEvent(
      Project.NameKey project,
      Set<GitReferencesUpdatedListener.UpdatedRef> updatedRefs,
      AccountInfo updater) {
    if (refsUpdatedListeners.isEmpty()) {
      return;
    }
    GitReferencesUpdatedEvent event = new GitReferencesUpdatedEvent(project, updatedRefs, updater);
    refsUpdatedListeners.runEach(l -> l.onGitReferencesUpdated(event));
  }

  private void fireRefUpdatedEvents(
      Project.NameKey project,
      Set<GitReferencesUpdatedListener.UpdatedRef> updatedRefs,
      AccountInfo updater) {
    for (GitReferencesUpdatedListener.UpdatedRef updatedRef : updatedRefs) {
      fireRefUpdatedEvent(project, updatedRef, updater);
    }
  }

  private void fireRefUpdatedEvent(
      Project.NameKey project,
      GitReferencesUpdatedListener.UpdatedRef updatedRef,
      AccountInfo updater) {
    if (refUpdatedListeners.isEmpty()) {
      return;
    }
    GitReferenceUpdatedEvent event = new GitReferenceUpdatedEvent(project, updatedRef, updater);
    refUpdatedListeners.runEach(l -> l.onGitReferenceUpdated(event));
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
  public static class GitReferencesUpdatedEvent implements GitReferencesUpdatedListener.Event {
    private final String projectName;
    private final Set<GitReferencesUpdatedListener.UpdatedRef> updatedRefs;
    private final AccountInfo updater;

    public GitReferencesUpdatedEvent(
        Project.NameKey project,
        Set<GitReferencesUpdatedListener.UpdatedRef> updatedRefs,
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
    public Set<GitReferencesUpdatedListener.UpdatedRef> getUpdatedRefs() {
      return updatedRefs;
    }

    @Override
    public Set<String> getRefNames() {
      return updatedRefs.stream()
          .map(GitReferencesUpdatedListener.UpdatedRef::getRefName)
          .collect(Collectors.toSet());
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
    private final GitReferencesUpdatedListener.UpdatedRef updatedRef;
    private final AccountInfo updater;

    public GitReferenceUpdatedEvent(
        Project.NameKey project,
        GitReferencesUpdatedListener.UpdatedRef updatedRef,
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
