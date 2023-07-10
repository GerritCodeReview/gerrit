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

import static com.google.gerrit.extensions.client.GeneralPreferencesInfo.EmailStrategy.CC_ON_OWN_COMMENTS;
import static com.google.gerrit.extensions.client.GeneralPreferencesInfo.EmailStrategy.DISABLED;
import static java.util.Objects.requireNonNull;

import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;
import com.google.common.base.Throwables;
import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.UsedAt;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Address;
import com.google.gerrit.entities.EmailHeader;
import com.google.gerrit.entities.EmailHeader.AddressList;
import com.google.gerrit.entities.EmailHeader.StringEmailHeader;
import com.google.gerrit.exceptions.EmailException;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.EmailFormat;
import com.google.gerrit.mail.MailHeader;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.update.RetryableAction.ActionType;
import com.google.gerrit.server.validators.OutgoingEmailValidationListener;
import com.google.gerrit.server.validators.ValidationException;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.jbcsrc.api.SoySauce;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import org.apache.james.mime4j.dom.field.FieldName;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.util.SystemReader;

/** Represents an email notification for some event that can be sent to interested parties. */
@AutoFactory
public final class OutgoingEmail {

  /** Provides content, recipients and any customizations of the email. */
  public interface EmailDecorator {
    /**
     * Stores the reference to the email for the subsequent calls.
     *
     * <p>Both init and populateEmailContent can be called multiply times in case of retries. Init
     * is therefore responsible for clearing up any changes which are not idempotent and
     * initializing data for use in populateEmailContent.
     *
     * <p>Can be used to adjust any of the behaviour of the {@link
     * OutgoingEmail#populateEmailContent}.
     */
    void init(OutgoingEmail email) throws EmailException;

    /**
     * Populate headers, recipients and body of the email.
     *
     * <p>Method operates on the email provided in the init method.
     *
     * <p>By default, all the contents and parameters of the email should be set in this method.
     */
    void populateEmailContent() throws EmailException;

    /** If returns false email is not sent to any recipients. */
    default boolean shouldSendMessage() {
      return true;
    }

    /**
     * Evaluates whether account can be added to the list of recipients.
     *
     * @param rcpt the recipient for which it should be checker whether it can be added to the list
     *     of recipients
     * @throws PermissionBackendException thrown if checking permissions fails
     */
    default boolean isRecipientAllowed(Account.Id rcpt) throws PermissionBackendException {
      return true;
    }

    /**
     * Evaluates whether email can be added to the list of recipients.
     *
     * @param rcpt the recipient for which it should be checker whether it can be added to the list
     *     of recipients
     * @throws PermissionBackendException thrown if checking permissions fails
     */
    default boolean isRecipientAllowed(Address rcpt) throws PermissionBackendException {
      return true;
    }
  }

  private static final String SOY_TEMPLATE_NAMESPACE = "com.google.gerrit.server.mail.template";
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private String messageClass;
  private final Set<Account.Id> rcptTo = new HashSet<>();
  private final Map<String, EmailHeader> headers = new LinkedHashMap<>();
  private final Set<Address> smtpRcptTo = new HashSet<>();
  private final Set<Address> smtpBccRcptTo = new HashSet<>();
  private Address smtpFromAddress;
  private StringBuilder textBody;
  private ArrayList<SanitizedContent> htmlBodySections;
  private MessageIdGenerator.MessageId messageId;
  private Map<String, Object> soyContext;
  private Map<String, Object> soyContextEmailData;
  private List<String> footers;
  private final EmailArguments args;
  private Account.Id fromId;
  private NotifyResolver.Result notify = NotifyResolver.Result.all();
  private final EmailDecorator templateProvider;
  private ArrayList<EmailResource> htmlResources;

  public OutgoingEmail(
      @Provided EmailArguments args, String messageClass, EmailDecorator templateProvider) {
    this.args = args;
    this.messageClass = messageClass;
    this.templateProvider = templateProvider;
  }

  /** Specify the account that triggered the notification. */
  public void setFrom(Account.Id id) {
    fromId = id;
  }

  /** Get the account that triggered the notification. */
  public Account.Id getFrom() {
    return fromId;
  }

  /** Set how widely the email notification is allowed to be sent. */
  public void setNotify(NotifyResolver.Result notify) {
    this.notify = requireNonNull(notify);
  }

  /** Returns the setting that controls how widely the email notification is allowed to be sent. */
  public NotifyResolver.Result getNotify() {
    return this.notify;
  }

