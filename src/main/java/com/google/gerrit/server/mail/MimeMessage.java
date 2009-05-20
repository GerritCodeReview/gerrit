// Copyright (C) 2008 The Android Open Source Project
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
