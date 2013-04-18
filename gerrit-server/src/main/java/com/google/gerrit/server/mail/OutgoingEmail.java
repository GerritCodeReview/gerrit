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

import com.google.common.collect.Sets;
import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.UserIdentity;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.mail.EmailHeader.AddressList;
import com.google.gwtorm.server.OrmException;

import org.apache.commons.lang.StringUtils;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.context.InternalContextAdapterImpl;
import org.apache.velocity.runtime.RuntimeInstance;
import org.apache.velocity.runtime.parser.node.SimpleNode;
import org.eclipse.jgit.util.SystemReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringReader;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Sends an email to one or more interested parties. */
public abstract class OutgoingEmail {
  private static final Logger log = LoggerFactory.getLogger(OutgoingEmail.class);

  private static final String HDR_TO = "To";
  private static final String HDR_CC = "CC";

  protected String messageClass;
  private final HashSet<Account.Id> rcptTo = new HashSet<Account.Id>();
  private final Map<String, EmailHeader> headers;
  private final Set<Address> smtpRcptTo = Sets.newHashSet();
  private Address smtpFromAddress;
  private StringBuilder body;
  protected VelocityContext velocityContext;

  protected final EmailArguments args;
  protected Account.Id fromId;


  protected OutgoingEmail(EmailArguments ea, String mc) {
    args = ea;
    messageClass = mc;
    headers = new LinkedHashMap<String, EmailHeader>();
  }

  public void setFrom(final Account.Id id) {
    fromId = id;
  }

  /**
   * Format and enqueue the message for delivery.
   *
   * @throws EmailException
   */
  public void send() throws EmailException {
    if (!args.emailSender.isEnabled()) {
      // Server has explicitly disabled email sending.
      //
      return;
    }

    init();
    format();
    if (shouldSendMessage()) {
      if (fromId != null) {
        final Account fromUser = args.accountCache.get(fromId).getAccount();

        if (fromUser.getGeneralPreferences().isCopySelfOnEmails()) {
          // If we are impersonating a user, make sure they receive a CC of
          // this message so they can always review and audit what we sent
          // on their behalf to others.
          //
          add(RecipientType.CC, fromId);

        } else if (rcptTo.remove(fromId)) {
          // If they don't want a copy, but we queued one up anyway,
          // drop them from the recipient lists.
          //
          final String fromEmail = fromUser.getPreferredEmail();
          for (Iterator<Address> i = smtpRcptTo.iterator(); i.hasNext();) {
            if (i.next().email.equals(fromEmail)) {
              i.remove();
            }
          }
          for (EmailHeader hdr : headers.values()) {
            if (hdr instanceof AddressList) {
              ((AddressList) hdr).remove(fromEmail);
            }
          }

          if (smtpRcptTo.isEmpty()) {
            return;
          }
        }
      }

      args.emailSender.send(smtpFromAddress, smtpRcptTo, headers, body.toString());
    }
  }

  /** Format the message body by calling {@link #appendText(String)}. */
  protected abstract void format() throws EmailException;

  /** Setup the message headers and envelope (TO, CC, BCC). */
  protected void init() throws EmailException {
    setupVelocityContext();

    smtpFromAddress = args.fromAddressGenerator.from(fromId);
    setHeader("Date", new Date());
    headers.put("From", new EmailHeader.AddressList(smtpFromAddress));
    headers.put(HDR_TO, new EmailHeader.AddressList());
    headers.put(HDR_CC, new EmailHeader.AddressList());
    setHeader("Message-ID", "");

    if (fromId != null) {
      // If we have a user that this message is supposedly caused by
      // but the From header on the email does not match the user as
      // it is a generic header for this Gerrit server, include the
      // Reply-To header with the current user's email address.
      //
      final Address a = toAddress(fromId);
      if (a != null && !smtpFromAddress.email.equals(a.email)) {
        setHeader("Reply-To", a.email);
      }
    }

    setHeader("X-Gerrit-MessageType", messageClass);
    body = new StringBuilder();

    if (fromId != null && args.fromAddressGenerator.isGenericAddress(fromId)) {
      appendText(getFromLine());
    }
  }

  protected String getFromLine() {
    final Account account = args.accountCache.get(fromId).getAccount();
    final String name = account.getFullName();
    final String email = account.getPreferredEmail();
    StringBuilder f = new StringBuilder();

    if ((name != null && !name.isEmpty())
        || (email != null && !email.isEmpty())) {
      f.append("From");
      if (name != null && !name.isEmpty()) {
        f.append(" ").append(name);
      }
      if (email != null && !email.isEmpty()) {
        f.append(" <").append(email).append(">");
      }
      f.append(":\n\n");
    }
    return f.toString();
  }

  public String getGerritHost() {
    if (getGerritUrl() != null) {
      try {
        return new URL(getGerritUrl()).getHost();
      } catch (MalformedURLException e) {
        // Try something else.
      }
    }

    // Fall back onto whatever the local operating system thinks
    // this server is called. We hopefully didn't get here as a
    // good admin would have configured the canonical url.
    //
    return SystemReader.getInstance().getHostname();
  }

  public String getSettingsUrl() {
    if (getGerritUrl() != null) {
      final StringBuilder r = new StringBuilder();
      r.append(getGerritUrl());
      r.append("settings");
      return r.toString();
    }
    return null;
  }

  public String getGerritUrl() {
    return args.urlProvider.get();
  }

  /** Set a header in the outgoing message using a template. */
  protected void setVHeader(final String name, final String value) throws
      EmailException {
    setHeader(name, velocify(value));
  }

  /** Set a header in the outgoing message. */
  protected void setHeader(final String name, final String value) {
    headers.put(name, new EmailHeader.String(value));
  }

