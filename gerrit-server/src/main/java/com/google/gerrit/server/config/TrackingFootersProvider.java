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

import com.google.gerrit.reviewdb.client.TrackingId;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Provides a list of all configured {@link TrackingFooter}s. */
@Singleton
public class TrackingFootersProvider implements Provider<TrackingFooters> {
  private static String TRACKING_ID_TAG = "trackingid";
  private static String FOOTER_TAG = "footer";
  private static String SYSTEM_TAG = "system";
  private static String REGEX_TAG = "match";
  private final List<TrackingFooter> trackingFooters = new ArrayList<>();
  private static final Logger log = LoggerFactory.getLogger(TrackingFootersProvider.class);

  @Inject
  TrackingFootersProvider(@GerritServerConfig final Config cfg) {
    for (String name : cfg.getSubsections(TRACKING_ID_TAG)) {
      boolean configValid = true;

      Set<String> footers =
          new HashSet<>(Arrays.asList(cfg.getStringList(TRACKING_ID_TAG, name, FOOTER_TAG)));
      footers.removeAll(Collections.singleton(null));

      if (footers.isEmpty()) {
        configValid = false;
        log.error(
            "Missing " + TRACKING_ID_TAG + "." + name + "." + FOOTER_TAG + " in gerrit.config");
      }

      String system = cfg.getString(TRACKING_ID_TAG, name, SYSTEM_TAG);
      if (system == null || system.isEmpty()) {
        configValid = false;
        log.error(
            "Missing " + TRACKING_ID_TAG + "." + name + "." + SYSTEM_TAG + " in gerrit.config");
      } else if (system.length() > TrackingId.TRACKING_SYSTEM_MAX_CHAR) {
        configValid = false;
        log.error(
            "String to long \""
                + system
                + "\" in gerrit.config "
                + TRACKING_ID_TAG
                + "."
                + name
                + "."
                + SYSTEM_TAG
                + " (max "
                + TrackingId.TRACKING_SYSTEM_MAX_CHAR
                + " char)");
      }

      String match = cfg.getString(TRACKING_ID_TAG, name, REGEX_TAG);
      if (match == null || match.isEmpty()) {
        configValid = false;
        log.error(
            "Missing " + TRACKING_ID_TAG + "." + name + "." + REGEX_TAG + " in gerrit.config");
      }

      if (configValid) {
        try {
          for (String footer : footers) {
            trackingFooters.add(new TrackingFooter(footer, match, system));
          }
        } catch (PatternSyntaxException e) {
          log.error(
              "Invalid pattern \""
                  + match
                  + "\" in gerrit.config "
                  + TRACKING_ID_TAG
                  + "."
                  + name
                  + "."
                  + REGEX_TAG
                  + ": "
                  + e.getMessage());
        }
      }
    }
  }

  @Override
  public TrackingFooters get() {
    return new TrackingFooters(trackingFooters);
  }
}
