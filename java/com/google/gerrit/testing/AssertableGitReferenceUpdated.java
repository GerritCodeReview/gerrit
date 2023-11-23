// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.testing;

import static com.google.common.truth.Truth.assertWithMessage;

import com.google.gerrit.entities.Project;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.inject.Singleton;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.transport.ReceiveCommand;

@Singleton
public class AssertableGitReferenceUpdated extends GitReferenceUpdated {

  private final AtomicInteger numInteractions = new AtomicInteger();

  @Override
  public void fire(
      Project.NameKey project,
      RefUpdate refUpdate,
      ReceiveCommand.Type type,
      AccountState updater) {
    numInteractions.incrementAndGet();
  }

  @Override
  public void fire(Project.NameKey project, RefUpdate refUpdate, AccountState updater) {
    numInteractions.incrementAndGet();
  }

  @Override
  public void fire(
      Project.NameKey project,
      String ref,
      ObjectId oldObjectId,
      ObjectId newObjectId,
      AccountState updater) {
    numInteractions.incrementAndGet();
  }

  @Override
  public void fire(Project.NameKey project, ReceiveCommand cmd, AccountState updater) {
    numInteractions.incrementAndGet();
  }

  @Override
  public void fire(Project.NameKey project, BatchRefUpdate batchRefUpdate, AccountState updater) {
    numInteractions.incrementAndGet();
  }

  public void assertInteractions(int expectedNumInteractions) {
    assertWithMessage("expectedGitReferenceUpdated")
        .that(numInteractions.get())
        .isEqualTo(expectedNumInteractions);
    numInteractions.set(0);
  }
}