  /** Set identifier for the email. Every email must have one. */
  public void setMessageId(MessageIdGenerator.MessageId messageId) {
    this.messageId = messageId;
  }

  private String constructTextEmail() {
    soyContext.put("body", textBody.toString());
    soyContext.put("footer", textTemplate("Footer"));
    return textTemplate("Email");
  }

  private String constructHtmlEmail() {
    soyContext.put("body_sections_html", htmlBodySections);
    soyContext.put("footer_html", soyHtmlTemplate("FooterHtml"));
    return soyHtmlTemplate("EmailHtml").toString();
  }

  /** Format and enqueue the message for delivery. */
  public void send() throws EmailException {
    try {
      args.retryHelper
          .action(
              ActionType.SEND_EMAIL,
              "sendEmail",
              () -> {
                sendImpl();
                return null;
              })
          .retryWithTrace(Exception.class::isInstance)
          .call();
    } catch (Exception e) {
      Throwables.throwIfUnchecked(e);
      Throwables.throwIfInstanceOf(e, EmailException.class);
      throw new EmailException("sending email failed", e);
    }
  }

  private void sendImpl() throws EmailException {
    if (!args.emailSender.isEnabled()) {
      // Server has explicitly disabled email sending.
      //
      logger.atFine().log(
          "Not sending '%s': Email sending is disabled by server config", messageClass);
      return;
    }

    init();
    if (!notify.shouldNotify()) {
      logger.atFine().log("Not sending '%s': Notify handling is NONE", messageClass);
      return;
    }
    populateEmailContent();
    if (messageId == null) {
      throw new IllegalStateException("All emails must have a messageId");
    }

    Set<Address> smtpRcptToPlaintextOnly = new HashSet<>();
    if (shouldSendMessage()) {
      if (fromId != null) {
        Optional<AccountState> fromUser = args.accountCache.get(fromId);
        if (fromUser.isPresent()) {
          GeneralPreferencesInfo senderPrefs = fromUser.get().generalPreferences();
          CurrentUser user = args.currentUserProvider.get();
          boolean isImpersonating = user.isIdentifiedUser() && user.isImpersonating();
          if (isImpersonating && user.getAccountId() != fromId) {
            // This should not be possible, if this is the case it means the RequestContext is not
            // set up correctly.
            throw new EmailException(
                String.format(
                    "User %s is sending email from %s, while acting on behalf of %s",
                    user.asIdentifiedUser().getRealUser().getAccountId(),
                    fromId,
                    user.getAccountId()));
          }
          if (senderPrefs != null && senderPrefs.getEmailStrategy() == CC_ON_OWN_COMMENTS) {
            // Include the sender in email if they enabled email notifications on their own
            // comments.
            //
            logger.atFine().log(
                "CC email sender %s because the email strategy of this user is %s",
                fromUser.get().account().id(), CC_ON_OWN_COMMENTS);
            addByAccountId(RecipientType.CC, fromId);
          } else if (isImpersonating) {
            // If we are impersonating a user, make sure they receive a CC of
            // this message regardless of email strategy, unless email notifications are explicitly
            // disabled for this user. This way they can always review and audit what we sent
            // on their behalf to others.
            logger.atFine().log(
                "CC email sender %s because the email is sent on behalf of and email notifications"
                    + " are enabled for this user.",
                fromUser.get().account().id());
            addByAccountId(RecipientType.CC, fromId);

          } else if (!notify.accounts().containsValue(fromId) && rcptTo.remove(fromId)) {
            // If they don't want a copy, but we queued one up anyway,
            // drop them from the recipient lists, but only if the user is not being impersonated.
            //
            logger.atFine().log(
                "Not CCing email sender %s because the email strategy of this user is not %s but"
                    + " %s",
                fromUser.get().account().id(),
                CC_ON_OWN_COMMENTS,
                senderPrefs != null ? senderPrefs.getEmailStrategy() : null);
            removeUser(fromUser.get().account());
          }
        }
      }
      // Check the preferences of all recipients. If any user has disabled
      // his email notifications then drop him from recipients' list.
      // In addition, check if users only want to receive plaintext email.
      for (Account.Id id : rcptTo) {
        Optional<AccountState> thisUser = args.accountCache.get(id);
        if (thisUser.isPresent()) {
          Account thisUserAccount = thisUser.get().account();
          GeneralPreferencesInfo prefs = thisUser.get().generalPreferences();
          if (prefs == null || prefs.getEmailStrategy() == DISABLED) {
            logger.atFine().log(
                "Not emailing account %s because user has set email strategy to %s", id, DISABLED);
            removeUser(thisUserAccount);
          } else if (useHtml() && prefs.getEmailFormat() == EmailFormat.PLAINTEXT) {
            logger.atFine().log(
                "Removing account %s from HTML email because user prefers plain text emails", id);
            removeUser(thisUserAccount);
            smtpRcptToPlaintextOnly.add(
                Address.create(thisUserAccount.fullName(), thisUserAccount.preferredEmail()));
          }
        }
        if (smtpRcptTo.isEmpty() && smtpRcptToPlaintextOnly.isEmpty()) {
          logger.atFine().log("Not sending '%s': No SMTP recipients", messageClass);
          return;
        }
      }

      // Set Reply-To only if it hasn't been set by a child class
      // Reply-To will already be populated for the message types where Gerrit supports
      // inbound email replies.
      if (!headers.containsKey(FieldName.REPLY_TO)) {
        StringJoiner j = new StringJoiner(", ");
        if (fromId != null) {
          Address address = toAddress(fromId);
          if (address != null) {
            j.add(address.email());
          }
        }
        // For users who prefer plaintext, this comes at the cost of not being
        // listed in the multipart To and Cc headers. We work around this by adding
        // all users to the Reply-To address in both the plaintext and multipart
        // email. We should exclude any BCC addresses from reply-to, because they should be
        // invisible to other recipients.
        Sets.difference(Sets.union(smtpRcptTo, smtpRcptToPlaintextOnly), smtpBccRcptTo).stream()
            .forEach(a -> j.add(a.email()));
        setHeader(FieldName.REPLY_TO, j.toString());
      }

      OutgoingEmailValidationListener.Args va = new OutgoingEmailValidationListener.Args();
      va.messageClass = messageClass;
      va.smtpFromAddress = smtpFromAddress;
      va.smtpRcptTo = smtpRcptTo;
      va.headers = headers;
      va.body = constructTextEmail();

      if (useHtml()) {
        va.htmlBody = constructHtmlEmail();
      } else {
        va.htmlBody = null;
      }

      Set<Address> intersection = Sets.intersection(va.smtpRcptTo, smtpRcptToPlaintextOnly);
      if (!intersection.isEmpty()) {
        logger.atSevere().log("Email '%s' will be sent twice to %s", messageClass, intersection);
      }
      if (!va.smtpRcptTo.isEmpty()) {
        // Send multipart message
        addMessageId(va, "-HTML");
        if (!validateEmail(va)) return;
        logger.atFine().log(
            "Sending multipart '%s' from %s to %s",
            messageClass, va.smtpFromAddress, va.smtpRcptTo);
        args.emailSender.send(
            va.smtpFromAddress, va.smtpRcptTo, va.headers, va.body, va.htmlBody, htmlResources);
      }
      if (!smtpRcptToPlaintextOnly.isEmpty()) {
        addMessageId(va, "-PLAIN");
        // Send plaintext message
        Map<String, EmailHeader> shallowCopy = new HashMap<>();
        shallowCopy.putAll(headers);
        // Remove To and Cc
        shallowCopy.remove(FieldName.TO);
        shallowCopy.remove(FieldName.CC);
        for (Address a : smtpRcptToPlaintextOnly) {
          // Add new To
          AddressList to = new AddressList();
          to.add(a);
          shallowCopy.put(FieldName.TO, to);
        }
        if (!validateEmail(va)) return;
        logger.atFine().log(
            "Sending plaintext '%s' from %s to %s",
            messageClass, va.smtpFromAddress, smtpRcptToPlaintextOnly);
        args.emailSender.send(va.smtpFromAddress, smtpRcptToPlaintextOnly, shallowCopy, va.body);
      }
    }
  }

