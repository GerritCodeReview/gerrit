// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.entities.converter;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.proto.testing.SerializedClassSubject.assertThatSerializedClass;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.entities.Account;
import com.google.gerrit.proto.testing.SerializedClassSubject;
import java.lang.reflect.Type;
import java.time.Instant;
import org.junit.Test;

/**
 * Tests for {@link AccountProtoConverter}.
 *
 * <p>These tests cover the requirements that would be enforced by {@code SafeProtoConverterTest} if
 * {@code AccountProtoConverter} implemented {@code SafeProtoConverter}. The converter cannot use
 * {@code SafeProtoConverter} because proto3 cannot distinguish between unset and empty string
 * fields, while Account treats null and empty string differently.
 */
public class AccountProtoConverterTest {
  private final AccountProtoConverter converter = AccountProtoConverter.INSTANCE;

  @Test
  public void allFieldsConvertedToProtoAndBack() {
    Account account =
        Account.builder(Account.id(123), Instant.ofEpochMilli(1234567890L))
            .setFullName("Test User")
            .setDisplayName("Test")
            .setPreferredEmail("test@example.com")
            .setAvatarEmail("avatar@example.com")
            .setInactive(false)
            .setStatus("OOO")
            .setMetaId("meta-123")
            .setUniqueTag("unique-123")
            .build();

    Account roundTripped = converter.fromProto(converter.toProto(account));

    assertThat(roundTripped).isEqualTo(account);
  }

  @Test
  public void nullFieldsPreservedThroughRoundTrip() {
    Account account =
        Account.builder(Account.id(789), Instant.ofEpochMilli(1111111111L))
            .setInactive(false)
            .build();

    Account roundTripped = converter.fromProto(converter.toProto(account));

    assertThat(roundTripped.fullName()).isNull();
    assertThat(roundTripped.displayName()).isNull();
    assertThat(roundTripped.preferredEmail()).isNull();
    assertThat(roundTripped.avatarEmail()).isNull();
    assertThat(roundTripped.status()).isNull();
  }

  @Test
  public void avatarEmailPreservedThroughRoundTrip() {
    Account account =
        Account.builder(Account.id(100), Instant.ofEpochMilli(2222222222L))
            .setPreferredEmail("preferred@example.com")
            .setAvatarEmail("avatar@example.com")
            .setInactive(false)
            .build();

    Account roundTripped = converter.fromProto(converter.toProto(account));

    assertThat(roundTripped.avatarEmail()).isEqualTo("avatar@example.com");
    assertThat(roundTripped.preferredEmail()).isEqualTo("preferred@example.com");
    assertThat(roundTripped.effectiveAvatarEmail()).isEqualTo("avatar@example.com");
  }

  @Test
  public void effectiveAvatarEmailFallsBackWhenAvatarEmailNull() {
    Account account =
        Account.builder(Account.id(101), Instant.ofEpochMilli(3333333333L))
            .setPreferredEmail("preferred@example.com")
            .setInactive(false)
            .build();

    Account roundTripped = converter.fromProto(converter.toProto(account));

    assertThat(roundTripped.avatarEmail()).isNull();
    assertThat(roundTripped.effectiveAvatarEmail()).isEqualTo("preferred@example.com");
  }

  @Test
  public void inactiveFieldPreserved() {
    Account active =
        Account.builder(Account.id(1), Instant.ofEpochMilli(1L)).setInactive(false).build();
    Account inactive =
        Account.builder(Account.id(2), Instant.ofEpochMilli(1L)).setInactive(true).build();

    assertThat(converter.fromProto(converter.toProto(active)).inactive()).isFalse();
    assertThat(converter.fromProto(converter.toProto(inactive)).inactive()).isTrue();
  }

  /**
   * If this test fails, it's likely that a field was added to or removed from {@link Account}. If a
   * field was added, please update {@link AccountProtoConverter} and the {@code AccountProto} in
   * {@code cache.proto} accordingly.
   *
   * @see SerializedClassSubject
   */
  @Test
  public void accountFieldsMatchExpected() {
    assertThatSerializedClass(Account.class)
        .hasFields(
            ImmutableMap.<String, Type>builder()
                .put("id", Account.Id.class)
                .put("registeredOn", Instant.class)
                .put("fullName", String.class)
                .put("displayName", String.class)
                .put("preferredEmail", String.class)
                .put("avatarEmail", String.class)
                .put("inactive", boolean.class)
                .put("status", String.class)
                .put("metaId", String.class)
                .put("uniqueTag", String.class)
                .build());
  }
}
