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

import static com.google.gerrit.server.mail.MetadataName.toFooterWithDelimiter;

import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.mail.receive.MailMessage;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class BadMailFilter implements MailFilter {

  private static final Logger log = LoggerFactory.getLogger(BadMailFilter.class);
  private final BadFilterMode mode;

  @Inject
  BadMailFilter(@GerritServerConfig Config cfg) {
    this.mode = cfg.getEnum("receiveemail", "filter", "mode", BadFilterMode.OFF);
  }

  @Override
  public boolean shouldProcessMessage(MailMessage message) {
    // @TODO: Add checks to ignore bots & invalid emails  (eg: added as Cc and not To)
    for (String header : message.additionalHeaders()) {
      if (header.startsWith(toFooterWithDelimiter(MetadataName.PRECEDENCE))) {
        String prec =
            header.substring(toFooterWithDelimiter(MetadataName.PRECEDENCE).length()).trim();
        if (prec.equals("list") || prec.equals("junk") || prec.equals("bulk")) {
          log.error(
              String.format(
                  "Message %s has a Precedence header. Will ignore and delete message.",
                  message.id()));
          return false;
        }
      } else if (header.startsWith(toFooterWithDelimiter(MetadataName.AUTO_SUBMITTED))) {
        String autoSubmitted =
            header.substring(toFooterWithDelimiter(MetadataName.AUTO_SUBMITTED).length()).trim();
        if (!autoSubmitted.equals("no")) {
          log.error(
              String.format(
                  "Message %s has a Auto-Submitted: yes header. Will ignore and delete message.",
                  message.id()));
          return false;
        }
      }
    }

    if (message.cc().size() != 0) {
      log.error(
          String.format(
              "Message %s has a Cc recipients. Will ignore and delete message.", message.id()));
      return false;
    }

    if (message.to().size() != 1) {
      // 0 would mean no recipients, more than 1 may indicate the email was not for us
      log.error(
          String.format(
              "Message %s has multiple recipients. Will ignore and delete message.", message.id()));
      return false;
    }

    return true;
  }

  public enum BadFilterMode {
    OFF,
    WHITELIST,
    BLACKLIST
  }
}
