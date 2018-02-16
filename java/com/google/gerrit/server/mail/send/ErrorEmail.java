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

import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.server.mail.Address;
import com.google.gerrit.server.project.ProjectState;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.sql.Timestamp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Sends an email to one or more interested parties. */
public class ErrorEmail extends OutgoingEmail {

  private static final Logger log = LoggerFactory.getLogger(ErrorEmail.class);
  protected Timestamp timestamp;
  protected ProjectState projectState;
  private Address to;

  private String replyTo;
  private Object reason;

  @Inject
  protected ErrorEmail(EmailArguments ea, String messageClass) throws OrmException {
    super(ea, messageClass);
  }

  @Override
  protected void init() throws EmailException {
    super.init();
    setListIdHeader();

    add(RecipientType.TO, to);

    if (replyTo != null) {
      setHeader("Reply-To", replyTo);
    }
  }

  private void setListIdHeader() {
    // Set a reasonable list id so that filters can be used to sort messages
    setHeader("List-Id", "<gerrit-noreply." + getGerritHost() + ">");
    if (getSettingsUrl() != null) {
      setHeader("List-Unsubscribe", "<" + getSettingsUrl() + ">");
    }
  }

  /** Format the message body by calling {@link #appendText(String)}. */
  @Override
  protected void format() throws EmailException {
    formatChange();
    appendText(textTemplate("ErrorEmail"));
    if (useHtml()) {
      appendHtml(soyHtmlTemplate("ErrorEmailHtml"));
    }
    formatFooter();
  }

  /** Format the message body by calling {@link #appendText(String)}. */
  protected void formatChange() throws EmailException {
    appendText("My test message is here. Error!");
  }

  /**
   * Format the message footer by calling {@link #appendText(String)}.
   *
   * @throws EmailException if an error occurred.
   */
  protected void formatFooter() throws EmailException {}

  /** Get a link to the change; null if the server doesn't know its own address. */
  public String getChangeUrl() {
    if (getGerritUrl() != null) {
      return getGerritUrl();
    }
    return null;
  }

  /** Get the project entity the change is in; null if its been deleted. */
  protected ProjectState getProjectState() {
    return projectState;
  }

  @Override
  protected void setupSoyContext() {
    super.setupSoyContext();
    footers.add("Gerrit-MessageType: " + messageClass);
    soyContext.put("reason", getReason());
  }

  public boolean getIncludeDiff() {
    return args.settings.includeDiff;
  }

  public void setTo(Address to) throws EmailException {
    this.to = to;
  }

  public void setReplyTo(String replyTo) {
    this.replyTo = replyTo;
  }

  public Object getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  public interface Factory {

    ErrorEmail create();
  }
}
