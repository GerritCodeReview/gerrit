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
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.Project.NameKey;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;

import java.util.Collections;
import java.util.List;

public class GitReferenceUpdated {
  public static final GitReferenceUpdated DISABLED = new GitReferenceUpdated(
      Collections.<GitReferenceUpdatedListener> emptyList());

  private final Iterable<GitReferenceUpdatedListener> listeners;
  private final String anonymousCowardName;

  @Inject
  GitReferenceUpdated(DynamicSet<GitReferenceUpdatedListener> listeners,
      @AnonymousCowardName String anonymousCowardName) {
    this.listeners = listeners;
    this.anonymousCowardName = anonymousCowardName;
  }

  GitReferenceUpdated(Iterable<GitReferenceUpdatedListener> listeners) {
    this.listeners = listeners;
    this.anonymousCowardName = "";
  }

  public void fire(Project.NameKey project, RefUpdate update, Account account) {
    fire(project, update.getName(), update.getOldObjectId(),
        update.getNewObjectId(), account);
  }

  public void fire(Project.NameKey project, String ref, ObjectId oldId,
      ObjectId newId, Account account) {
    Event event =
        new Event(project, ref, objectName(oldId), objectName(newId),
            accountName(account));
    for (GitReferenceUpdatedListener l : listeners) {
      l.onGitReferenceUpdated(event);
    }
  }

  private String objectName(ObjectId objId) {
    return objId == null ? null : objId.getName();
  }

  private String accountName(Account account) {
    if (account == null) {
      return anonymousCowardName;
    }

    String result =
        (account.getFullName() == null) ? anonymousCowardName : account
            .getFullName();
    if (account.getPreferredEmail() != null) {
      result += " (" + account.getPreferredEmail() + ")";
    }
    return result;
  }


  public void fire(NameKey parentKey, String refsNotesReview) {
    fire(parentKey, refsNotesReview, null, null, null);
  }

  private static class Event implements GitReferenceUpdatedListener.Event {
    private final String projectName;
    private final String ref;
    private final String oldId;
    private final String newId;
    private final String submitter;

    Event(Project.NameKey project, String ref, String oldId, String newId,
        String account) {
      this.projectName = project.get();
      this.ref = ref;
      this.oldId = oldId;
      this.newId = newId;
      this.submitter = account;
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

            @Override
            public String getNewObjectId() {
              return newId;
            }

            @Override
            public String getOldObjectId() {
              return oldId;
            }

            @Override
            public String getSubmitter() {
              return submitter;
            }
          };
      return ImmutableList.of(update);
    }
  }

}
