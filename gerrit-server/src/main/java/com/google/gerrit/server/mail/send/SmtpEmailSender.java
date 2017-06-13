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

package com.google.gerrit.server.mail.send;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.io.BaseEncoding;
import com.google.common.primitives.Ints;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.Version;
import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.mail.Address;
import com.google.gerrit.server.mail.Encryption;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
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
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.apache.commons.net.smtp.AuthSMTPClient;
import org.apache.commons.net.smtp.SMTPClient;
import org.apache.commons.net.smtp.SMTPReply;
import org.eclipse.jgit.lib.Config;

/** Sends email via a nearby SMTP server. */
@Singleton
public class SmtpEmailSender implements EmailSender {
  /** The socket's connect timeout (0 = infinite timeout) */
  private static final int DEFAULT_CONNECT_TIMEOUT = 0;

  public static class Module extends AbstractModule {
    @Override
    protected void configure() {
      bind(EmailSender.class).to(SmtpEmailSender.class);
    }
  }

  private final boolean enabled;
  private final int connectTimeout;

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
    connectTimeout =
        Ints.checkedCast(
            ConfigUtil.getTimeUnit(
                cfg,
                "sendemail",
                null,
                "connectTimeout",
                DEFAULT_CONNECT_TIMEOUT,
                TimeUnit.MILLISECONDS));

    smtpHost = cfg.getString("sendemail", null, "smtpserver");
    if (smtpHost == null) {
      smtpHost = "127.0.0.1";
    }

    smtpEncryption = cfg.getEnum("sendemail", null, "smtpencryption", Encryption.NONE);
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

    Set<String> rcpt = new HashSet<>();
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
  public void send(
      final Address from,
      Collection<Address> rcpt,
      final Map<String, EmailHeader> callerHeaders,
      String body)
      throws EmailException {
    send(from, rcpt, callerHeaders, body, null);
  }

