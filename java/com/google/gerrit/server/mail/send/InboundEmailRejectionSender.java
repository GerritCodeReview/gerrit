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

package com.google.gerrit.server.mail.send;

import static java.util.Objects.requireNonNull;

import com.google.gerrit.exceptions.EmailException;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.mail.Address;
import com.google.gerrit.mail.MailHeader;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import org.apache.james.mime4j.dom.field.FieldName;

/** Send an email to inform users that parsing their inbound email failed. */
public class InboundEmailRejectionSender extends OutgoingEmail {

  /** Used by the templating system to determine what error message should be sent */
  public enum Error {
    PARSING_ERROR,
    INACTIVE_ACCOUNT,
    UNKNOWN_ACCOUNT,
    INTERNAL_EXCEPTION;
  }

  public interface Factory {
    InboundEmailRejectionSender create(Address to, String threadId, Error reason);
  }

  private final Address to;
  private final Error reason;
  private final String threadId;

  @Inject
  public InboundEmailRejectionSender(
      EmailArguments ea, @Assisted Address to, @Assisted String threadId, @Assisted Error reason) {
    super(ea, "error");
    this.to = requireNonNull(to);
    this.threadId = requireNonNull(threadId);
    this.reason = requireNonNull(reason);
  }

  @Override
  protected void init() throws EmailException {
    super.init();
    setListIdHeader();
    setHeader(FieldName.SUBJECT, "[Gerrit Code Review] Unable to process your email");

    add(RecipientType.TO, to);

    if (!threadId.isEmpty()) {
      setHeader(MailHeader.REFERENCES.fieldName(), threadId);
    }
  }

  private void setListIdHeader() {
    // Set a reasonable list id so that filters can be used to sort messages
    setHeader("List-Id", "<gerrit-noreply." + getGerritHost() + ">");
    if (getSettingsUrl() != null) {
      setHeader("List-Unsubscribe", "<" + getSettingsUrl() + ">");
    }
  }

  @Override
  protected void format() throws EmailException {
    appendText(textTemplate("InboundEmailRejection_" + reason.name()));
    if (useHtml()) {
      appendHtml(soyHtmlTemplate("InboundEmailRejectionHtml_" + reason.name()));
    }
  }

  @Override
  protected void setupSoyContext() {
    super.setupSoyContext();
    footers.add(MailHeader.MESSAGE_TYPE.withDelimiter() + messageClass);
  }

  @Override
  protected boolean supportsHtml() {
    return true;
  }
}
