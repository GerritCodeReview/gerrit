// Copyright (C) 2023 The Android Open Source Project
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

import com.google.common.base.Strings;
import com.google.gerrit.entities.Account;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.config.EnablePeerIPInReflogRecord;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Instant;
import java.time.ZoneId;
import org.eclipse.jgit.lib.PersonIdent;

@Singleton
public class DefaultRefLogIdentityProvider implements RefLogIdentityProvider {
  public static class Module extends AbstractModule {
    @Override
    protected void configure() {
      bind(RefLogIdentityProvider.class).to(DefaultRefLogIdentityProvider.class);
    }
  }

  private final String anonymousCowardName;
  private final Boolean enablePeerIPInReflogRecord;

  @Inject
  DefaultRefLogIdentityProvider(
      @AnonymousCowardName String anonymousCowardName,
      @EnablePeerIPInReflogRecord Boolean enablePeerIPInReflogRecord) {
    this.anonymousCowardName = anonymousCowardName;
    this.enablePeerIPInReflogRecord = enablePeerIPInReflogRecord;
  }

  @Override
  public PersonIdent newRefLogIdent(IdentifiedUser user, Instant when, ZoneId zoneId) {
    Account account = user.getAccount();

    String name = account.fullName();
    if (name == null || name.isEmpty()) {
      name = account.preferredEmail();
    }
    if (name == null || name.isEmpty()) {
      name = anonymousCowardName;
    }

    String email;
    if (enablePeerIPInReflogRecord) {
      email = constructMailAddress(user, guessHost(user));
    } else {
      email =
          Strings.isNullOrEmpty(account.preferredEmail())
              ? constructMailAddress(user, "unknown")
              : account.preferredEmail();
    }

    return new PersonIdent(name, email, when, zoneId);
  }

  private String constructMailAddress(IdentifiedUser user, String host) {
    return user.getUserName().orElse("")
        + "|account-"
        + user.getAccountId().toString()
        + "@"
        + host;
  }

  private String guessHost(IdentifiedUser user) {
    String host = null;
    SocketAddress remotePeer = user.getRemotePeer();
    if (remotePeer instanceof InetSocketAddress) {
      InetSocketAddress sa = (InetSocketAddress) remotePeer;
      InetAddress in = sa.getAddress();
      host = in != null ? in.getHostAddress() : sa.getHostName();
    }
    if (Strings.isNullOrEmpty(host)) {
      return "unknown";
    }
    return host;
  }
}