  @Override
  public void send(
      final Address from,
      Collection<Address> rcpt,
      final Map<String, EmailHeader> callerHeaders,
      String textBody,
      @Nullable String htmlBody)
      throws EmailException {
    if (!isEnabled()) {
      throw new EmailException("Sending email is disabled");
    }

    final Map<String, EmailHeader> hdrs = new LinkedHashMap<>(callerHeaders);
    setMissingHeader(hdrs, "MIME-Version", "1.0");
    setMissingHeader(hdrs, "Content-Transfer-Encoding", "8bit");
    setMissingHeader(hdrs, "Content-Disposition", "inline");
    setMissingHeader(hdrs, "User-Agent", "Gerrit/" + Version.getVersion());
    if (importance != null) {
      setMissingHeader(hdrs, "Importance", importance);
    }
    if (expiryDays > 0) {
      Date expiry = new Date(TimeUtil.nowMs() + expiryDays * 24 * 60 * 60 * 1000L);
      setMissingHeader(
          hdrs, "Expiry-Date", new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z").format(expiry));
    }

    String encodedBody;
    if (htmlBody == null) {
      setMissingHeader(hdrs, "Content-Type", "text/plain; charset=UTF-8");
      encodedBody = textBody;
    } else {
      String boundary = generateMultipartBoundary(textBody, htmlBody);
      setMissingHeader(
          hdrs,
          "Content-Type",
          "multipart/alternative; boundary=\"" + boundary + "\"; charset=UTF-8");
      encodedBody = buildMultipartBody(boundary, textBody, htmlBody);
    }

    StringBuffer rejected = new StringBuffer();
    try {
      final SMTPClient client = open();
      try {
        if (!client.setSender(from.getEmail())) {
          throw new EmailException(
              "Server " + smtpHost + " rejected from address " + from.getEmail());
        }

        /* Do not prevent the email from being sent to "good" users simply
         * because some users get rejected.  If not, a single rejected
         * project watcher could prevent email for most actions on a project
         * from being sent to any user!  Instead, queue up the errors, and
         * throw an exception after sending the email to get the rejected
         * error(s) logged.
         */
        for (Address addr : rcpt) {
          if (!client.addRecipient(addr.getEmail())) {
            String error = client.getReplyString();
            rejected
                .append("Server ")
                .append(smtpHost)
                .append(" rejected recipient ")
                .append(addr)
                .append(": ")
                .append(error);
          }
        }

        Writer messageDataWriter = client.sendMessageData();
        if (messageDataWriter == null) {
          /* Include rejected recipient error messages here to not lose that
           * information. That piece of the puzzle is vital if zero recipients
           * are accepted and the server consequently rejects the DATA command.
           */
          throw new EmailException(
              rejected
                  + "Server "
                  + smtpHost
                  + " rejected DATA command: "
                  + client.getReplyString());
        }
        try (Writer w = new BufferedWriter(messageDataWriter)) {
          for (Map.Entry<String, EmailHeader> h : hdrs.entrySet()) {
            if (!h.getValue().isEmpty()) {
              w.write(h.getKey());
              w.write(": ");
              h.getValue().write(w);
              w.write("\r\n");
            }
          }

          w.write("\r\n");
          w.write(encodedBody);
          w.flush();
        }

        if (!client.completePendingCommand()) {
          throw new EmailException(
              "Server " + smtpHost + " rejected message body: " + client.getReplyString());
        }

        client.logout();
        if (rejected.length() > 0) {
          throw new EmailException(rejected.toString());
        }
      } finally {
        client.disconnect();
      }
    } catch (IOException e) {
      throw new EmailException("Cannot send outgoing email", e);
    }
  }

  public static String generateMultipartBoundary(String textBody, String htmlBody)
      throws EmailException {
    byte[] bytes = new byte[8];
    ThreadLocalRandom rng = ThreadLocalRandom.current();

    // The probability of the boundary being valid is approximately
    // (2^64 - len(message)) / 2^64.
    //
    // The message is much shorter than 2^64 bytes, so if two tries don't
    // suffice, something is seriously wrong.
    for (int i = 0; i < 2; i++) {
      rng.nextBytes(bytes);
      String boundary = BaseEncoding.base64().encode(bytes);
      String encBoundary = "--" + boundary;
      if (textBody.contains(encBoundary) || htmlBody.contains(encBoundary)) {
        continue;
      }
      return boundary;
    }
    throw new EmailException("Gave up generating unique MIME boundary");
  }

  protected String buildMultipartBody(String boundary, String textPart, String htmlPart) {
    return
    // Output the text part:
    "--"
        + boundary
        + "\r\n"
        + "Content-Type: text/plain; charset=UTF-8\r\n"
        + "Content-Transfer-Encoding: 8bit\r\n"
        + "\r\n"
        + textPart
        + "\r\n"

        // Output the HTML part:
        + "--"
        + boundary
        + "\r\n"
        + "Content-Type: text/html; charset=UTF-8\r\n"
        + "Content-Transfer-Encoding: 8bit\r\n"
        + "\r\n"
        + htmlPart
        + "\r\n"

        // Output the closing boundary.
        + "--"
        + boundary
        + "--\r\n";
  }

  private void setMissingHeader(final Map<String, EmailHeader> hdrs, String name, String value) {
    if (!hdrs.containsKey(name) || hdrs.get(name).isEmpty()) {
      hdrs.put(name, new EmailHeader.String(value));
    }
  }

  private SMTPClient open() throws EmailException {
    final AuthSMTPClient client = new AuthSMTPClient(UTF_8.name());

    if (smtpEncryption == Encryption.SSL) {
      client.enableSSL(sslVerify);
    }

    client.setConnectTimeout(connectTimeout);
    try {
      client.connect(smtpHost, smtpPort);
      int replyCode = client.getReplyCode();
      String replyString = client.getReplyString();
      if (!SMTPReply.isPositiveCompletion(replyCode)) {
        throw new EmailException(
            String.format("SMTP server rejected connection: %d: %s", replyCode, replyString));
      }
      if (!client.login()) {
        throw new EmailException("SMTP server rejected HELO/EHLO greeting: " + replyString);
      }

      if (smtpEncryption == Encryption.TLS) {
        if (!client.startTLS(smtpHost, smtpPort, sslVerify)) {
          throw new EmailException("SMTP server does not support TLS");
        }
        if (!client.login()) {
          throw new EmailException("SMTP server rejected login: " + replyString);
        }
      }

      if (smtpUser != null && !client.auth(smtpUser, smtpPass)) {
        throw new EmailException("SMTP server rejected auth: " + replyString);
      }
      return client;
    } catch (IOException | EmailException e) {
      if (client.isConnected()) {
        try {
          client.disconnect();
        } catch (IOException e2) {
          // Ignored
        }
      }
      if (e instanceof EmailException) {
        throw (EmailException) e;
      }
      throw new EmailException(e.getMessage(), e);
    }
  }
}
