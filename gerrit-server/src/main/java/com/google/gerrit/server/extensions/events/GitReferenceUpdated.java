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

import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;

public class GitReferenceUpdated {
  private static final Logger log = LoggerFactory
      .getLogger(GitReferenceUpdated.class);

  public static final GitReferenceUpdated DISABLED = new GitReferenceUpdated(
      Collections.<GitReferenceUpdatedListener> emptyList());

  private final Iterable<GitReferenceUpdatedListener> listeners;

  @Inject
  GitReferenceUpdated(DynamicSet<GitReferenceUpdatedListener> listeners) {
    this.listeners = listeners;
  }

  GitReferenceUpdated(Iterable<GitReferenceUpdatedListener> listeners) {
    this.listeners = listeners;
  }

  public void fire(Project.NameKey project, RefUpdate refUpdate,
      ReceiveCommand.Type type) {
    fire(project, refUpdate.getName(), refUpdate.getOldObjectId(),
        refUpdate.getNewObjectId(), type);
  }

  public void fire(Project.NameKey project, RefUpdate refUpdate) {
    fire(project, refUpdate.getName(), refUpdate.getOldObjectId(),
        refUpdate.getNewObjectId(), ReceiveCommand.Type.UPDATE);
  }

  public void fire(Project.NameKey project, String ref, ObjectId oldObjectId,
      ObjectId newObjectId, ReceiveCommand.Type type) {
    ObjectId o = oldObjectId != null ? oldObjectId : ObjectId.zeroId();
    ObjectId n = newObjectId != null ? newObjectId : ObjectId.zeroId();
    Event event = new Event(project, ref, o.name(), n.name(), type);
    for (GitReferenceUpdatedListener l : listeners) {
      try {
        l.onGitReferenceUpdated(event);
      } catch (RuntimeException e) {
        log.warn("Failure in GitReferenceUpdatedListener", e);
      }
    }
  }

  public void fire(Project.NameKey project, String ref, ObjectId oldObjectId,
      ObjectId newObjectId) {
    fire(project, ref, oldObjectId, newObjectId, ReceiveCommand.Type.UPDATE);
  }

  public void fire(Project.NameKey project, ReceiveCommand cmd) {
    fire(project, cmd.getRefName(), cmd.getOldId(), cmd.getNewId(), cmd.getType());
  }

  public void fire(Project.NameKey project, BatchRefUpdate batchRefUpdate) {
    for (ReceiveCommand cmd : batchRefUpdate.getCommands()) {
      if (cmd.getResult() == ReceiveCommand.Result.OK) {
        fire(project, cmd);
      }
    }
  }

  private static class Event implements GitReferenceUpdatedListener.Event {
    private final String projectName;
    private final String ref;
    private final String oldObjectId;
    private final String newObjectId;
    private final ReceiveCommand.Type type;

    Event(Project.NameKey project, String ref,
        String oldObjectId, String newObjectId,
        ReceiveCommand.Type type) {
      this.projectName = project.get();
      this.ref = ref;
      this.oldObjectId = oldObjectId;
      this.newObjectId = newObjectId;
      this.type = type;
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
    public String toString() {
      return String.format("%s[%s,%s: %s -> %s]", getClass().getSimpleName(),
          projectName, ref, oldObjectId, newObjectId);
    }
  }
}
