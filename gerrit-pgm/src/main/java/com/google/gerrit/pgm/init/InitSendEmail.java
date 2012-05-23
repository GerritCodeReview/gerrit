// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.pgm.init;

import static com.google.gerrit.pgm.init.InitUtil.isLocal;
import static com.google.gerrit.pgm.init.InitUtil.username;

import com.google.gerrit.pgm.util.ConsoleUI;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.mail.SmtpEmailSender.Encryption;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/** Initialize the {@code sendemail} configuration section. */
@Singleton
class InitSendEmail implements InitStep {
  private final ConsoleUI ui;
  private final Section sendemail;
  private final SitePaths site;

  @Inject
  InitSendEmail(final ConsoleUI ui, final SitePaths site,
      final Section.Factory sections) {
    this.ui = ui;
    this.sendemail = sections.get("sendemail");
    this.site = site;
  }

  public void run() {
    ui.header("Email Delivery");

    final String hostname =
        sendemail.string("SMTP server hostname", "smtpServer", "localhost");

    sendemail.string("SMTP server port", "smtpServerPort", "(default)", true);

    final Encryption enc =
        sendemail.select("SMTP encryption", "smtpEncryption", Encryption.NONE,
            true);

    String username = null;
    if (site.gerrit_config.exists()) {
      username = sendemail.get("smtpUser");
    } else if ((enc != null && enc != Encryption.NONE) || !isLocal(hostname)) {
      username = username();
    }
    sendemail.string("SMTP username", "smtpUser", username);
    sendemail.password("smtpUser", "smtpPass");
  }
}
