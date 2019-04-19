// Copyright (C) 2010 The Android Open Source Project
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.PatternSyntaxException;
import org.eclipse.jgit.lib.Config;

/** Provides a list of all configured {@link TrackingFooter}s. */
@Singleton
public class TrackingFootersProvider implements Provider<TrackingFooters> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final int MAX_LENGTH = 10;

  private static String TRACKING_ID_TAG = "trackingid";
  private static String FOOTER_TAG = "footer";
  private static String SYSTEM_TAG = "system";
  private static String REGEX_TAG = "match";
  private final List<TrackingFooter> trackingFooters = new ArrayList<>();

  @Inject
  TrackingFootersProvider(@GerritServerConfig Config cfg) {
    for (String name : cfg.getSubsections(TRACKING_ID_TAG)) {
      boolean configValid = true;

      Set<String> footers =
          new HashSet<>(Arrays.asList(cfg.getStringList(TRACKING_ID_TAG, name, FOOTER_TAG)));
      footers.removeAll(Collections.singleton(null));

      if (footers.isEmpty()) {
        configValid = false;
        logger.atSevere().log(
            "Missing %s.%s.%s in gerrit.config", TRACKING_ID_TAG, name, FOOTER_TAG);
      }

      String system = cfg.getString(TRACKING_ID_TAG, name, SYSTEM_TAG);
      if (system == null || system.isEmpty()) {
        configValid = false;
        logger.atSevere().log(
            "Missing %s.%s.%s in gerrit.config", TRACKING_ID_TAG, name, SYSTEM_TAG);
      } else if (system.length() > MAX_LENGTH) {
        configValid = false;
        logger.atSevere().log(
            "String too long \"%s\" in gerrit.config %s.%s.%s (max %d char)",
            system, TRACKING_ID_TAG, name, SYSTEM_TAG, MAX_LENGTH);
      }

      String match = cfg.getString(TRACKING_ID_TAG, name, REGEX_TAG);
      if (match == null || match.isEmpty()) {
        configValid = false;
        logger.atSevere().log(
            "Missing %s.%s.%s in gerrit.config", TRACKING_ID_TAG, name, REGEX_TAG);
      }

      if (configValid) {
        try {
          for (String footer : footers) {
            trackingFooters.add(new TrackingFooter(footer, match, system));
          }
        } catch (PatternSyntaxException e) {
          logger.atSevere().log(
              "Invalid pattern \"%s\" in gerrit.config %s.%s.%s: %s",
              match, TRACKING_ID_TAG, name, REGEX_TAG, e.getMessage());
        }
      }
    }
  }

  @Override
  public TrackingFooters get() {
    return new TrackingFooters(trackingFooters);
  }
}
