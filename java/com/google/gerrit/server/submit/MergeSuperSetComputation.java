// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.submit;

import com.google.gerrit.extensions.annotations.ExtensionPoint;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.permissions.PermissionBackendException;
import java.io.IOException;

/**
 * Interface to compute the merge super set to detect changes that should be submitted together.
 *
 * <p>E.g. to speed up performance implementations could decide to do the computation in batches in
 * parallel on different server nodes.
 */
@ExtensionPoint
public interface MergeSuperSetComputation {

  /**
   * Compute the set of changes that should be submitted together. As input a set of changes is
   * provided for which it is known that they should be submitted together. This method should
   * complete the set by including open predecessor changes that need to be submitted as well. To
   * decide whether open predecessor changes should be included the method must take the submit type
   * into account (e.g. for changes with submit type "Cherry-Pick" open predecessor changes must not
   * be included).
   *
   * <p>This method is invoked iteratively while new changes to be submitted together are discovered
   * by expanding the topics of the changes. This method must not do any topic expansion on its own.
   *
   * @param orm {@link MergeOpRepoManager} that should be used to access repositories
   * @param changeSet A set of changes for which it is known that they should be submitted together
   * @param user The user for which the visibility checks should be performed
   * @return the completed set of changes that should be submitted together
   */
  ChangeSet completeWithoutTopic(MergeOpRepoManager orm, ChangeSet changeSet, CurrentUser user)
      throws IOException, PermissionBackendException;
}
