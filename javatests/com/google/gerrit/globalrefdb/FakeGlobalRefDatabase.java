// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.globalrefdb;

import com.google.common.collect.MapMaker;
import com.google.gerrit.reviewdb.client.Project;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.junit.Ignore;

@Ignore
public class FakeGlobalRefDatabase implements GlobalRefDatabase {

  private ConcurrentMap<Project.NameKey, ConcurrentMap<String, AtomicReference<ObjectId>>>
      keyValueStore;

  public FakeGlobalRefDatabase() {
    keyValueStore = new MapMaker().concurrencyLevel(1).makeMap();
  }

  @Override
  public boolean isUpToDate(Project.NameKey project, Ref ref) throws GlobalRefDbLockException {
    AtomicReference<ObjectId> value = projectRefDb(project).get(ref.getName());
    if (value == null) {
      return true;
    }
    return ref.getObjectId().equals(value.get());
  }

  @Override
  public boolean compareAndPut(Project.NameKey project, Ref currRef, ObjectId newRefValue)
      throws GlobalRefDbSystemError {
    ConcurrentMap<String, AtomicReference<ObjectId>> projectRefDb = projectRefDb(project);
    AtomicReference<ObjectId> currValue = projectRefDb.get(currRef.getName());
    if (currValue == null) {
      projectRefDb.put(currRef.getName(), new AtomicReference<>(newRefValue));
      return true;
    }

    return currValue.compareAndSet(currRef.getObjectId(), newRefValue);
  }

  @Override
  public AutoCloseable lockRef(Project.NameKey project, String refName)
      throws GlobalRefDbLockException {
    return null;
  }

  @Override
  public boolean exists(Project.NameKey project, String refName) {
    return projectRefDb(project).containsKey(refName);
  }

  @Override
  public void remove(Project.NameKey project) throws GlobalRefDbSystemError {
    keyValueStore.remove(project);
  }

  private ConcurrentMap<String, AtomicReference<ObjectId>> projectRefDb(Project.NameKey project) {
    ConcurrentMap<String, AtomicReference<ObjectId>> projectRefDb = keyValueStore.get(project);
    if (projectRefDb == null) {
      projectRefDb = new MapMaker().concurrencyLevel(1).makeMap();
      keyValueStore.put(project, projectRefDb);
    }

    return projectRefDb;
  }
}
