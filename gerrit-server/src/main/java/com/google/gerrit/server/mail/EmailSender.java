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

import java.util.Collection;
import java.util.Map;

/** Sends email messages to third parties. */
public interface EmailSender {
  boolean isEnabled();

  /**
   * Can the address receive messages from us?
   *
   * @param address the address to consider.
   * @return true if this sender will deliver to the address.
   */
  boolean canEmail(String address);

  /**
   * Sends an email message.
   *
   * @param from who the message is from.
   * @param rcpt one or more address where the message will be delivered to.
   *        This list overrides any To or CC headers in {@code headers}.
   * @param headers message headers.
   * @param body text to appear in the body of the message.
   * @throws EmailException the message cannot be sent.
   */
  void send(Address from, Collection<Address> rcpt,
      Map<String, EmailHeader> headers, String body) throws EmailException;
}
