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

import com.google.gerrit.common.Version;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.apache.commons.net.smtp.AuthSMTPClient;
import org.apache.commons.net.smtp.SMTPClient;
import org.apache.commons.net.smtp.SMTPReply;
import org.eclipse.jgit.lib.Config;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Sends email via a nearby SMTP server. */
@Singleton
public class SmtpEmailSender implements EmailSender {
  public static class Module extends AbstractModule {
    @Override
    protected void configure() {
      bind(EmailSender.class).to(SmtpEmailSender.class);
    }
  }

  public static enum Encryption {
    NONE, SSL, TLS;
  }

  private final boolean enabled;

  private String smtpHost;
  private int smtpPort;
  private String smtpUser;
  private String smtpPass;
  private Encryption smtpEncryption;
  private boolean sslVerify;
  private Set<String> allowrcpt;
  private String importance;
  private int expiryDays;

  @Inject
  SmtpEmailSender(@GerritServerConfig final Config cfg) {
    enabled = cfg.getBoolean("sendemail", null, "enable", true);

    smtpHost = cfg.getString("sendemail", null, "smtpserver");
    if (smtpHost == null) {
      smtpHost = "127.0.0.1";
    }

    smtpEncryption =
        ConfigUtil.getEnum(cfg, "sendemail", null, "smtpencryption",
            Encryption.NONE);
    sslVerify = cfg.getBoolean("sendemail", null, "sslverify", true);

    final int defaultPort;
    switch (smtpEncryption) {
      case SSL:
        defaultPort = 465;
        break;

      case NONE:
      case TLS:
      default:
        defaultPort = 25;
        break;
    }
    smtpPort = cfg.getInt("sendemail", null, "smtpserverport", defaultPort);

    smtpUser = cfg.getString("sendemail", null, "smtpuser");
    smtpPass = cfg.getString("sendemail", null, "smtppass");

    Set<String> rcpt = new HashSet<String>();
    for (String addr : cfg.getStringList("sendemail", null, "allowrcpt")) {
      rcpt.add(addr);
    }
    allowrcpt = Collections.unmodifiableSet(rcpt);
    importance = cfg.getString("sendemail", null, "importance");
    expiryDays = cfg.getInt("sendemail", null, "expiryDays", 0);
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public boolean canEmail(String address) {
    if (!isEnabled()) {
      return false;
    }

    if (allowrcpt.isEmpty()) {
      return true;
    }

    if (allowrcpt.contains(address)) {
      return true;
    }

    String domain = address.substring(address.lastIndexOf('@') + 1);
    if (allowrcpt.contains(domain) || allowrcpt.contains("@" + domain)) {
      return true;
    }

    return false;
  }

  @Override
  public void send(final Address from, Collection<Address> rcpt,
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
    if(importance != null) {
      setMissingHeader(hdrs, "Importance", importance);
    }
    if(expiryDays > 0) {
      Date expiry = new Date(System.currentTimeMillis() + 
        expiryDays * 24 * 60 * 60 * 1000 );
      setMissingHeader(hdrs, "Expiry-Date", 
        new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z").format(expiry));
    }

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

    if (smtpEncryption == Encryption.SSL) {
      client.enableSSL(sslVerify);
    }

    try {
      client.connect(smtpHost, smtpPort);
      if (!SMTPReply.isPositiveCompletion(client.getReplyCode())) {
        throw new EmailException("SMTP server rejected connection");
      }
      if (!client.login()) {
        String e = client.getReplyString();
        throw new EmailException("SMTP server rejected login: " + e);
      }

      if (smtpEncryption == Encryption.TLS) {
        if (!client.startTLS(smtpHost, smtpPort, sslVerify)) {
          throw new EmailException("SMTP server does not support TLS");
        }
        if (!client.login()) {
          String e = client.getReplyString();
          throw new EmailException("SMTP server rejected login: " + e);
        }
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
