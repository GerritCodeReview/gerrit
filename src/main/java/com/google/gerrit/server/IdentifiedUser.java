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

package com.google.gerrit.server;

import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.AccountGroup;
import com.google.gerrit.client.reviewdb.SystemConfig;
import com.google.gerrit.client.rpc.Common;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.servlet.RequestScoped;

import org.spearce.jgit.lib.PersonIdent;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Set;

/** An authenticated user. */
@RequestScoped
public class IdentifiedUser extends CurrentUser {
  public interface Factory {
    IdentifiedUser create(Account.Id id);
  }

  private final Account.Id accountId;

  @Inject(optional = true)
  @RemotePeer
  private Provider<SocketAddress> remotePeerProvider;

  private Account account;
  private Set<AccountGroup.Id> effectiveGroups;

  @Inject
  IdentifiedUser(final SystemConfig cfg, @Assisted final Account.Id id) {
    super(cfg);
    accountId = id;
  }

  /** The account identity for the user. */
  public Account.Id getAccountId() {
    return accountId;
  }

  public Account getAccount() {
    if (account == null) {
      account = Common.getAccountCache().get(getAccountId());
    }
    return account;
  }

  @Override
  public Set<AccountGroup.Id> getEffectiveGroups() {
    if (effectiveGroups == null) {
      effectiveGroups =
          Common.getGroupCache().getEffectiveGroups(getAccountId());
    }
    return effectiveGroups;
  }

  public PersonIdent toPersonIdent() {
    final Account ua = getAccount();
    String name = ua.getFullName();
    if (name == null) {
      name = ua.getPreferredEmail();
    }
    if (name == null) {
      name = "Anonymous Coward";
    }

    final String userId = "account-" + ua.getId().toString();
    final String user;
    if (ua.getSshUserName() != null) {
      user = ua.getSshUserName() + "|" + userId;
    } else {
      user = userId;
    }

    String host = null;
    final SocketAddress remotePeer =
        remotePeerProvider != null ? remotePeerProvider.get() : null;
    if (remotePeer instanceof InetSocketAddress) {
      final InetSocketAddress sa = (InetSocketAddress) remotePeer;
      final InetAddress in = sa.getAddress();
      if (in != null) {
        host = in.getCanonicalHostName();
      } else {
        host = sa.getHostName();
      }
    }
    if (host == null) {
      host = "unknown";
    }

    return new PersonIdent(name, user + "@" + host);
  }

  @Override
  public String toString() {
    return "IdentifiedUser[account " + getAccountId() + "]";
  }
}
