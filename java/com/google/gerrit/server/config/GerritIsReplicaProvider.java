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

package com.google.gerrit.server.config;

import com.google.gerrit.server.util.ReplicaUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import org.eclipse.jgit.lib.Config;

/**
 * Provides {@link Boolean} annotated with {@link GerritIsReplica}.
 *
 * <p>The returned boolean indicates whether Gerrit is run as a read-only replica.
 */
@Singleton
public final class GerritIsReplicaProvider implements Provider<Boolean> {
  private final Config config;

  @Inject
  public GerritIsReplicaProvider(@GerritServerConfig Config config) {
    this.config = config;
  }

  @Override
  public Boolean get() {
    return ReplicaUtil.isReplica(config);
  }
}
