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

import com.google.gerrit.config.GerritServerConfig;
import com.google.gerrit.server.mail.receive.MailMessage;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Arrays;
import java.util.regex.Pattern;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ListMailFilter implements MailFilter {
  public enum ListFilterMode {
    OFF,
    WHITELIST,
    BLACKLIST
  }

  private static final Logger log = LoggerFactory.getLogger(ListMailFilter.class);

  private final ListFilterMode mode;
  private final Pattern mailPattern;

  @Inject
  ListMailFilter(@GerritServerConfig Config cfg) {
    this.mode = cfg.getEnum("receiveemail", "filter", "mode", ListFilterMode.OFF);
    String[] addresses = cfg.getStringList("receiveemail", "filter", "patterns");
    String concat = Arrays.asList(addresses).stream().collect(joining("|"));
    this.mailPattern = Pattern.compile(concat);
  }

  @Override
  public boolean shouldProcessMessage(MailMessage message) {
    if (mode == ListFilterMode.OFF) {
      return true;
    }

    boolean match = mailPattern.matcher(message.from().email).find();
    if (mode == ListFilterMode.WHITELIST && !match || mode == ListFilterMode.BLACKLIST && match) {
      log.info("Mail message from " + message.from() + " rejected by list filter");
      return false;
    }
    return true;
  }
}
