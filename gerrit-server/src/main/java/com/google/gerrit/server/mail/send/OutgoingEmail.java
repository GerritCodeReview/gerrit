// Copyright (C) 2016 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.gerrit.extensions.client.GeneralPreferencesInfo.EmailStrategy.CC_ON_OWN_COMMENTS;
import static com.google.gerrit.extensions.client.GeneralPreferencesInfo.EmailStrategy.DISABLED;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.EmailFormat;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.UserIdentity;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.mail.Address;
import com.google.gerrit.server.mail.send.EmailHeader.AddressList;
import com.google.gerrit.server.validators.OutgoingEmailValidationListener;
import com.google.gerrit.server.validators.ValidationException;
import com.google.gwtorm.server.OrmException;
import com.google.template.soy.data.SanitizedContent;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import org.apache.commons.lang.StringUtils;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.context.InternalContextAdapterImpl;
import org.apache.velocity.runtime.RuntimeInstance;
import org.apache.velocity.runtime.parser.node.SimpleNode;
import org.eclipse.jgit.util.SystemReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Sends an email to one or more interested parties. */
public abstract class OutgoingEmail {
  private static final Logger log = LoggerFactory.getLogger(OutgoingEmail.class);

  private static final String HDR_TO = "To";
  private static final String HDR_CC = "CC";

  protected String messageClass;
  private final HashSet<Account.Id> rcptTo = new HashSet<>();
  private final Map<String, EmailHeader> headers;
  private final Set<Address> smtpRcptTo = new HashSet<>();
  private Address smtpFromAddress;
  private StringBuilder textBody;
  private StringBuilder htmlBody;
  private ListMultimap<RecipientType, Account.Id> accountsToNotify = ImmutableListMultimap.of();
  protected VelocityContext velocityContext;
  protected Map<String, Object> soyContext;
  protected Map<String, Object> soyContextEmailData;
  protected List<String> footers;
  protected final EmailArguments args;
  protected Account.Id fromId;
  protected NotifyHandling notify = NotifyHandling.ALL;

  protected OutgoingEmail(EmailArguments ea, String mc) {
    args = ea;
    messageClass = mc;
    headers = new LinkedHashMap<>();
  }

  public void setFrom(Account.Id id) {
    fromId = id;
  }

  public void setNotify(NotifyHandling notify) {
    this.notify = checkNotNull(notify);
  }

  public void setAccountsToNotify(ListMultimap<RecipientType, Account.Id> accountsToNotify) {
    this.accountsToNotify = checkNotNull(accountsToNotify);
  }

