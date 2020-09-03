// Copyright (C) 2020 The Android Open Source Project
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

import com.google.common.collect.ImmutableListMultimap;
import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.common.PluginDefinedInfo;
import com.google.gerrit.server.query.change.ChangeData;
import java.util.Collection;

/**
 * Interface to generate {@code PluginDefinedInfo}s from registered {@code
 * ChangePluginDefinedInfoFactory}s.
 *
 * <p>See the <a
 * href="https://gerrit-review.googlesource.com/Documentation/dev-plugins.html#query_attributes">
 * plugin developer documentation for more details and examples.
 */
public interface PluginDefinedInfosFactory {

  /**
   * Create a plugin-provided info field from all the plugins for each of the provided {@link
   * ChangeData}s.
   *
   * @param cds changes.
   * @return map of the all plugin's special infos for each change.
   */
  ImmutableListMultimap<Change.Id, PluginDefinedInfo> createPluginDefinedInfos(
      Collection<ChangeData> cds);
}
