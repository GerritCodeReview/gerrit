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

import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.server.mail.Address;
import com.google.gerrit.server.mail.Metadata;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import org.apache.james.mime4j.dom.field.FieldName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * end an email to inform users that parsing their inbound email failed.
 */
public class InboundEmailRejectionSender extends OutgoingEmail {

  public interface Factory {

    InboundEmailRejectionSender create(
        Address to,
        @Assisted("reason") String reason,
        @Assisted("replyTo") String replyTo
    );
  }

  private static final Logger log = LoggerFactory.getLogger(InboundEmailRejectionSender.class);
  private Address to;
  private String replyTo;
  private String reason;

  @Inject
  public InboundEmailRejectionSender(
      EmailArguments ea,
      @Assisted Address to,
      @Assisted("reason") String reason,
      @Assisted("replyTo") @Nullable String replyTo)
      throws OrmException {
    super(ea, "error");
    this.to = to;
    this.reason = reason;
    this.replyTo = replyTo;
  }

  @Override
  protected void init() throws EmailException {
    super.init();
    setListIdHeader();

    add(RecipientType.TO, to);

    if (replyTo != null && !replyTo.isEmpty()) {
      setHeader(FieldName.REPLY_TO, replyTo);
    }
  }

  private void setListIdHeader() {
    // Set a reasonable list id so that filters can be used to sort messages
    setHeader("List-Id", "<gerrit-noreply." + getGerritHost() + ">");
    if (getSettingsUrl() != null) {
      setHeader("List-Unsubscribe", "<" + getSettingsUrl() + ">");
    }
  }

  /**
   * Format the message body by calling {@link #appendText(String)}.
   */
  @Override
  protected void format() throws EmailException {
    appendText(textTemplate("InboundEmailRejection"));
  }

  @Override
  protected void setupSoyContext() {
    super.setupSoyContext();
    footers.add(Metadata.MESSAGE_TYPE.withDelimiter() + messageClass);
    soyContext.put("reason", getReason());
  }

  public String getReason() {
    return reason;
  }
}