  /**
   * Format and enqueue the message for delivery.
   *
   * @throws EmailException
   */
  public void send() throws EmailException {
    if (NotifyHandling.NONE.equals(notify) && accountsToNotify.isEmpty()) {
      return;
    }

    if (!args.emailSender.isEnabled()) {
      // Server has explicitly disabled email sending.
      //
      return;
    }

    init();
    if (useHtml()) {
      appendHtml(soyHtmlTemplate("HeaderHtml"));
    }
    format();
    appendText(textTemplate("Footer"));
    if (useHtml()) {
      appendHtml(soyHtmlTemplate("FooterHtml"));
    }

    Set<Address> smtpRcptToPlaintextOnly = new HashSet<>();
    if (shouldSendMessage()) {
      if (fromId != null) {
        final Account fromUser = args.accountCache.get(fromId).getAccount();
        GeneralPreferencesInfo senderPrefs = fromUser.getGeneralPreferencesInfo();

        if (senderPrefs != null && senderPrefs.getEmailStrategy() == CC_ON_OWN_COMMENTS) {
          // If we are impersonating a user, make sure they receive a CC of
          // this message so they can always review and audit what we sent
          // on their behalf to others.
          //
          add(RecipientType.CC, fromId);
        } else if (!accountsToNotify.containsValue(fromId) && rcptTo.remove(fromId)) {
          // If they don't want a copy, but we queued one up anyway,
          // drop them from the recipient lists.
          //
          removeUser(fromUser);
        }
      }
      // Check the preferences of all recipients. If any user has disabled
      // his email notifications then drop him from recipients' list.
      // In addition, check if users only want to receive plaintext email.
      for (Account.Id id : rcptTo) {
        Account thisUser = args.accountCache.get(id).getAccount();
        GeneralPreferencesInfo prefs = thisUser.getGeneralPreferencesInfo();
        if (prefs == null || prefs.getEmailStrategy() == DISABLED) {
          removeUser(thisUser);
        } else if (useHtml() && prefs.getEmailFormat() == EmailFormat.PLAINTEXT) {
          removeUser(thisUser);
          smtpRcptToPlaintextOnly.add(
              new Address(thisUser.getFullName(), thisUser.getPreferredEmail()));
        }
        if (smtpRcptTo.isEmpty() && smtpRcptToPlaintextOnly.isEmpty()) {
          return;
        }
      }

      // Set Reply-To only if it hasn't been set by a child class
      // Reply-To will already be populated for the message types where Gerrit supports
      // inbound email replies.
      if (!headers.containsKey("Reply-To")) {
        StringJoiner j = new StringJoiner(", ");
        if (fromId != null) {
          Address address = toAddress(fromId);
          if (address != null) {
            j.add(address.getEmail());
          }
        }
        smtpRcptTo.stream().forEach(a -> j.add(a.getEmail()));
        smtpRcptToPlaintextOnly.stream().forEach(a -> j.add(a.getEmail()));
        setHeader("Reply-To", j.toString());
      }

      String textPart = textBody.toString();
      OutgoingEmailValidationListener.Args va = new OutgoingEmailValidationListener.Args();
      va.messageClass = messageClass;
      va.smtpFromAddress = smtpFromAddress;
      va.smtpRcptTo = smtpRcptTo;
      va.headers = headers;
      va.body = textPart;

      if (useHtml()) {
        va.htmlBody = htmlBody.toString();
      } else {
        va.htmlBody = null;
      }

      for (OutgoingEmailValidationListener validator : args.outgoingEmailValidationListeners) {
        try {
          validator.validateOutgoingEmail(va);
        } catch (ValidationException e) {
          return;
        }
      }

      if (!smtpRcptTo.isEmpty()) {
        // Send multipart message
        args.emailSender.send(va.smtpFromAddress, va.smtpRcptTo, va.headers, va.body, va.htmlBody);
      }

      if (!smtpRcptToPlaintextOnly.isEmpty()) {
        // Send plaintext message
        Map<String, EmailHeader> shallowCopy = new HashMap<>();
        shallowCopy.putAll(headers);
        // Remove To and Cc
        shallowCopy.remove(HDR_TO);
        shallowCopy.remove(HDR_CC);
        for (Address a : smtpRcptToPlaintextOnly) {
          // Add new To
          EmailHeader.AddressList to = new EmailHeader.AddressList();
          to.add(a);
          shallowCopy.put(HDR_TO, to);
        }
        args.emailSender.send(va.smtpFromAddress, smtpRcptToPlaintextOnly, shallowCopy, va.body);
      }
    }
  }

  /** Format the message body by calling {@link #appendText(String)}. */
  protected abstract void format() throws EmailException;

  /**
   * Setup the message headers and envelope (TO, CC, BCC).
   *
   * @throws EmailException if an error occurred.
   */
  protected void init() throws EmailException {
    setupVelocityContext();
    setupSoyContext();

    smtpFromAddress = args.fromAddressGenerator.from(fromId);
    setHeader("Date", new Date());
    headers.put("From", new EmailHeader.AddressList(smtpFromAddress));
    headers.put(HDR_TO, new EmailHeader.AddressList());
    headers.put(HDR_CC, new EmailHeader.AddressList());
    setHeader("Message-ID", "");

    for (RecipientType recipientType : accountsToNotify.keySet()) {
      add(recipientType, accountsToNotify.get(recipientType));
    }

    setHeader("X-Gerrit-MessageType", messageClass);
    textBody = new StringBuilder();
    htmlBody = new StringBuilder();

    if (fromId != null && args.fromAddressGenerator.isGenericAddress(fromId)) {
      appendText(getFromLine());
    }
  }

