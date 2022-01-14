// Copyright (C) 2022 The Android Open Source Project
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
import com.google.gerrit.extensions.events.GitReferencesUpdatedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.plugincontext.PluginContext.PluginMetrics;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class GitReferenceUpdatedTest {
  private DynamicSet<GitReferencesUpdatedListener> listeners;

  @Mock GitReferencesUpdatedListener listener;
  @Mock EventUtil util;
  @Mock AccountState updater;

  @Before
  public void setup() {
    listeners = new DynamicSet<>();
    listeners.add("gerrit", listener);
  }

  @Test
  public void aSingleEventPerBatchRefUpdateIsFired() {
    BatchRefUpdate update = newBatchRefUpdate();
    update.addCommand(
        new ReceiveCommand(
            ObjectId.zeroId(),
            ObjectId.fromString("0000000000000000000000000000000000000001"),
            "refs/changes/01/1/1"));
    update.addCommand(
        new ReceiveCommand(
            ObjectId.zeroId(),
            ObjectId.fromString("0000000000000000000000000000000000000001"),
            "refs/changes/01/1/meta"));

    GitReferencesUpdated event =
        new GitReferencesUpdated(
            new PluginSetContext<>(listeners, PluginMetrics.DISABLED_INSTANCE), null, util);
    event.fire(Project.NameKey.parse("project"), update, updater);
    Mockito.verify(listener, Mockito.times(1)).onGitReferencesUpdated(Mockito.any());
  }

  private static BatchRefUpdate newBatchRefUpdate() {
    try (Repository repo = new InMemoryRepository(new DfsRepositoryDescription("repo"))) {
      return repo.getRefDatabase().newBatchUpdate();
    }
  }
}