  protected void setHeader(final String name, final Date date) {
    headers.put(name, new EmailHeader.Date(date));
  }

  /** Append text to the outgoing email body. */
  protected void appendText(final String text) {
    if (text != null) {
      body.append(text);
    }
  }

  /** Lookup a human readable name for an account, usually the "full name". */
  protected String getNameFor(final Account.Id accountId) {
    if (accountId == null) {
      return args.anonymousCowardName;
    }

    final Account userAccount = args.accountCache.get(accountId).getAccount();
    String name = userAccount.getFullName();
    if (name == null) {
      name = userAccount.getPreferredEmail();
    }
    if (name == null) {
      name = args.anonymousCowardName + " #" + accountId;
    }
    return name;
  }

  public String getNameEmailFor(Account.Id accountId) {
    AccountState who = args.accountCache.get(accountId);
    String name = who.getAccount().getFullName();
    String email = who.getAccount().getPreferredEmail();

    if (name != null && email != null) {
      return name + " <" + email + ">";

    } else if (name != null) {
      return name;
    } else if (email != null) {
      return email;

    } else /* (name == null && email == null) */{
      return args.anonymousCowardName + " #" + accountId;
    }
  }

  protected boolean shouldSendMessage() {
    if (body.length() == 0) {
      // If we have no message body, don't send.
      log.warn("Skipping delivery of email with no body");
      return false;
    }

    if (smtpRcptTo.isEmpty()) {
      // If we have nobody to send this message to, then all of our
      // selection filters previously for this type of message were
      // unable to match a destination. Don't bother sending it.
      log.info("Skipping delivery of email with no recipients");
      return false;
    }

    if (smtpRcptTo.size() == 1 && rcptTo.size() == 1 && rcptTo.contains(fromId)) {
      // If the only recipient is also the sender, don't bother.
      //
      return false;
    }

    return true;
  }

  /** Schedule this message for delivery to the listed accounts. */
  protected void add(final RecipientType rt, final Collection<Account.Id> list) {
    for (final Account.Id id : list) {
      add(rt, id);
    }
  }

  protected void add(final RecipientType rt, final UserIdentity who) {
    if (who != null && who.getAccount() != null) {
      add(rt, who.getAccount());
    }
  }

  /** Schedule delivery of this message to the given account. */
  protected void add(final RecipientType rt, final Account.Id to) {
    try {
      if (!rcptTo.contains(to) && isVisibleTo(to)) {
        rcptTo.add(to);
        add(rt, toAddress(to));
      }
    } catch (OrmException e) {
      log.error("Error reading database for account: " + to, e);
    }
  }

  protected boolean isVisibleTo(final Account.Id to) throws OrmException {
    return true;
  }

  /** Schedule delivery of this message to the given account. */
  protected void add(final RecipientType rt, final Address addr) {
    if (addr != null && addr.email != null && addr.email.length() > 0) {
      if (args.emailSender.canEmail(addr.email)) {
        if (smtpRcptTo.add(addr)) {
          switch (rt) {
            case TO:
              ((EmailHeader.AddressList) headers.get(HDR_TO)).add(addr);
              break;
            case CC:
              ((EmailHeader.AddressList) headers.get(HDR_CC)).add(addr);
              break;
            case BCC:
              break;
          }
        }
      } else {
        log.warn("Not emailing " + addr.email + " (prohibited by allowrcpt)");
      }
    }
  }

  private Address toAddress(final Account.Id id) {
    final Account a = args.accountCache.get(id).getAccount();
    final String e = a.getPreferredEmail();
    if (!a.isActive() || e == null) {
      return null;
    }
    return new Address(a.getFullName(), e);
  }

  protected void setupVelocityContext() {
    velocityContext = new VelocityContext();

    velocityContext.put("email", this);
    velocityContext.put("messageClass", messageClass);
    velocityContext.put("StringUtils", StringUtils.class);
  }

  protected String velocify(String template) throws EmailException {
    try {
      RuntimeInstance runtime = args.velocityRuntime;
      String templateName = "OutgoingEmail";
      SimpleNode tree = runtime.parse(new StringReader(template), templateName);
      InternalContextAdapterImpl ica = new InternalContextAdapterImpl(velocityContext);
      ica.pushCurrentTemplateName(templateName);
      try {
        tree.init(ica, runtime);
        StringWriter w = new StringWriter();
        tree.render(ica, w);
        return w.toString();
      } finally {
        ica.popCurrentTemplateName();
      }
    } catch (Exception e) {
      throw new EmailException("Cannot format velocity template: " + template, e);
    }
  }

  protected String velocifyFile(String name) throws EmailException {
    try {
      RuntimeInstance runtime = args.velocityRuntime;
      if (runtime.getLoaderNameForResource(name) == null) {
        name = "com/google/gerrit/server/mail/" + name;
      }
      Template template = runtime.getTemplate(name, "UTF-8");
      StringWriter w = new StringWriter();
      template.merge(velocityContext, w);
      return w.toString();
    } catch (EmailException e) {
      throw e;
    } catch (Exception e) {
      throw new EmailException("Cannot format velocity template " + name, e);
    }
  }

  public String joinStrings(Iterable<Object> in, String joiner) {
    return joinStrings(in.iterator(), joiner);
  }

  public String joinStrings(Iterator<Object> in, String joiner) {
    if (!in.hasNext()) {
      return "";
    }

    Object first = in.next();
    if (!in.hasNext()) {
      return safeToString(first);
    }

    StringBuilder r = new StringBuilder();
    r.append(safeToString(first));
    while (in.hasNext()) {
      r.append(joiner).append(safeToString(in.next()));
    }
    return r.toString();
  }

  private static String safeToString(Object obj) {
    return obj != null ? obj.toString() : "";
  }
}
