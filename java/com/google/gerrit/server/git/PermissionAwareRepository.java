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
import org.eclipse.jgit.lib.DelegateRepository;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;

/**
 * Wrapper around {@link DelegateRepository} that overwrites {@link #getRefDatabase()} to return a
 * {@link PermissionAwareReadOnlyRefDatabase}.
 */
public class PermissionAwareRepository extends DelegateRepository {

  private final PermissionAwareReadOnlyRefDatabase permissionAwareReadOnlyRefDatabase;

  public PermissionAwareRepository(Repository delegate, PermissionBackend.ForProject forProject) {
    super(delegate, false);
    this.permissionAwareReadOnlyRefDatabase =
        new PermissionAwareReadOnlyRefDatabase(delegate, forProject);
  }

  @Override
  public RefDatabase getRefDatabase() {
    return permissionAwareReadOnlyRefDatabase;
  }
}
