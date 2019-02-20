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

package com.google.gerrit.plugins.checkers.api;

import com.google.common.collect.ImmutableListMultimap;
import com.google.gerrit.extensions.common.PluginDefinedInfo;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryProcessor;
import com.google.gerrit.server.query.change.ChangeQueryProcessor.ChangeAttributeFactory;
import com.google.inject.Singleton;

@Singleton
public class CombinedChangeStateFactory implements ChangeAttributeFactory {
  // TODO(hiesel): Inject cache and fill in implementation.

  @Override
  public PluginDefinedInfo create(ChangeData cd, ChangeQueryProcessor qp, String pluginName) {
    return new CheckPluginDefinedInfo(CombinedCheckState.combine(ImmutableListMultimap.of()));
  }
}
