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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.inject.Inject;

import java.util.Collections;
import java.util.List;

public class GitReferenceUpdated {
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

  public void fire(Project.NameKey project, String ref) {
    Event event = new Event(project, ref);
    for (GitReferenceUpdatedListener l : listeners) {
      l.onGitReferenceUpdated(event);
    }
  }

  private static class Event implements GitReferenceUpdatedListener.Event {
    private final String projectName;
    private final String ref;

    Event(Project.NameKey project, String ref) {
      this.projectName = project.get();
      this.ref = ref;
    }

    @Override
    public String getProjectName() {
      return projectName;
    }

    @Override
    public List<GitReferenceUpdatedListener.Update> getUpdates() {
      GitReferenceUpdatedListener.Update update =
          new GitReferenceUpdatedListener.Update() {
            public String getRefName() {
              return ref;
            }
          };
      return ImmutableList.of(update);
    }
  }
}
