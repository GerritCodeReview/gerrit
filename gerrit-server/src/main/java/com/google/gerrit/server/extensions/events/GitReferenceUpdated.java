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

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
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

  public void fire(Project.NameKey project, RefUpdate refUpdate) {
    fire(project, refUpdate.getName(), refUpdate.getOldObjectId(),
        refUpdate.getNewObjectId());
  }

  public void fire(Project.NameKey project, String ref,
      ObjectId oldObjectId, ObjectId newObjectId) {
    ObjectId o = oldObjectId != null ? oldObjectId : ObjectId.zeroId();
    ObjectId n = newObjectId != null ? newObjectId : ObjectId.zeroId();
    Event event = new Event(project, ref, o.name(), n.name());
    for (GitReferenceUpdatedListener l : listeners) {
      try {
        l.onGitReferenceUpdated(event);
      } catch (RuntimeException e) {
        log.warn("Failure in GitReferenceUpdatedListener", e);
      }
    }
  }

  private static class Event implements GitReferenceUpdatedListener.Event {
    private final String projectName;
    private final String ref;
    private final String oldObjectId;
    private final String newObjectId;

    Event(Project.NameKey project, String ref,
        String oldObjectId, String newObjectId) {
      this.projectName = project.get();
      this.ref = ref;
      this.oldObjectId = oldObjectId;
      this.newObjectId = newObjectId;
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
  }
}
