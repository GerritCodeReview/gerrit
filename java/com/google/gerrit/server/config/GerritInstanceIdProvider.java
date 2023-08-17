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

import com.google.common.flogger.FluentLogger;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.regex.Pattern;
import org.eclipse.jgit.lib.Config;

/** Provides {@link GerritInstanceId} from {@code gerrit.instanceId}. */
@Singleton
public class GerritInstanceIdProvider implements Provider<String> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final Pattern ENV_VAR_PATTERN = Pattern.compile("^\\$[A-Za-z_][A-Za-z0-9_]*$");
  private final String instanceId;

  @Inject
  public GerritInstanceIdProvider(@GerritServerConfig Config cfg, EnvVarProvider envVarProvider) {
    String instanceIdCfg = cfg.getString("gerrit", null, "instanceId");
    if (instanceIdCfg != null && ENV_VAR_PATTERN.matcher(instanceIdCfg).matches()) {
      instanceId = envVarProvider.get(instanceIdCfg.substring(1));
      if (instanceId == null) {
        logger.atWarning().log(
            "Could not find environment variable %s to read instance ID. No instance ID will be set.",
            instanceIdCfg);
      }
    } else {
      instanceId = instanceIdCfg;
    }
  }

  @Override
  public String get() {
    return instanceId;
  }
}
