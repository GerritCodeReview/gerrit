// Copyright (C) 2015 The Android Open Source Project
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

import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Config;

import java.util.concurrent.TimeUnit;

@Singleton
public class ChangeCleanupConfig {
  private static String SECTION = "changeCleanup";
  private static String KEY_ABANDON_AFTER = "abandonAfter";
  private static String KEY_ABANDON_IF_MERGEABLE = "abandonIfMergeable";
  private static String KEY_ABANDON_MESSAGE = "abandonMessage";
  private static String DEFAULT_ABANDON_MESSAGE =
      "Auto-Abandoned due to inactivity, see "
      + "${URL}Documentation/user-change-cleanup.html#auto-abandon\n"
      + "\n"
      + "If this change is still wanted it should be restored.";
  private static String KEY_REBASE_AFTER = "rebaseAfter";

  private final ScheduleConfig scheduleConfig;
  private final long abandonAfter;
  private final String abandonMessage;
  private final boolean abandonIfMergeable;
  private final long rebaseAfter;

  @Inject
  ChangeCleanupConfig(@GerritServerConfig Config cfg,
      @CanonicalWebUrl String canonicalWebUrl) {
    scheduleConfig = new ScheduleConfig(cfg, SECTION);
    abandonAfter = readTime(cfg, KEY_ABANDON_AFTER);
    abandonIfMergeable = cfg.getBoolean(SECTION, null, KEY_ABANDON_IF_MERGEABLE, true);
    abandonMessage = readAbandonMessage(cfg, canonicalWebUrl);
    rebaseAfter = readTime(cfg, KEY_REBASE_AFTER);
  }

  private long readTime(Config cfg, String key) {
    long time =
        ConfigUtil.getTimeUnit(cfg, SECTION, null, key, 0,
            TimeUnit.MILLISECONDS);
    return time >= 0 ? time : 0;
  }

  private String readAbandonMessage(Config cfg, String webUrl) {
    String abandonMessage = cfg.getString(SECTION, null, KEY_ABANDON_MESSAGE);
    if (Strings.isNullOrEmpty(abandonMessage)) {
      abandonMessage = DEFAULT_ABANDON_MESSAGE;
    }
    abandonMessage = abandonMessage.replaceAll("\\$\\{URL\\}", webUrl);
    return abandonMessage;
  }

  public ScheduleConfig getScheduleConfig() {
    return scheduleConfig;
  }

  public long getAbandonAfter() {
    return abandonAfter;
  }

  public boolean getAbandonIfMergeable() {
    return abandonIfMergeable;
  }

  public String getAbandonMessage() {
    return abandonMessage;
  }

  public long getRebaseAfter() {
    return rebaseAfter;
  }
}
