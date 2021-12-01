// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.server.project;

import com.google.common.collect.ImmutableSortedSet;
import com.google.gerrit.entities.AccountGroup.UUID;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.exceptions.StorageException;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;

/** An implementation of {@link ProjectCache} with no operations implemented. */
public class NullProjectCache implements ProjectCache {

  @Override
  public ProjectState getAllProjects() {
    throw new UnsupportedOperationException();
  }

  @Override
  public ProjectState getAllUsers() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Optional<ProjectState> get(NameKey projectName) throws StorageException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void evict(NameKey p) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void remove(Project p) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void remove(NameKey name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ImmutableSortedSet<NameKey> all() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Set<UUID> guessRelevantGroupUUIDs() {
    throw new UnsupportedOperationException();
  }

  @Override
  public ImmutableSortedSet<NameKey> byName(String prefix) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void onCreateProject(NameKey newProjectName) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void evictAndReindex(Project p) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void evictAndReindex(NameKey p) {
    throw new UnsupportedOperationException();
  }
}
