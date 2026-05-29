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

package com.google.gerrit.server.mail;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.mail.MailHeader;
import com.google.gerrit.mail.MailMessage;
import com.google.inject.Singleton;

/** Filters out auto-reply messages according to RFC 3834. */
@Singleton
public class AutoReplyMailFilter implements MailFilter {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Override
  public boolean shouldProcessMessage(MailMessage message) {
    for (String header : message.additionalHeaders()) {
      if (header.startsWith(MailHeader.PRECEDENCE.fieldWithDelimiter())) {
        String prec = header.substring(MailHeader.PRECEDENCE.fieldWithDelimiter().length()).trim();

        if (prec.equals("list") || prec.equals("junk") || prec.equals("bulk")) {
          logger.atSevere().log(
              "Message %s has a Precedence header. Will ignore and delete message.", message.id());
          return false;
        }

      } else if (header.startsWith(MailHeader.AUTO_SUBMITTED.fieldWithDelimiter())) {
        String autoSubmitted =
            header.substring(MailHeader.AUTO_SUBMITTED.fieldWithDelimiter().length()).trim();

        if (!autoSubmitted.equals("no")) {
          logger.atSevere().log(
              "Message %s has an Auto-Submitted header. Will ignore and delete message.",
              message.id());
          return false;
        }
      }
    }

    return true;
  }
}
