// Copyright (C) 2018 The Android Open Source Project
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

import com.google.gerrit.common.Nullable;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import org.eclipse.jgit.lib.Config;

/** Provides {@link GerritInstanceName} from {@code gerrit.name}. */
@Singleton
public class GerritInstanceNameProvider implements Provider<String> {
  private final String instanceName;

  @Inject
  public GerritInstanceNameProvider(
      @GerritServerConfig Config config,
      @CanonicalWebUrl @Nullable Provider<String> canonicalUrlProvider) {
    this.instanceName = getInstanceName(config, canonicalUrlProvider);
  }

  private String getInstanceName(Config config, @Nullable Provider<String> canonicalUrlProvider) {
    String instanceName = config.getString("gerrit", null, "shortName");
    if (instanceName != null || canonicalUrlProvider == null) {
      return instanceName;
    }

    return canonicalUrlProvider.get();
  }

  @Override
  public String get() {
    return instanceName;
  }
}
