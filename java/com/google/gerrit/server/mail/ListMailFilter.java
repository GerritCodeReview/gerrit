// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.mail;

import static java.util.stream.Collectors.joining;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.mail.MailMessage;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Arrays;
import java.util.regex.Pattern;
import org.eclipse.jgit.lib.Config;

@Singleton
public class ListMailFilter implements MailFilter {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public enum ListFilterMode {
    OFF,
    ALLOW,
    BLOCK
  }

  private final ListFilterMode mode;
  private final Pattern mailPattern;

  @Inject
  ListMailFilter(@GerritServerConfig Config cfg) {
    mode = getListFilterMode(cfg);
    String[] addresses = cfg.getStringList("receiveemail", "filter", "patterns");
    String concat = Arrays.asList(addresses).stream().collect(joining("|"));
    this.mailPattern = Pattern.compile(concat);
  }

  private static final String LEGACY_ALLOW = "WHITELIST";
  private static final String LEGACY_BLOCK = "BLACKLIST";

  /** Legacy names are supported, but should be removed in the future. */
  private ListFilterMode getListFilterMode(Config cfg) {
    ListFilterMode mode;
    String modeString = cfg.getString("receiveemail", "filter", "mode");
    if (modeString == null) {
      modeString = "";
    }
    mode =
        switch (modeString) {
          case LEGACY_ALLOW, "ALLOW" -> ListFilterMode.ALLOW;
          case LEGACY_BLOCK, "BLOCK" -> ListFilterMode.BLOCK;
          default -> ListFilterMode.OFF;
        };
    return mode;
  }

  @Override
  public boolean shouldProcessMessage(MailMessage message) {
    if (mode == ListFilterMode.OFF) {
      return true;
    }

    boolean match = mailPattern.matcher(message.from().email()).find();
    if ((mode == ListFilterMode.ALLOW && !match) || (mode == ListFilterMode.BLOCK && match)) {
      logger.atInfo().log("Mail message from %s rejected by list filter", message.from());
      return false;
    }
    return true;
  }
}
