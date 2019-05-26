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

package com.google.gerrit.server.change;

import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.annotations.ExtensionPoint;

/**
 * Allows plugins to contribute a value to the change ETag computation.
 *
 * <p>Plugins can affect the result of the get change / get change details REST endpoints by:
 *
 * <ul>
 *   <li>providing plugin defined attributes to {@link
 *       com.google.gerrit.extensions.common.ChangeInfo#plugins} (see {@link
 *       ChangeAttributeFactory})
 *   <li>implementing a {@link com.google.gerrit.server.rules.SubmitRule} which affects the
 *       computation of {@link com.google.gerrit.extensions.common.ChangeInfo#submittable}
 * </ul>
 *
 * <p>If the plugin defined part of {@link com.google.gerrit.extensions.common.ChangeInfo} depends
 * on plugin specific data, callers that use the change ETags to avoid unneeded recomputations of
 * ChangeInfos may see outdated plugin attributes and/or outdated submittable information, because a
 * ChangeInfo is only reloaded if the change ETag changes.
 *
 * <p>By implementating this interface plugins can contribute to the change ETag computation and
 * thus ensure that the ETag changes when the plugin data was changed. This way it is ensured that
 * callers do not see outdated ChangeInfos.
 *
 * @see ChangeResource#getETag()
 */
@ExtensionPoint
public interface ChangeETagComputation {
  /**
   * Computes an ETag of plugin-specific data for the given change.
   *
   * <p><strong>Note:</strong> Change ETags are computed very frequently and the computation must be
   * cheap. Take good care to not perform any expensive computations when implementing this.
   *
   * <p>If an error is encountered during the ETag computation the plugin can indicate this by
   * throwing any RuntimeException. In this case no value will be included in the change ETag
   * computation. This means if the error is transient, the ETag will differ when the computation
   * succeeds on a follow-up run.
   *
   * @param projectName the name of the project that contains the change
   * @param changeId ID of the change for which the ETag should be computed
   * @return the ETag
   */
  String getETag(Project.NameKey projectName, Change.Id changeId);
}
