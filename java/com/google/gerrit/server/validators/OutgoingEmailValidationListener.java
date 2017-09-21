// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.validators;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.annotations.ExtensionPoint;
import com.google.gerrit.server.mail.Address;
import com.google.gerrit.server.mail.send.EmailHeader;
import java.util.Map;
import java.util.Set;

/** Listener to provide validation on outgoing email notification. */
@ExtensionPoint
public interface OutgoingEmailValidationListener {
  /** Arguments supplied to validateOutgoingEmail. */
  class Args {
    // in arguments
    public String messageClass;
    @Nullable public String htmlBody;

    // in/out arguments
    public Address smtpFromAddress;
    public Set<Address> smtpRcptTo;
    public String body; // The text body of the email.
    public Map<String, EmailHeader> headers;
  }

  /**
   * Outgoing e-mail validation.
   *
   * <p>Invoked by Gerrit just before an e-mail is sent, after all e-mail templates have been
   * applied.
   *
   * <p>Plugins may modify the following fields in args: - smtpFromAddress - smtpRcptTo - body -
   * headers
   *
   * @param args E-mail properties. Some are mutable.
   * @throws ValidationException if validation fails.
   */
  void validateOutgoingEmail(OutgoingEmailValidationListener.Args args) throws ValidationException;
}
