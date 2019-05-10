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

package com.google.gerrit.server.git;

import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.inject.Singleton;
import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.lib.Repository;

/**
 * Wraps and unwraps existing repositories and makes them permission-aware by returning a {@link
 * PermissionAwareReadOnlyRefDatabase}.
 */
@Singleton
public class PermissionAwareRepositoryManagerImpl implements PermissionAwareRepositoryManager {
  @Override
  public Repository wrap(Repository delegate, PermissionBackend.ForProject forProject) {
    if (delegate instanceof PermissionAwareRepositoryWrapper) {
      return wrap(((PermissionAwareRepositoryWrapper) delegate).unwrap(), forProject);
    }

    if (delegate instanceof DfsRepository) {
      return new PermissionAwareDfsRepository((DfsRepository) delegate, forProject);
    }

    return new PermissionAwareRepository(delegate, forProject);
  }

  @Override
  public Repository unwrap(Repository repository) {
    if (repository instanceof PermissionAwareRepositoryWrapper) {
      return ((PermissionAwareRepositoryWrapper) repository).unwrap();
    }
    return repository;
  }
}
