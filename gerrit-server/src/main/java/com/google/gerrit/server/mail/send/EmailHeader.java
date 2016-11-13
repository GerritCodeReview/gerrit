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

package com.google.gerrit.server.mail.send;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.MoreObjects;
import com.google.gerrit.server.mail.Address;
import java.io.IOException;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public abstract class EmailHeader {
  public abstract boolean isEmpty();

  public abstract void write(Writer w) throws IOException;

  public static class String extends EmailHeader {
    private final java.lang.String value;

    public String(java.lang.String v) {
      value = v;
    }

    public java.lang.String getString() {
      return value;
    }

    @Override
    public boolean isEmpty() {
      return value == null || value.length() == 0;
    }

    @Override
    public void write(Writer w) throws IOException {
      if (needsQuotedPrintable(value)) {
        w.write(quotedPrintable(value));
      } else {
        w.write(value);
      }
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(value);
    }

    @Override
    public boolean equals(Object o) {
      return (o instanceof String) && Objects.equals(value, ((String) o).value);
    }

    @Override
    public java.lang.String toString() {
      return MoreObjects.toStringHelper(this).addValue(value).toString();
    }
  }

  public static boolean needsQuotedPrintable(java.lang.String value) {
    for (int i = 0; i < value.length(); i++) {
      if (value.charAt(i) < ' ' || '~' < value.charAt(i)) {
        return true;
      }
    }
    return false;
  }

  static boolean needsQuotedPrintableWithinPhrase(final int cp) {
    switch (cp) {
      case '!':
      case '*':
      case '+':
      case '-':
      case '/':
      case '=':
      case '_':
        return false;
      default:
        if (('a' <= cp && cp <= 'z') || ('A' <= cp && cp <= 'Z') || ('0' <= cp && cp <= '9')) {
          return false;
        }
        return true;
    }
  }

  public static java.lang.String quotedPrintable(java.lang.String value) {
    final StringBuilder r = new StringBuilder();

    r.append("=?UTF-8?Q?");
    for (int i = 0; i < value.length(); i++) {
      final int cp = value.codePointAt(i);
      if (cp == ' ') {
        r.append('_');

      } else if (needsQuotedPrintableWithinPhrase(cp)) {
        byte[] buf = new java.lang.String(Character.toChars(cp)).getBytes(UTF_8);
        for (byte b : buf) {
          r.append('=');
          r.append(Integer.toHexString((b >>> 4) & 0x0f).toUpperCase());
          r.append(Integer.toHexString(b & 0x0f).toUpperCase());
        }

      } else {
        r.append(Character.toChars(cp));
      }
    }
    r.append("?=");

    return r.toString();
  }

  public static class Date extends EmailHeader {
    private final java.util.Date value;

    public Date(java.util.Date v) {
      value = v;
    }

    public java.util.Date getDate() {
      return value;
    }

    @Override
    public boolean isEmpty() {
      return value == null;
    }

    @Override
    public void write(Writer w) throws IOException {
      final SimpleDateFormat fmt;
      // Mon, 1 Jun 2009 10:49:44 -0700
      fmt = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z", Locale.US);
      w.write(fmt.format(value));
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(value);
    }

    @Override
    public boolean equals(Object o) {
      return (o instanceof Date) && Objects.equals(value, ((Date) o).value);
    }

    @Override
    public java.lang.String toString() {
      return MoreObjects.toStringHelper(this).addValue(value).toString();
    }
  }

  public static class AddressList extends EmailHeader {
    private final List<Address> list = new ArrayList<>();

    public AddressList() {}

    public AddressList(Address addr) {
      add(addr);
    }

    public List<Address> getAddressList() {
      return Collections.unmodifiableList(list);
    }

    public void add(Address addr) {
      list.add(addr);
    }

    void remove(java.lang.String email) {
      for (Iterator<Address> i = list.iterator(); i.hasNext(); ) {
        if (i.next().getEmail().equals(email)) {
          i.remove();
        }
      }
    }

    @Override
    public boolean isEmpty() {
      return list.isEmpty();
    }

    @Override
    public void write(Writer w) throws IOException {
      int len = 8;
      boolean firstAddress = true;
      boolean needComma = false;
      for (final Address addr : list) {
        java.lang.String s = addr.toHeaderString();
        if (firstAddress) {
          firstAddress = false;
        } else if (72 < len + s.length()) {
          w.write(",\r\n\t");
          len = 8;
          needComma = false;
        }

        if (needComma) {
          w.write(", ");
        }
        w.write(s);
        len += s.length();
        needComma = true;
      }
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(list);
    }

    @Override
    public boolean equals(Object o) {
      return (o instanceof AddressList) && Objects.equals(list, ((AddressList) o).list);
    }

    @Override
    public java.lang.String toString() {
      return MoreObjects.toStringHelper(this).addValue(list).toString();
    }
  }
}
