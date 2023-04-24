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

import com.google.gerrit.entities.Address;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.mail.MailHeader;
import com.google.gerrit.server.mail.send.OutgoingEmailNew.EmailDecorator;
import org.apache.james.mime4j.dom.field.FieldName;

/** Send an email to inform users that parsing their inbound email failed. */
public class InboundEmailRejectionEmailDecorator implements EmailDecorator {

  /** Used by the templating system to determine what error message should be sent */
  public enum InboundEmailError {
    PARSING_ERROR,
    INACTIVE_ACCOUNT,
    UNKNOWN_ACCOUNT,
    INTERNAL_EXCEPTION,
    COMMENT_REJECTED,
    CHANGE_NOT_FOUND
  }

  private OutgoingEmailNew email;
  private final Address to;
  private final InboundEmailError reason;
  private final String threadId;

  public InboundEmailRejectionEmailDecorator(
      Address to, String threadId, InboundEmailError reason) {
    this.to = requireNonNull(to);
    this.threadId = requireNonNull(threadId);
    this.reason = requireNonNull(reason);
  }

  @Override
  public void init(OutgoingEmailNew email) {
    this.email = email;

    setListIdHeader();
    email.setHeader(FieldName.SUBJECT, "[Gerrit Code Review] Unable to process your email");

    if (!threadId.isEmpty()) {
      email.setHeader(MailHeader.REFERENCES.fieldName(), threadId);
    }
  }

  private void setListIdHeader() {
    // Set a reasonable list id so that filters can be used to sort messages
    email.setHeader("List-Id", "<gerrit-noreply." + email.getGerritHost() + ">");
    if (email.getSettingsUrl() != null) {
      email.setHeader("List-Unsubscribe", "<" + email.getSettingsUrl() + ">");
    }
  }

  @Override
  public void populateEmailContent() {
    email.addByEmail(RecipientType.TO, to);

    email.appendText(email.textTemplate("InboundEmailRejection_" + reason.name()));
    if (email.useHtml()) {
      email.appendHtml(email.soyHtmlTemplate("InboundEmailRejectionHtml_" + reason.name()));
    }
  }
}
