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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.change.AccountPatchReviewStore;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * An implementation of the {@link AccountPatchReviewStore} that's only used in tests. This
 * implementation stores reviewed files in memory.
 */
@Singleton
public class FakeAccountPatchReviewStore implements AccountPatchReviewStore, LifecycleListener {

  private final Set<Entity> store = new HashSet<>();

  @Override
  public void start() {}

  @Override
  public void stop() {}

  public static class FakeAccountPatchReviewStoreModule extends LifecycleModule {
    @Override
    protected void configure() {
      DynamicItem.bind(binder(), AccountPatchReviewStore.class)
          .to(FakeAccountPatchReviewStore.class);
      listener().to(FakeAccountPatchReviewStore.class);
    }
  }

  @AutoValue
  abstract static class Entity {
    abstract PatchSet.Id psId();

    abstract Account.Id accountId();

    abstract String path();

    static Entity create(PatchSet.Id psId, Account.Id accountId, String path) {
      return new AutoValue_FakeAccountPatchReviewStore_Entity(psId, accountId, path);
    }
  }

  @Override
  @CanIgnoreReturnValue
  public boolean markReviewed(PatchSet.Id psId, Account.Id accountId, String path) {
    synchronized (store) {
      Entity entity = Entity.create(psId, accountId, path);
      return store.add(entity);
    }
  }

  @Override
  public void markReviewed(PatchSet.Id psId, Account.Id accountId, Collection<String> paths) {
    paths.forEach(path -> markReviewed(psId, accountId, path));
  }

  @Override
  public void clearReviewed(PatchSet.Id psId, Account.Id accountId, String path) {
    synchronized (store) {
      store.remove(Entity.create(psId, accountId, path));
    }
  }

  @Override
  public void clearReviewed(PatchSet.Id psId) {
    synchronized (store) {
      List<Entity> toRemove = new ArrayList<>();
      for (Entity entity : store) {
        if (entity.psId().equals(psId)) {
          toRemove.add(entity);
        }
      }
      store.removeAll(toRemove);
    }
  }

  @Override
  public void clearReviewed(Change.Id changeId) {
    synchronized (store) {
      List<Entity> toRemove = new ArrayList<>();
      for (Entity entity : store) {
        if (entity.psId().changeId().equals(changeId)) {
          toRemove.add(entity);
        }
      }
      store.removeAll(toRemove);
    }
  }

  @Override
  public void clearReviewedBy(Account.Id accountId) {
    synchronized (store) {
      List<Entity> toRemove = new ArrayList<>();
      for (Entity entity : store) {
        if (entity.accountId().equals(accountId)) {
          toRemove.add(entity);
        }
      }
      store.removeAll(toRemove);
    }
  }

  @Override
  public Optional<PatchSetWithReviewedFiles> findReviewed(PatchSet.Id psId, Account.Id accountId) {
    synchronized (store) {
      int matchedPsNumber = -1;
      Optional<PatchSetWithReviewedFiles> result = Optional.empty();
      for (Entity entity : store) {
        if (!entity.accountId().equals(accountId)
            || !entity.psId().changeId().equals(psId.changeId())) {
          continue;
        }
        int entityPsNumber = Integer.parseInt(entity.psId().getId());
        if (entityPsNumber <= psId.get() && entityPsNumber > matchedPsNumber) {
          matchedPsNumber = entityPsNumber;
          result =
              Optional.of(
                  PatchSetWithReviewedFiles.create(
                      PatchSet.id(psId.changeId(), matchedPsNumber),
                      ImmutableSet.of(entity.path())));
        }
      }
      return result;
    }
  }
}
