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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.gerrit.proto.testing.SerializedClassSubject.assertThatSerializedClass;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.extensions.api.accounts.AccountInput;
import com.google.gerrit.extensions.auth.AuthTokenInput;
import com.google.gerrit.proto.Entities;
import com.google.gerrit.proto.testing.SerializedClassSubject;
import com.google.inject.TypeLiteral;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.junit.Test;

public class AccountInputProtoConverterTest {
  private final AccountInputProtoConverter accountInputProtoConverter =
      AccountInputProtoConverter.INSTANCE;
  private final TokenInputProtoConverter tokenInputProtoConverter =
      TokenInputProtoConverter.INSTANCE;

  private AccountInput createAccountInputInstance() {

    AccountInput accountInput = new AccountInput();
    accountInput.username = "test-username";
    accountInput.name = "test-name";
    accountInput.displayName = "test-display-name";
    accountInput.email = "test-email@gmail.com";
    accountInput.sshKey = "test-ssh-key";
    accountInput.httpPassword = "test-http-password";
    accountInput.groups = List.of("group1", "group2");
    accountInput.tokens = getTokens();
    return accountInput;
  }

  private List<AuthTokenInput> getTokens() {
    AuthTokenInput token1 = new AuthTokenInput();
    token1.id = "id1";
    token1.token = "secret";

    AuthTokenInput token2 = new AuthTokenInput();
    token2.id = "another_token";
    token2.token = "123456";

    return List.of(token1, token2);
  }

  private void assertAccountInputEquals(AccountInput expected, AccountInput actual) {
    assertThat(
            Objects.equals(expected.username, actual.username)
                && Objects.equals(expected.name, actual.name)
                && Objects.equals(expected.displayName, actual.displayName)
                && Objects.equals(expected.email, actual.email)
                && Objects.equals(expected.sshKey, actual.sshKey)
                && Objects.equals(expected.httpPassword, actual.httpPassword)
                && Objects.equals(expected.tokens, actual.tokens)
                && Objects.equals(expected.groups, actual.groups))
        .isTrue();
  }

  @Test
  public void allValuesConvertedToProto() {

    Entities.AccountInput proto = accountInputProtoConverter.toProto(createAccountInputInstance());

    Entities.AccountInput expectedProto =
        Entities.AccountInput.newBuilder()
            .setUsername("test-username")
            .setName("test-name")
            .setDisplayName("test-display-name")
            .setEmail("test-email@gmail.com")
            .setSshKey("test-ssh-key")
            .setHttpPassword("test-http-password")
            .addAllGroups(ImmutableList.of("group1", "group2"))
            .addAllTokens(
                getTokens().stream()
                    .map(tokenInputProtoConverter::toProto)
                    .collect(Collectors.toList()))
            .build();
    assertThat(proto).isEqualTo(expectedProto);
  }

  @Test
  public void allValuesConvertedToProtoAndBackAgain() {
    AccountInput accountInput = createAccountInputInstance();

    AccountInput convertedaccountInput =
        accountInputProtoConverter.fromProto(accountInputProtoConverter.toProto(accountInput));

    assertAccountInputEquals(accountInput, convertedaccountInput);
  }

  /** See {@link SerializedClassSubject} for background and what to do if this test fails. */
  @Test
  public void methodsExistAsExpected() {
    assertThatSerializedClass(AccountInput.class)
        .hasFields(
            ImmutableMap.<String, Type>builder()
                .put("username", String.class)
                .put("name", String.class)
                .put("displayName", String.class)
                .put("email", String.class)
                .put("sshKey", String.class)
                .put("httpPassword", String.class)
                .put("groups", new TypeLiteral<List<String>>() {}.getType())
                .put("tokens", new TypeLiteral<List<AuthTokenInput>>() {}.getType())
                .build());
  }
}