  private boolean validateEmail(OutgoingEmailValidationListener.Args va) {
    for (OutgoingEmailValidationListener validator : args.outgoingEmailValidationListeners) {
      try {
        validator.validateOutgoingEmail(va);
      } catch (ValidationException e) {
        logger.atFine().log(
            "Not sending '%s': Rejected by outgoing email validator: %s",
            messageClass, e.getMessage());
        return false;
      }
    }
    return true;
  }

  // All message ids must start with < and end with >. Also, they must have @domain and no spaces.
  private void addMessageId(OutgoingEmailValidationListener.Args va, String suffix) {
    if (messageId != null) {
      String message = "<" + messageId.id() + suffix + "@" + getGerritHost() + ">";
      message = message.replaceAll("\\s", "");
      va.headers.put(FieldName.MESSAGE_ID, new StringEmailHeader(message));
    }
  }

  /**
   * Setup the message headers and envelope (TO, CC, BCC).
   *
   * @throws EmailException if an error occurred.
   */
  public void init() throws EmailException {
    soyContext = new HashMap<>();
    footers = new ArrayList<>();
    soyContextEmailData = new HashMap<>();
    htmlResources = new ArrayList<>();

    smtpFromAddress = args.fromAddressGenerator.get().from(fromId);
    setHeader(FieldName.DATE, Instant.now());
    headers.put(FieldName.FROM, new AddressList(smtpFromAddress));
    headers.put(FieldName.TO, new AddressList());
    headers.put(FieldName.CC, new AddressList());
    setHeader(MailHeader.AUTO_SUBMITTED.fieldName(), "auto-generated");

    setHeader(MailHeader.MESSAGE_TYPE.fieldName(), messageClass);
    addFooter(MailHeader.MESSAGE_TYPE.withDelimiter() + messageClass);
    textBody = new StringBuilder();
    htmlBodySections = new ArrayList<>();

    if (fromId != null && args.fromAddressGenerator.get().isGenericAddress(fromId)) {
      appendText(getFromLine());
    }

    templateProvider.init(this);
  }

