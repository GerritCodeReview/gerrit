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

package com.google.gerrit.server.mail.receive.data;

import com.google.gerrit.server.mail.receive.MailMessage;

/** Base class for all email parsing tests. */
public abstract class RawMailMessage {
  // Raw content to feed the parser
  public abstract String raw();

  public abstract int[] rawChars();
  // Parsed representation for asserting the expected parser output
  public abstract MailMessage expectedMailMessage();
}
