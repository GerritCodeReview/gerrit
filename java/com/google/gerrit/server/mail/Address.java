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

import com.google.gerrit.server.mail.send.EmailHeader;

public class Address {
  public static Address parse(String in) {
    final int lt = in.indexOf('<');
    final int gt = in.indexOf('>');
    final int at = in.indexOf("@");
    if (0 <= lt && lt < gt && lt + 1 < at && at + 1 < gt) {
      final String email = in.substring(lt + 1, gt).trim();
      final String name = in.substring(0, lt).trim();
      int nameStart = 0;
      int nameEnd = name.length();
      if (name.startsWith("\"")) {
        nameStart++;
      }
      if (name.endsWith("\"")) {
        nameEnd--;
      }
      return new Address(name.length() > 0 ? name.substring(nameStart, nameEnd) : null, email);
    }

    if (lt < 0 && gt < 0 && 0 < at && at < in.length() - 1) {
      return new Address(in);
    }

    throw new IllegalArgumentException("Invalid email address: " + in);
  }

  public static Address tryParse(String in) {
    try {
      return parse(in);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  final String name;
  final String email;

  public Address(String email) {
    this(null, email);
  }

  public Address(String name, String email) {
    this.name = name;
    this.email = email;
  }

  public String getName() {
    return name;
  }

  public String getEmail() {
    return email;
  }

  @Override
  public int hashCode() {
    return email.hashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other instanceof Address) {
      return email.equals(((Address) other).email);
    }
    return false;
  }

  @Override
  public String toString() {
    return toHeaderString();
  }

  public String toHeaderString() {
    if (name != null) {
      return quotedPhrase(name) + " <" + email + ">";
    } else if (isSimple()) {
      return email;
    }
    return "<" + email + ">";
  }

  private static final String MUST_QUOTE_EMAIL = "()<>,;:\\\"[]";
  private static final String MUST_QUOTE_NAME = MUST_QUOTE_EMAIL + "@.";

  private boolean isSimple() {
    for (int i = 0; i < email.length(); i++) {
      final char c = email.charAt(i);
      if (c <= ' ' || 0x7F <= c || MUST_QUOTE_EMAIL.indexOf(c) != -1) {
        return false;
      }
    }
    return true;
  }

  private static String quotedPhrase(String name) {
    if (EmailHeader.needsQuotedPrintable(name)) {
      return EmailHeader.quotedPrintable(name);
    }
    for (int i = 0; i < name.length(); i++) {
      final char c = name.charAt(i);
      if (MUST_QUOTE_NAME.indexOf(c) != -1) {
        return wrapInQuotes(name);
      }
    }
    return name;
  }

  private static String wrapInQuotes(String name) {
    final StringBuilder r = new StringBuilder(2 + name.length());
    r.append('"');
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if (c == '"' || c == '\\') {
        r.append('\\');
      }
      r.append(c);
    }
    r.append('"');
    return r.toString();
  }
}
