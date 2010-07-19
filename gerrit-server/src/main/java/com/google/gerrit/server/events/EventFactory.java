// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.server.events;

import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.internal.Nullable;

@Singleton
public class EventFactory {
  private final AccountCache accountCache;
  private final Provider<String> urlProvider;

  @Inject
  EventFactory(AccountCache accountCache,
      @CanonicalWebUrl @Nullable Provider<String> urlProvider) {
    this.accountCache = accountCache;
    this.urlProvider = urlProvider;
  }

  /**
   * Create a ChangeAttribute for the given change suitable for serialization to
   * JSON.
   *
   * @param change
   * @return object suitable for serialization to JSON
   */
  public ChangeAttribute asChangeAttribute(final Change change) {
    ChangeAttribute a = new ChangeAttribute();
    a.project = change.getProject().get();
    a.branch = change.getDest().getShortName();
    a.id = change.getKey().get();
    a.number = change.getId().toString();
    a.subject = change.getSubject();
    a.url = getChangeUrl(change);
    a.sortKey = change.getSortKey();

    final AccountState owner = accountCache.get(change.getOwner());
    a.owner = asAccountAttribute(owner.getAccount());
    return a;
  }

  /**
   * Create a PatchSetAttribute for the given patchset suitable for
   * serialization to JSON.
   *
   * @param patchSet
   * @return object suitable for serialization to JSON
   */
  public PatchSetAttribute asPatchSetAttribute(final PatchSet patchSet) {
    PatchSetAttribute p = new PatchSetAttribute();
    p.revision = patchSet.getRevision().get();
    p.number = Integer.toString(patchSet.getPatchSetId());
    p.ref = patchSet.getRefName();

    final AccountState uploader = accountCache.get(patchSet.getUploader());
    p.uploader = asAccountAttribute(uploader.getAccount());
    return p;
  }

  /**
   * Create an AuthorAttribute for the given account suitable for serialization
   * to JSON.
   *
   * @param account
   * @return object suitable for serialization to JSON
   */
  public AccountAttribute asAccountAttribute(final Account account) {
    AccountAttribute who = new AccountAttribute();
    who.name = account.getFullName();
    who.email = account.getPreferredEmail();
    return who;
  }

  /** Get a link to the change; null if the server doesn't know its own address. */
  private String getChangeUrl(final Change change) {
    if (change != null && urlProvider.get() != null) {
      final StringBuilder r = new StringBuilder();
      r.append(urlProvider.get());
      r.append(change.getChangeId());
      return r.toString();
    }
    return null;
  }
}
