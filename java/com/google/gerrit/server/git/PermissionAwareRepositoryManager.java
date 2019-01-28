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
import org.eclipse.jgit.lib.Repository;

/**
 * Wraps and unwraps existing repositories and makes them permission-aware by returning a {@link
 * PermissionAwareReadOnlyRefDatabase}.
 */
public class PermissionAwareRepositoryManager {
  public static Repository wrap(Repository delegate, PermissionBackend.ForProject forProject) {
    if (delegate instanceof PermissionAwareRepository) {
      new PermissionAwareRepository(((PermissionAwareRepository) delegate).unwrap(), forProject);
    }
    return new PermissionAwareRepository(delegate, forProject);
  }

  public static Repository unwrap(Repository repository) {
    if (repository instanceof PermissionAwareRepository) {
      return ((PermissionAwareRepository) repository).unwrap();
    }
    return repository;
  }
}