  private String getFromLine() {
    StringBuilder f = new StringBuilder();
    Optional<Account> account = args.accountCache.get(fromId).map(AccountState::account);
    if (account.isPresent()) {
      String name = account.get().fullName();
      String email = account.get().preferredEmail();
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
    }
    return f.toString();
  }

  public String getGerritHost() {
    Optional<String> gerritUrl = args.urlFormatter.get().getWebUrl();
    if (gerritUrl.isPresent()) {
      try {
        return new URL(gerritUrl.get()).getHost();
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

  @Nullable
  public String getSettingsUrl() {
    return args.urlFormatter.get().getSettingsUrl().map(EmailArguments::addUspParam).orElse(null);
  }

  @Nullable
  public String getSettingsUrl(String section) {
    return args.urlFormatter
        .get()
        .getSettingsUrl(section)
        .map(EmailArguments::addUspParam)
        .orElse(null);
  }

  /** Set a header in the outgoing message. */
  public void setHeader(String name, String value) {
    headers.put(name, new StringEmailHeader(value));
  }

  /** Remove a header from the outgoing message. */
  public void removeHeader(String name) {
    headers.remove(name);
  }

  /** Set a date header in the outgoing message. */
  public void setHeader(String name, Instant date) {
    headers.put(name, new EmailHeader.Date(date));
  }

  /** Append text to the outgoing email body. */
  public void appendText(String text) {
    if (text != null) {
      textBody.append(text);
    }
  }

  /** Append html to the outgoing email body. */
  public void appendHtml(SanitizedContent html) {
    if (html != null) {
      htmlBodySections.add(html);
    }
  }

  /** Lookup a human readable name for an account, usually the "full name". */
  public String getNameFor(@Nullable Account.Id accountId) {
    if (accountId == null) {
      return args.gerritPersonIdent.get().getName();
    }

    Optional<Account> account = args.accountCache.get(accountId).map(AccountState::account);
    String name = null;
    if (account.isPresent()) {
      name = account.get().fullName();
      if (name == null) {
        name = account.get().preferredEmail();
      }
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
  public String getNameEmailFor(@Nullable Account.Id accountId) {
    if (accountId == null) {
      PersonIdent gerritIdent = args.gerritPersonIdent.get();
      return gerritIdent.getName() + " <" + gerritIdent.getEmailAddress() + ">";
    }

    Optional<Account> account = args.accountCache.get(accountId).map(AccountState::account);
    if (account.isPresent()) {
      String name = account.get().fullName();
      String email = account.get().preferredEmail();
      if (name != null && email != null) {
        return name + " <" + email + ">";
      } else if (name != null) {
        return name;
      } else if (email != null) {
        return email;
      }
    }
    return args.anonymousCowardName + " #" + accountId;
  }

  /**
   * Gets the human readable name and email for an account; if both are unavailable, returns the
   * username. If no username is set, this function returns null.
   *
   * @param accountId user to fetch.
   * @return name/email of account, username, or null if unset or the accountId is null.
   */
  @Nullable
  public String getUserNameEmailFor(@Nullable Account.Id accountId) {
    if (accountId == null) {
      return null;
    }

    Optional<AccountState> accountState = args.accountCache.get(accountId);
    if (!accountState.isPresent()) {
      return null;
    }

    Account account = accountState.get().account();
    String name = account.fullName();
    String email = account.preferredEmail();
    if (name != null && email != null) {
      return name + " <" + email + ">";
    } else if (email != null) {
      return email;
    } else if (name != null) {
      return name;
    }
    return accountState.get().userName().orElse(null);
  }

  private boolean shouldSendMessage() {
    if (textBody.length() == 0) {
      // If we have no message body, don't send.
      logger.atFine().log("Not sending '%s': No message body", messageClass);
      return false;
    }

    if (smtpRcptTo.isEmpty()) {
      // If we have nobody to send this message to, then all of our
      // selection filters previously for this type of message were
      // unable to match a destination. Don't bother sending it.
      logger.atFine().log("Not sending '%s': No recipients", messageClass);
      return false;
    }

    if (notify.accounts().isEmpty()
        && smtpRcptTo.size() == 1
        && rcptTo.size() == 1
        && rcptTo.contains(fromId)) {
      // If the only recipient is also the sender, don't bother.
      //
      logger.atFine().log("Not sending '%s': Sender is only recipient", messageClass);
      return false;
    }

    return templateProvider.shouldSendMessage();
  }

  /**
   * Adds a recipient that the email will be sent to.
   *
   * @param rt category of recipient (TO, CC, BCC)
   * @param addr Name and email of the recipient.
   */
  public final void addByEmail(RecipientType rt, Address addr) {
    addByEmail(rt, addr, false);
  }

  /**
   * Adds a recipient that the email will be sent to.
   *
   * @param rt category of recipient (TO, CC, BCC).
   * @param addr Name and email of the recipient.
   * @param override if the recipient was added previously and override is false no change is made
   *     regardless of {@code rt}.
   */
  public final void addByEmail(RecipientType rt, Address addr, boolean override) {
    try {
      if (isRecipientAllowed(addr)) {
        add(rt, addr, override);
      }
    } catch (PermissionBackendException e) {
      logger.atSevere().withCause(e).log("Error checking permissions for email address: %s", addr);
    }
  }

  /**
   * Returns whether this email is allowed to be sent to the given address
   *
   * @param addr email address of recipient.
   * @throws PermissionBackendException thrown if checking a permission fails due to an error in the
   *     permission backend
   */
  public boolean isRecipientAllowed(Address addr) throws PermissionBackendException {
    return templateProvider.isRecipientAllowed(addr);
  }

  /**
   * Adds a recipient that the email will be sent to.
   *
   * @param rt category of recipient (TO, CC, BCC)
   * @param to Gerrit Account of the recipient.
   */
  public void addByAccountId(RecipientType rt, Account.Id to) {
    addByAccountId(rt, to, false);
  }

  /**
   * Adds a recipient that the email will be sent to.
   *
   * @param rt category of recipient (TO, CC, BCC)
   * @param to Gerrit Account of the recipient.
   * @param override if the recipient was added previously and override is false no change is made
   *     regardless of {@code rt}.
   */
  public void addByAccountId(RecipientType rt, Account.Id to, boolean override) {
    try {
      if (rcptTo.contains(to) || !isRecipientAllowed(to)) {
        return;
      }
      Address addr = toAddress(to);
      if (addr == null) {
        logger.atFine().log("Not emailing account %s because user has no preferred email", to);
        return;
      }
      rcptTo.add(to);
      add(rt, addr, override);
    } catch (PermissionBackendException e) {
      logger.atSevere().withCause(e).log("Error checking permissions for account: %s", to);
    }
  }

  /**
   * Returns whether this email is allowed to be sent to the given account
   *
   * @param to account.
   * @throws PermissionBackendException thrown if checking a permission fails due to an error in the
   *     permission backend
   */
  public boolean isRecipientAllowed(Account.Id to) throws PermissionBackendException {
    return templateProvider.isRecipientAllowed(to);
  }

  private final void add(RecipientType rt, Address addr, boolean override) {
    if (addr != null && addr.email() != null && addr.email().length() > 0) {
      if (!args.validator.isValid(addr.email())) {
        logger.atWarning().log("Not emailing %s (invalid email address)", addr.email());
      } else if (args.emailSender.canEmail(addr.email())) {
        if (!smtpRcptTo.add(addr)) {
          if (!override) {
            return;
          }
          ((AddressList) headers.get(FieldName.TO)).remove(addr.email());
          ((AddressList) headers.get(FieldName.CC)).remove(addr.email());
          smtpBccRcptTo.remove(addr);
        }
        switch (rt) {
          case TO:
            ((AddressList) headers.get(FieldName.TO)).add(addr);
            break;
          case CC:
            ((AddressList) headers.get(FieldName.CC)).add(addr);
            break;
          case BCC:
            smtpBccRcptTo.add(addr);
            break;
        }
      }
    }
  }

  /** Returns preferred email address for the account. */
  @Nullable
  public Address toAddress(Account.Id id) {
    Optional<Account> accountState = args.accountCache.get(id).map(AccountState::account);
    if (!accountState.isPresent()) {
      return null;
    }

    Account account = accountState.get();
    String e = account.preferredEmail();
    if (!account.isActive() || e == null) {
      return null;
    }
    return Address.create(account.fullName(), e);
  }

  /** Returns the type of notification being sent. */
  public String getMessageClass() {
    return messageClass;
  }

  /** Set recipients, headers, body of the email. */
  public void populateEmailContent() throws EmailException {
    for (RecipientType recipientType : notify.accounts().keySet()) {
      notify.accounts().get(recipientType).stream().forEach(a -> addByAccountId(recipientType, a));
    }

    addSoyParam("messageClass", messageClass);
    addSoyParam("footers", footers);
    addSoyEmailDataParam("settingsUrl", getSettingsUrl());
    addSoyEmailDataParam("instanceName", getInstanceName());
    addSoyEmailDataParam("gerritHost", getGerritHost());
    addSoyParam("email", soyContextEmailData);

    templateProvider.populateEmailContent();
  }

  /** Adds param to the data map passed into soy when rendering templates. */
  public void addSoyParam(String key, Object value) {
    soyContext.put(key, value);
  }

  /** Adds entry to the `email` param passed to the soy when rendering templates. */
  public void addSoyEmailDataParam(String key, Object value) {
    soyContextEmailData.put(key, value);
  }

  /**
   * Add a line to email footer with additional information. Typically, in the form of {@literal
   * <key>: <value>}.
   */
  public void addFooter(String footer) {
    footers.add(footer);
  }

  /**
   * Add a resource that can be referenced in HTML code using their {@link EmailResource#contentId}.
   */
  public void addHtmlResource(EmailResource resource) {
    htmlResources.add(resource);
  }

  private String getInstanceName() {
    return args.instanceNameProvider.get();
  }

  /** Renders a soy template of kind="text". */
  public String textTemplate(String name) {
    return configureRenderer(name).renderText().get();
  }

  /** Renders a soy template of kind="html". */
  public SanitizedContent soyHtmlTemplate(String name) {
    return configureRenderer(name).renderHtml().get();
  }

  /** Renders a soy template of kind="css". */
  @UsedAt(UsedAt.Project.GOOGLE)
  public SanitizedContent soyCssTemplate(String name) {
    return configureRenderer(name).renderCss().get();
  }

  /** Configures a soy renderer for the given template name and rendering data map. */
  private SoySauce.Renderer configureRenderer(String templateName) {
    int baseNameIndex = templateName.indexOf("_");
    // In case there are multiple templates in file (now only InboundEmailRejection and
    // InboundEmailRejectionHtml).
    String fileNamespace =
        baseNameIndex == -1 ? templateName : templateName.substring(0, baseNameIndex);
    String templateInFileNamespace =
        String.join(".", SOY_TEMPLATE_NAMESPACE, fileNamespace, templateName);
    String templateInCommonNamespace = String.join(".", SOY_TEMPLATE_NAMESPACE, templateName);
    SoySauce soySauce = args.soySauce.get();
    // For backwards compatibility with existing customizations and plugin templates with the
    // old non-unique namespace.
    String fullTemplateName =
        soySauce.hasTemplate(templateInFileNamespace)
            ? templateInFileNamespace
            : templateInCommonNamespace;
    return soySauce.renderTemplate(fullTemplateName).setData(soyContext);
  }

  /** Remove user from the multipart email recipients. */
  private void removeUser(Account user) {
    String fromEmail = user.preferredEmail();
    for (Iterator<Address> j = smtpRcptTo.iterator(); j.hasNext(); ) {
      if (j.next().email().equals(fromEmail)) {
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

  /** Return true, if the email should include html body. */
  public boolean useHtml() {
    return args.settings.html;
  }
}
