// Copyright (C) 2017 The Android Open Source Project
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

import com.google.gerrit.extensions.annotations.ExtensionPoint;
import com.google.gerrit.mail.MailMessage;

/**
 * Listener to filter incoming email.
 *
 * <p>Invoked by Gerrit for each incoming email.
 */
@ExtensionPoint
public interface MailFilter {
  /**
   * Determine if Gerrit should discard or further process the message.
   *
   * @param message MailMessage parsed by Gerrit.
   * @return {@code true}, if Gerrit should process the message, {@code false} otherwise.
   */
  boolean shouldProcessMessage(MailMessage message);
}