  protected String getFromLine() {
    final Account account = args.accountCache.get(fromId).getAccount();
    final String name = account.getFullName();
    final String email = account.getPreferredEmail();
    StringBuilder f = new StringBuilder();

    if ((name != null && !name.isEmpty()) || (email != null && !email.isEmpty())) {
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
  protected void setVHeader(String name, String value) throws EmailException {
    setHeader(name, velocify(value));
  }

  /** Set a header in the outgoing message. */
  protected void setHeader(String name, String value) {
    headers.put(name, new EmailHeader.String(value));
  }

  /** Remove a header from the outgoing message. */
  protected void removeHeader(String name) {
    headers.remove(name);
  }

  protected void setHeader(String name, Date date) {
    headers.put(name, new EmailHeader.Date(date));
  }

  /** Append text to the outgoing email body. */
  protected void appendText(String text) {
    if (text != null) {
      textBody.append(text);
    }
  }

  /** Append html to the outgoing email body. */
  protected void appendHtml(String html) {
    if (html != null) {
      htmlBody.append(html);
    }
  }

  /** Lookup a human readable name for an account, usually the "full name". */
  protected String getNameFor(Account.Id accountId) {
    if (accountId == null) {
      return args.gerritPersonIdent.getName();
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

  /**
   * Gets the human readable name and email for an account; if neither are available, returns the
   * Anonymous Coward name.
   *
   * @param accountId user to fetch.
   * @return name/email of account, or Anonymous Coward if unset.
   */
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

    } else /* (name == null && email == null) */ {
      return args.anonymousCowardName + " #" + accountId;
    }
  }

  /**
   * Gets the human readable name and email for an account; if both are unavailable, returns the
   * username. If no username is set, this function returns null.
   *
   * @param accountId user to fetch.
   * @return name/email of account, username, or null if unset.
   */
  public String getUserNameEmailFor(Account.Id accountId) {
    AccountState who = args.accountCache.get(accountId);
    String name = who.getAccount().getFullName();
    String email = who.getAccount().getPreferredEmail();

    if (name != null && email != null) {
      return name + " <" + email + ">";
    } else if (email != null) {
      return email;
    } else if (name != null) {
      return name;
    }
    String username = who.getUserName();
    if (username != null) {
      return username;
    }
    return null;
  }

  protected boolean shouldSendMessage() {
    if (textBody.length() == 0) {
      // If we have no message body, don't send.
      return false;
    }

    if (smtpRcptTo.isEmpty()) {
      // If we have nobody to send this message to, then all of our
      // selection filters previously for this type of message were
      // unable to match a destination. Don't bother sending it.
      return false;
    }

    if ((accountsToNotify == null || accountsToNotify.isEmpty())
        && smtpRcptTo.size() == 1
        && rcptTo.size() == 1
        && rcptTo.contains(fromId)) {
      // If the only recipient is also the sender, don't bother.
      //
      return false;
    }

    return true;
  }

  /** Schedule this message for delivery to the listed accounts. */
  protected void add(RecipientType rt, Collection<Account.Id> list) {
    add(rt, list, false);
  }

  /** Schedule this message for delivery to the listed accounts. */
  protected void add(RecipientType rt, Collection<Account.Id> list, boolean override) {
    for (final Account.Id id : list) {
      add(rt, id, override);
    }
  }

  /** Schedule this message for delivery to the listed address. */
  protected void addByEmail(RecipientType rt, Collection<Address> list) {
    addByEmail(rt, list, false);
  }

  /** Schedule this message for delivery to the listed address. */
  protected void addByEmail(RecipientType rt, Collection<Address> list, boolean override) {
    for (final Address id : list) {
      add(rt, id, override);
    }
  }

  protected void add(RecipientType rt, UserIdentity who) {
    add(rt, who, false);
  }

  protected void add(RecipientType rt, UserIdentity who, boolean override) {
    if (who != null && who.getAccount() != null) {
      add(rt, who.getAccount(), override);
    }
  }

  /** Schedule delivery of this message to the given account. */
  protected void add(RecipientType rt, Account.Id to) {
    add(rt, to, false);
  }

  protected void add(RecipientType rt, Account.Id to, boolean override) {
    try {
      if (!rcptTo.contains(to) && isVisibleTo(to)) {
        rcptTo.add(to);
        add(rt, toAddress(to), override);
      }
    } catch (OrmException e) {
      log.error("Error reading database for account: " + to, e);
    }
  }

  /**
   * @param to account.
   * @throws OrmException
   * @return whether this email is visible to the given account.
   */
  protected boolean isVisibleTo(Account.Id to) throws OrmException {
    return true;
  }

  /** Schedule delivery of this message to the given account. */
  protected void add(RecipientType rt, Address addr) {
    add(rt, addr, false);
  }

  protected void add(RecipientType rt, Address addr, boolean override) {
    if (addr != null && addr.getEmail() != null && addr.getEmail().length() > 0) {
      if (!args.validator.isValid(addr.getEmail())) {
        log.warn("Not emailing " + addr.getEmail() + " (invalid email address)");
      } else if (!args.emailSender.canEmail(addr.getEmail())) {
        log.warn("Not emailing " + addr.getEmail() + " (prohibited by allowrcpt)");
      } else {
        if (!smtpRcptTo.add(addr)) {
          if (!override) {
            return;
          }
          ((EmailHeader.AddressList) headers.get(HDR_TO)).remove(addr.getEmail());
          ((EmailHeader.AddressList) headers.get(HDR_CC)).remove(addr.getEmail());
        }
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
    }
  }

  private Address toAddress(Account.Id id) {
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

  protected void setupSoyContext() {
    soyContext = new HashMap<>();
    footers = new ArrayList<>();

    soyContext.put("messageClass", messageClass);
    soyContext.put("footers", footers);

    soyContextEmailData = new HashMap<>();
    soyContextEmailData.put("settingsUrl", getSettingsUrl());
    soyContextEmailData.put("gerritHost", getGerritHost());
    soyContextEmailData.put("gerritUrl", getGerritUrl());
    soyContext.put("email", soyContextEmailData);
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
      Template template = runtime.getTemplate(name, UTF_8.name());
      StringWriter w = new StringWriter();
      template.merge(velocityContext, w);
      return w.toString();
    } catch (Exception e) {
      throw new EmailException("Cannot format velocity template " + name, e);
    }
  }

  private String soyTemplate(String name, SanitizedContent.ContentKind kind) {
    return args.soyTofu
        .newRenderer("com.google.gerrit.server.mail.template." + name)
        .setContentKind(kind)
        .setData(soyContext)
        .render();
  }

  protected String soyTextTemplate(String name) {
    return soyTemplate(name, SanitizedContent.ContentKind.TEXT);
  }

  protected String soyHtmlTemplate(String name) {
    return soyTemplate(name, SanitizedContent.ContentKind.HTML);
  }

  /**
   * Evaluate the named template according to the following priority: 1) Velocity file override,
   * OR... 2) Soy file override, OR... 3) Soy resource.
   */
  protected String textTemplate(String name) throws EmailException {
    String velocityName = name + ".vm";
    Path filePath = args.site.mail_dir.resolve(velocityName);
    if (Files.isRegularFile(filePath)) {
      return velocifyFile(velocityName);
    }
    return soyTextTemplate(name);
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

  protected void removeUser(Account user) {
    String fromEmail = user.getPreferredEmail();
    for (Iterator<Address> j = smtpRcptTo.iterator(); j.hasNext(); ) {
      if (j.next().getEmail().equals(fromEmail)) {
        j.remove();
      }
    }
    for (Map.Entry<String, EmailHeader> entry : headers.entrySet()) {
      // Don't remove fromEmail from the "From" header though!
      if (entry.getValue() instanceof AddressList && !entry.getKey().equals("From")) {
        ((AddressList) entry.getValue()).remove(fromEmail);
      }
    }
  }

  private static String safeToString(Object obj) {
    return obj != null ? obj.toString() : "";
  }

  protected final boolean useHtml() {
    return args.settings.html && supportsHtml();
  }

  /** Override this method to enable HTML in a subclass. */
  protected boolean supportsHtml() {
    return false;
  }
}
