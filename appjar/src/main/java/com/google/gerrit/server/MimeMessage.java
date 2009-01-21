package com.google.gerrit.server;

import javax.mail.MessagingException;
import javax.mail.Session;

class MimeMessage extends javax.mail.internet.MimeMessage {
  MimeMessage(final Session session) {
    super(session);
  }

  private String messageID;

  void setMessageID(final String id) {
    messageID = id;
  }

  @Override
  protected void updateMessageID() throws MessagingException {
    if (messageID != null) {
      setHeader("Message-ID", messageID);
    } else {
      super.updateMessageID();
    }
  }
}
