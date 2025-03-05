/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.gerrit.entities.converter;

import com.google.errorprone.annotations.Immutable;
import com.google.gerrit.extensions.api.accounts.AccountInput;
import com.google.gerrit.extensions.auth.AuthTokenInput;
import com.google.gerrit.proto.Entities;
import com.google.protobuf.Parser;
import java.util.stream.Collectors;

/**
 * Proto converter between {@link AccountInput} and {@link
 * com.google.gerrit.proto.Entities.AccountInput}.
 */
@Immutable
public enum AccountInputProtoConverter
    implements ProtoConverter<Entities.AccountInput, AccountInput> {
  INSTANCE;

  private final ProtoConverter<Entities.AuthTokenInput, AuthTokenInput> tokenInputConverter =
      TokenInputProtoConverter.INSTANCE;

  @Override
  public Entities.AccountInput toProto(AccountInput accountInput) {
    Entities.AccountInput.Builder builder = Entities.AccountInput.newBuilder();
    if (accountInput.username != null) {
      builder.setUsername(accountInput.username);
    }
    if (accountInput.name != null) {
      builder.setName(accountInput.name);
    }
    if (accountInput.displayName != null) {
      builder.setDisplayName(accountInput.displayName);
    }
    if (accountInput.email != null) {
      builder.setEmail(accountInput.email);
    }
    if (accountInput.sshKey != null) {
      builder.setSshKey(accountInput.sshKey);
    }
    if (accountInput.httpPassword != null) {
      builder.setHttpPassword(accountInput.httpPassword);
    }
    if (accountInput.groups != null) {
      builder.addAllGroups(accountInput.groups);
    }
    if (accountInput.tokens != null) {
      builder.addAllTokens(
          accountInput.tokens.stream()
              .map(tokenInputConverter::toProto)
              .collect(Collectors.toList()));
    }

    return builder.build();
  }

  @Override
  public AccountInput fromProto(Entities.AccountInput proto) {
    AccountInput accountInput = new AccountInput();
    if (proto.hasUsername()) {
      accountInput.username = proto.getUsername();
    }
    if (proto.hasName()) {
      accountInput.name = proto.getName();
    }
    if (proto.hasDisplayName()) {
      accountInput.displayName = proto.getDisplayName();
    }
    if (proto.hasEmail()) {
      accountInput.email = proto.getEmail();
    }
    if (proto.hasSshKey()) {
      accountInput.sshKey = proto.getSshKey();
    }
    if (proto.hasHttpPassword()) {
      accountInput.httpPassword = proto.getHttpPassword();
    }
    if (proto.getGroupsCount() > 0) {
      accountInput.groups = proto.getGroupsList();
    }
    if (proto.getTokensCount() > 0) {
      accountInput.tokens =
          proto.getTokensList().stream()
              .map(tokenInputConverter::fromProto)
              .collect(Collectors.toList());
    }
    return accountInput;
  }

  @Override
  public Parser<Entities.AccountInput> getParser() {
    return Entities.AccountInput.parser();
  }
}
