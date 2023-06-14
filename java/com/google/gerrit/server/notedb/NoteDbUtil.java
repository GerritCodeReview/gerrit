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

package com.google.gerrit.server.notedb;

import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Ints;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdCache;
import com.google.gerrit.server.config.GerritServerId;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Optional;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.util.GitDateFormatter;
import org.eclipse.jgit.util.GitDateFormatter.Format;

@Singleton
public class NoteDbUtil {

  private final String serverId;
  private final ExternalIdCache externalIdCache;

  @Inject
  public NoteDbUtil(@GerritServerId String serverId, ExternalIdCache externalIdCache) {
    this.serverId = serverId;
    this.externalIdCache = externalIdCache;
  }

  private static final CharMatcher INVALID_FOOTER_CHARS = CharMatcher.anyOf("\r\n\0");

  private static final ImmutableList<String> PACKAGE_PREFIXES =
      ImmutableList.of("com.google.gerrit.server.", "com.google.gerrit.");
  private static final ImmutableSet<String> SERVLET_NAMES =
      ImmutableSet.of("com.google.gerrit.httpd.restapi.RestApiServlet");

  /**
   * Returns an AccountId for the given person's identity and the current serverId. Reverse lookup
   * the AccountId using the ExternalIdCache if the account has a foreign serverId.
   *
   * @param ident the accountId@serverId identity
   * @return a defined accountId if the account was found, {@link Account#UNKNOWN_ACCOUNT_ID} if the
   *     lookup via external-id did not return any account, or an empty value if the identity was
   *     malformed.
   */
  public Optional<Account.Id> parseIdent(PersonIdent ident) {
    return parseIdent(ident.getEmailAddress());
  }

  /**
   * Returns an AccountId for the given email address and the current serverId. Reverse lookup the
   * AccountId using the ExternalIdCache if the account has a foreign serverId.
   *
   * @param email the accountId@serverId email identity
   * @return a defined accountId if the account was found, {@link Account#UNKNOWN_ACCOUNT_ID} if the
   *     lookup via external-id did not return any account, or an empty value if the identity was
   *     malformed.
   */
  public Optional<Account.Id> parseIdent(String email) {
    int at = email.indexOf('@');
    if (at >= 0) {
      Integer id = Ints.tryParse(email.substring(0, at));
      String accountServerId = email.substring(at + 1);
      if (id != null) {
        if (accountServerId.equals(serverId)) {
          return Optional.of(Account.id(id));
        }

        ExternalId.Key extIdKey = ExternalId.Key.create(ExternalId.SCHEME_IMPORTED, email, false);
        try {
          return externalIdCache
              .byKey(extIdKey)
              .map(ExternalId::accountId)
              .or(() -> Optional.of(Account.UNKNOWN_ACCOUNT_ID));
        } catch (IOException e) {
          throw new IllegalArgumentException("Unable to lookup external id from cache", e);
        }
      }
    }
    return Optional.empty();
  }

  public static String extractHostPartFromPersonIdent(PersonIdent ident) {
    String email = ident.getEmailAddress();
    int at = email.indexOf('@');
    if (at >= 0) {
      return email.substring(at + 1);
    }
    throw new IllegalArgumentException("No host part found: " + email);
  }

  public static String formatTime(PersonIdent ident, Timestamp t) {
    GitDateFormatter dateFormatter = new GitDateFormatter(Format.DEFAULT);
    // TODO(dborowitz): Use a ThreadLocal or use Joda.
    PersonIdent newIdent = new PersonIdent(ident, t);
    return dateFormatter.formatDate(newIdent);
  }

  /**
   * Returns the name of the REST API handler that is in the stack trace of the caller of this
   * method.
   */
  @Nullable
  static String guessRestApiHandler() {
    StackTraceElement[] trace = Thread.currentThread().getStackTrace();
    int i = findRestApiServlet(trace);
    if (i < 0) {
      i = findApiImpl(trace);
    }
    if (i < 0) {
      return null;
    }
    try {
      for (i--; i >= 0; i--) {
        String cn = trace[i].getClassName();
        Class<?> cls = Class.forName(cn);
        if (RestModifyView.class.isAssignableFrom(cls)) {
          return viewName(cn);
        }
      }
      return null;
    } catch (ClassNotFoundException e) {
      return null;
    }
  }

  static String sanitizeFooter(String value) {
    // Remove characters that would confuse JGit's footer parser if they were
    // included in footer values, for example by splitting the footer block into
    // multiple paragraphs.
    //
    // One painful example: RevCommit#getShorMessage() might return a message
    // containing "\r\r", which RevCommit#getFooterLines() will treat as an
    // empty paragraph for the purposes of footer parsing.
    return INVALID_FOOTER_CHARS.trimAndCollapseFrom(value, ' ');
  }

  private static int findRestApiServlet(StackTraceElement[] trace) {
    for (int i = 0; i < trace.length; i++) {
      if (SERVLET_NAMES.contains(trace[i].getClassName())) {
        return i;
      }
    }
    return -1;
  }

  private static int findApiImpl(StackTraceElement[] trace) {
    for (int i = 0; i < trace.length; i++) {
      String clazz = trace[i].getClassName();
      if (clazz.startsWith("com.google.gerrit.server.api.") && clazz.endsWith("ApiImpl")) {
        return i;
      }
    }
    return -1;
  }

  private static String viewName(String cn) {
    String impl = cn.replace('$', '.');
    for (String p : PACKAGE_PREFIXES) {
      if (impl.startsWith(p)) {
        return impl.substring(p.length());
      }
    }
    return impl;
  }
}
