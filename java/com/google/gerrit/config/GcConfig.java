// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.config;

import com.google.gerrit.config.ScheduleConfig.Schedule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ConfigConstants;

@Singleton
public class GcConfig {
  private final Optional<Schedule> schedule;
  private final boolean aggressive;

  @Inject
  GcConfig(@GerritServerConfig Config cfg) {
    schedule = ScheduleConfig.createSchedule(cfg, ConfigConstants.CONFIG_GC_SECTION);
    aggressive = cfg.getBoolean(ConfigConstants.CONFIG_GC_SECTION, "aggressive", false);
  }

  public Optional<Schedule> getSchedule() {
    return schedule;
  }

  public boolean isAggressive() {
    return aggressive;
  }
}
