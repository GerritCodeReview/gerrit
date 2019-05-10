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

import org.eclipse.jgit.lib.Repository;

import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.inject.ImplementedBy;

/**
 * Wraps and unwraps existing repositories and makes them permission-aware
 */
@ImplementedBy(PermissionAwareRepositoryManagerNoopImpl.class)
public interface PermissionAwareRepositoryManager {
	/**
	 * @param delegate the repository to wrap
	 * @param forProject the permission filter to apply to the wrapped calls
	 * @return a wrapper to the given delegate that will filter calls using
	 * the given forProject filter 
	 */
	Repository wrap(Repository delegate, PermissionBackend.ForProject forProject);

	/**
	 * @param repository
	 * @return Returns the unwrapped repository, if the argument is not a permission 
	 * aware repository (i.e. an instance of {@link PermissionAwareRepositoryWrapper}
	 * the call returns the argument 
	 */
	Repository unwrap(Repository repository);
}