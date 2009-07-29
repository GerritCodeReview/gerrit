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

package com.google.gerrit.server.mail;

import com.google.gerrit.pgm.Version;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.apache.commons.net.smtp.AuthSMTPClient;
import org.apache.commons.net.smtp.SMTPClient;
import org.apache.commons.net.smtp.SMTPReply;
import org.spearce.jgit.lib.Config;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Sends email via a nearby SMTP server. */
@Singleton
public class SmtpEmailSender implements EmailSender {
  private final boolean enabled;

  private String smtpHost;
  private int smtpPort;
  private String smtpUser;
  private String smtpPass;
  private String[] allowrcpt;

  @Inject
  SmtpEmailSender(@GerritServerConfig final Config cfg) {
    enabled = cfg.getBoolean("sendemail", null, "enable", true);

    smtpHost = cfg.getString("sendemail", null, "smtpserver");
    if (smtpHost == null) {
      smtpHost = "127.0.0.1";
    }
    smtpPort = cfg.getInt("sendemail", null, "smtpserverport", 25);
    smtpUser = cfg.getString("sendemail", null, "smtpuser");
    smtpPass = cfg.getString("sendemail", null, "smtpuserpass");
    allowrcpt = cfg.getStringList("sendemail", null, "allowrcpt");
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public void send(final Address from, final Collection<Address> rcpt,
      final Map<String, EmailHeader> callerHeaders, final String body)
      throws EmailException {
    if (!isEnabled()) {
      throw new EmailException("Sending email is disabled");
    }

    final Map<String, EmailHeader> hdrs =
        new LinkedHashMap<String, EmailHeader>(callerHeaders);
    setMissingHeader(hdrs, "MIME-Version", "1.0");
    setMissingHeader(hdrs, "Content-Type", "text/plain; charset=UTF-8");
    setMissingHeader(hdrs, "Content-Transfer-Encoding", "8bit");
    setMissingHeader(hdrs, "Content-Disposition", "inline");
    setMissingHeader(hdrs, "User-Agent", "Gerrit/" + Version.getVersion());

    try {
      final SMTPClient client = open();
      try {
        if (!client.setSender(from.email)) {
          throw new EmailException("Server " + smtpHost
              + " rejected from address " + from.email);
        }

        for (Address addr : rcpt) {
          if (!client.addRecipient(addr.email)) {
            String error = client.getReplyString();
            throw new EmailException("Server " + smtpHost
                + " rejected recipient " + addr + ": " + error);
          }
        }

        Writer w = client.sendMessageData();
        if (w == null) {
          throw new EmailException("Server " + smtpHost + " rejected body");
        }
        w = new BufferedWriter(w);

        for (Map.Entry<String, EmailHeader> h : hdrs.entrySet()) {
          if (!h.getValue().isEmpty()) {
            w.write(h.getKey());
            w.write(": ");
            h.getValue().write(w);
            w.write("\r\n");
          }
        }

        w.write("\r\n");
        w.write(body);
        w.flush();
        w.close();

        if (!client.completePendingCommand()) {
          throw new EmailException("Server " + smtpHost + " rejected body");
        }

        client.logout();
      } finally {
        client.disconnect();
      }
    } catch (IOException e) {
      throw new EmailException("Cannot send outgoing email", e);
    }
  }

  private void setMissingHeader(final Map<String, EmailHeader> hdrs,
      final String name, final String value) {
    if (!hdrs.containsKey(name) || hdrs.get(name).isEmpty()) {
      hdrs.put(name, new EmailHeader.String(value));
    }
  }

  private SMTPClient open() throws EmailException {
    final AuthSMTPClient client = new AuthSMTPClient("UTF-8");
    client.setAllowRcpt(allowrcpt);
    try {
      client.connect(smtpHost, smtpPort);
      if (!SMTPReply.isPositiveCompletion(client.getReplyCode())) {
        throw new EmailException("SMTP server rejected connection");
      }
      if (!client.login()) {
        String e = client.getReplyString();
        throw new EmailException("SMTP server rejected login: " + e);
      }
      if (smtpUser != null && !client.auth(smtpUser, smtpPass)) {
        String e = client.getReplyString();
        throw new EmailException("SMTP server rejected auth: " + e);
      }
    } catch (IOException e) {
      if (client.isConnected()) {
        try {
          client.disconnect();
        } catch (IOException e2) {
        }
      }
      throw new EmailException(e.getMessage(), e);
    } catch (EmailException e) {
      if (client.isConnected()) {
        try {
          client.disconnect();
        } catch (IOException e2) {
        }
      }
      throw e;
    }
    return client;
  }
}
