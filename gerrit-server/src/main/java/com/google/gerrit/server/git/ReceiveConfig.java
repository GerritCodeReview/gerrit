// Copyright (C) 2013 The Android Open Source Project
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

import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Config;

@Singleton
class ReceiveConfig {
  final boolean checkMagicRefs;
  final boolean checkReferencedObjectsAreReachable;
  final boolean allowDrafts;
  final int maxBatchChanges;

  @Inject
  ReceiveConfig(@GerritServerConfig Config config) {
    checkMagicRefs = config.getBoolean(
        "receive", null, "checkMagicRefs",
        true);
    checkReferencedObjectsAreReachable = config.getBoolean(
        "receive", null, "checkReferencedObjectsAreReachable",
        true);
    allowDrafts = config.getBoolean(
        "change", null, "allowDrafts",
        true);
    maxBatchChanges = config.getInt("receive", "maxBatchChanges", 0);
  }
}
