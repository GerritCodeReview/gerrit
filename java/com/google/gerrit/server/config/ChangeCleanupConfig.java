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
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.server.change.MergeabilityComputationBehavior;
import com.google.gerrit.server.config.ScheduleConfig.Schedule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;

@Singleton
public class ChangeCleanupConfig {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static String SECTION = "changeCleanup";
  private static String KEY_ABANDON_AFTER = "abandonAfter";
  private static String KEY_ABANDON_IF_MERGEABLE = "abandonIfMergeable";
  private static String KEY_ABANDON_MESSAGE = "abandonMessage";
  private static String KEY_CLEANUP_ACCOUNT_PATCH_REVIEW = "cleanupAccountPatchReview";
  private static String DEFAULT_ABANDON_MESSAGE =
      "Auto-Abandoned due to inactivity, see "
          + "${URL}\n"
          + "\n"
          + "If this change is still wanted it should be restored.";

  private final DynamicItem<UrlFormatter> urlFormatter;
  private final Optional<Schedule> schedule;
  private final long abandonAfter;
  private final boolean abandonIfMergeable;
  private final boolean cleanupAccountPatchReview;
  private final String abandonMessage;

  @Inject
  ChangeCleanupConfig(@GerritServerConfig Config cfg, DynamicItem<UrlFormatter> urlFormatter) {
    this.urlFormatter = urlFormatter;
    schedule = ScheduleConfig.createSchedule(cfg, SECTION);
    abandonAfter = readAbandonAfter(cfg);
    boolean indexMergeable = MergeabilityComputationBehavior.fromConfig(cfg).includeInIndex();
    if (!indexMergeable) {
      if (!readAbandonIfMergeable(cfg)) {
        logger.atWarning().log(
            "index.change.indexMergeable is disabled; %s.%s=false will be ineffective",
            SECTION, KEY_ABANDON_IF_MERGEABLE);
      }
      abandonIfMergeable = true;
    } else {
      abandonIfMergeable = readAbandonIfMergeable(cfg);
    }
    cleanupAccountPatchReview =
        cfg.getBoolean(SECTION, null, KEY_CLEANUP_ACCOUNT_PATCH_REVIEW, false);
    abandonMessage = readAbandonMessage(cfg);
  }

  private boolean readAbandonIfMergeable(Config cfg) {
    return cfg.getBoolean(SECTION, null, KEY_ABANDON_IF_MERGEABLE, true);
  }

  private long readAbandonAfter(Config cfg) {
    long abandonAfter =
        ConfigUtil.getTimeUnit(cfg, SECTION, null, KEY_ABANDON_AFTER, 0, TimeUnit.MILLISECONDS);
    return abandonAfter >= 0 ? abandonAfter : 0;
  }

  private String readAbandonMessage(Config cfg) {
    String abandonMessage = cfg.getString(SECTION, null, KEY_ABANDON_MESSAGE);
    return Strings.isNullOrEmpty(abandonMessage) ? DEFAULT_ABANDON_MESSAGE : abandonMessage;
  }

  public Optional<Schedule> getSchedule() {
    return schedule;
  }

  public long getAbandonAfter() {
    return abandonAfter;
  }

  public boolean getAbandonIfMergeable() {
    return abandonIfMergeable;
  }

  public boolean getCleanupAccountPatchReview() {
    return cleanupAccountPatchReview;
  }

  public String getAbandonMessage() {
    String docUrl =
        urlFormatter.get().getDocUrl("user-change-cleanup.html", "auto-abandon").orElse("");
    return docUrl.isEmpty() ? abandonMessage : abandonMessage.replace("${URL}", docUrl);
  }
}
