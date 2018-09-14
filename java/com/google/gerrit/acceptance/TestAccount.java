// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.acceptance;

import static java.util.stream.Collectors.toList;

import com.google.auto.value.AutoValue;
import com.google.common.net.InetAddresses;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.mail.Address;
import com.google.gerrit.reviewdb.client.Account;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import org.apache.http.client.utils.URIBuilder;
import org.eclipse.jgit.lib.PersonIdent;

@AutoValue
public abstract class TestAccount {
  public static List<Account.Id> ids(List<TestAccount> accounts) {
    return accounts.stream().map(TestAccount::id).collect(toList());
  }

  public static List<String> names(List<TestAccount> accounts) {
    return accounts.stream().map(TestAccount::fullName).collect(toList());
  }

  public static List<String> names(TestAccount... accounts) {
    return names(Arrays.asList(accounts));
  }

  static TestAccount create(
      Account.Id id,
      @Nullable String username,
      @Nullable String email,
      @Nullable String fullName,
      @Nullable String httpPassword) {
    return new AutoValue_TestAccount(id, username, email, fullName, httpPassword);
  }

  public abstract Account.Id id();

  @Nullable
  public abstract String username();

  @Nullable
  public abstract String email();

  @Nullable
  public abstract String fullName();

  @Nullable
  public abstract String httpPassword();

  public PersonIdent newIdent() {
    return new PersonIdent(fullName(), email());
  }

  public String getHttpUrl(GerritServer server) {
    InetSocketAddress addr = server.getHttpAddress();
    return new URIBuilder()
        .setScheme("http")
        .setUserInfo(username(), httpPassword())
        .setHost(InetAddresses.toUriString(addr.getAddress()))
        .setPort(addr.getPort())
        .toString();
  }

  public Address getEmailAddress() {
    // Address is weird enough that it's safer and clearer to create a new instance in a
    // non-abstract method rather than, say, having an abstract emailAddress() as part of this
    // AutoValue class. Specifically:
    //  * Email is not specified as @Nullable in Address, but it is nullable in this class. If this
    //    is a problem, at least it's a problem only for users of TestAccount that actually call
    //    emailAddress().
    //  * Address#equals only considers email, not name, whereas TestAccount#equals should include
    //    name.
    return new Address(fullName(), email());
  }
}
