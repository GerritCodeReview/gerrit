// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.auth.oauth;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.gerrit.proto.testing.SerializedClassSubject.assertThatSerializedClass;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.auth.oauth.OAuthToken;
import com.google.gerrit.extensions.auth.oauth.OAuthTokenEncrypter;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.proto.testing.SerializedClassSubject;
import com.google.gerrit.server.cache.proto.Cache.OAuthTokenProto;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import java.lang.reflect.Type;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class OAuthTokenCacheTest {
  @Test
  public void oAuthTokenSerializer() throws Exception {
    OAuthToken token = new OAuthToken("token", "secret", "raw", 12345L, "provider");
    CacheSerializer<OAuthToken> s = new OAuthTokenCache.Serializer();
    byte[] serialized = s.serialize(token);
    assertThat(OAuthTokenProto.parseFrom(serialized))
        .isEqualTo(
            OAuthTokenProto.newBuilder()
                .setToken("token")
                .setSecret("secret")
                .setRaw("raw")
                .setExpiresAtMillis(12345L)
                .setProviderId("provider")
                .build());
    assertThat(s.deserialize(serialized)).isEqualTo(token);
  }

  @Test
  public void oAuthTokenSerializerWithNullProvider() throws Exception {
    OAuthToken tokenWithNull = new OAuthToken("token", "secret", "raw", 12345L, null);
    CacheSerializer<OAuthToken> s = new OAuthTokenCache.Serializer();
    OAuthTokenProto expectedProto =
        OAuthTokenProto.newBuilder()
            .setToken("token")
            .setSecret("secret")
            .setRaw("raw")
            .setExpiresAtMillis(12345L)
            .setProviderId("")
            .build();

    byte[] serializedWithNull = s.serialize(tokenWithNull);
    assertThat(OAuthTokenProto.parseFrom(serializedWithNull)).isEqualTo(expectedProto);
    assertThat(s.deserialize(serializedWithNull)).isEqualTo(tokenWithNull);

    OAuthToken tokenWithEmptyString = new OAuthToken("token", "secret", "raw", 12345L, "");
    assertThat(tokenWithEmptyString).isEqualTo(tokenWithNull);
    byte[] serializedWithEmptyString = s.serialize(tokenWithEmptyString);
    assertThat(OAuthTokenProto.parseFrom(serializedWithEmptyString)).isEqualTo(expectedProto);
    assertThat(s.deserialize(serializedWithEmptyString)).isEqualTo(tokenWithNull);
  }

  @Test
  public void serializeAndDeserializeBackAccountId() {
    OAuthTokenCache.AccountIdSerializer serializer = OAuthTokenCache.AccountIdSerializer.INSTANCE;

    Account.Id id = Account.id(1234);
    assertThat(serializer.deserialize(serializer.serialize(id))).isEqualTo(id);
  }

  // Anonymous classes can break some cache implementations that try to parse the
  // serializer class name and expect a well-defined class name: test that
  // OAuthTokenCache.AccountIdSerializer is not an anonymous class.
  @Test
  public void accountIdSerializerIsNotAnAnonymousClass() {
    assertThat(OAuthTokenCache.AccountIdSerializer.INSTANCE.getDeclaringClass().getSimpleName())
        .isNotEmpty();
  }

  private static OAuthTokenCache newCache() {
    Cache<Account.Id, OAuthToken> cache = CacheBuilder.newBuilder().build();
    DynamicItem<OAuthTokenEncrypter> encrypter =
        DynamicItem.itemOf(OAuthTokenEncrypter.class, null);
    return new OAuthTokenCache(cache, encrypter);
  }

  private static OAuthToken tokenExpiringAt(long expiresAtMillis) {
    return new OAuthToken("t", "s", "raw", expiresAtMillis, "provider");
  }

  @Test
  public void getEvenIfExpired_returnsExpiredEntry_withoutEvicting() {
    OAuthTokenCache cache = newCache();
    Account.Id id = Account.id(1);
    OAuthToken expired = tokenExpiringAt(System.currentTimeMillis() - 1000);
    cache.put(id, expired);

    // Returns the expired token, and a second call still returns it: no eviction.
    assertThat(cache.getEvenIfExpired(id)).isEqualTo(expired);
    assertThat(cache.getEvenIfExpired(id)).isEqualTo(expired);
  }

  @Test
  public void getOrEvictIfExpired_evictsExpiredEntry_andReturnsNull() {
    OAuthTokenCache cache = newCache();
    Account.Id id = Account.id(1);
    cache.put(id, tokenExpiringAt(System.currentTimeMillis() - 1000));

    assertThat(cache.getOrEvictIfExpired(id)).isNull();
    // get() evicted it, so even getEvenIfExpired now misses.
    assertThat(cache.getEvenIfExpired(id)).isNull();
  }

  @Test
  public void getOrEvictIfExpired_returnsUnexpiredEntry() {
    OAuthTokenCache cache = newCache();
    Account.Id id = Account.id(1);
    OAuthToken valid = tokenExpiringAt(Long.MAX_VALUE);
    cache.put(id, valid);

    assertThat(cache.getOrEvictIfExpired(id)).isEqualTo(valid);
    assertThat(cache.getEvenIfExpired(id)).isEqualTo(valid);
  }

  @Test
  public void getEvenIfExpired_missing_returnsNull() {
    assertThat(newCache().getEvenIfExpired(Account.id(999))).isNull();
  }

  @Test
  public void hasExpiredToken_trueForExpired_falseForValidOrAbsent() {
    OAuthTokenCache cache = newCache();
    Account.Id id = Account.id(1);
    assertThat(cache.hasExpiredToken(id)).isFalse(); // absent
    cache.put(id, tokenExpiringAt(Long.MAX_VALUE));
    assertThat(cache.hasExpiredToken(id)).isFalse(); // valid
    cache.put(id, tokenExpiringAt(System.currentTimeMillis() - 1000));
    assertThat(cache.hasExpiredToken(id)).isTrue(); // expired
  }

  @Test
  public void getEvenIfExpired_decryptsWithBoundEncrypter() {
    Cache<Account.Id, OAuthToken> backing = CacheBuilder.newBuilder().build();
    DynamicItem<OAuthTokenEncrypter> encrypter =
        DynamicItem.itemOf(OAuthTokenEncrypter.class, new FakeEncrypter());
    OAuthTokenCache cache = new OAuthTokenCache(backing, encrypter);
    Account.Id id = Account.id(1);
    cache.put(id, tokenExpiringAt(Long.MAX_VALUE)); // stored encrypted

    // Backing cache holds the encrypted form; getEvenIfExpired returns the decrypted original.
    assertThat(backing.getIfPresent(id).getToken()).isEqualTo("enc:t");
    assertThat(cache.getEvenIfExpired(id).getToken()).isEqualTo("t");
  }

  /** Reversible, non-crypto stand-in that prefixes the token so decrypt is observable. */
  private static final class FakeEncrypter implements OAuthTokenEncrypter {
    @Override
    public OAuthToken encrypt(OAuthToken t) {
      return new OAuthToken(
          "enc:" + t.getToken(), t.getSecret(), t.getRaw(), t.getExpiresAt(), t.getProviderId());
    }

    @Override
    public OAuthToken decrypt(OAuthToken t) {
      return new OAuthToken(
          t.getToken().substring("enc:".length()),
          t.getSecret(),
          t.getRaw(),
          t.getExpiresAt(),
          t.getProviderId());
    }
  }

  /** See {@link SerializedClassSubject} for background and what to do if this test fails. */
  @Test
  public void oAuthTokenFields() throws Exception {
    assertThatSerializedClass(OAuthToken.class)
        .hasFields(
            ImmutableMap.<String, Type>builder()
                .put("token", String.class)
                .put("secret", String.class)
                .put("raw", String.class)
                .put("expiresAt", long.class)
                .put("providerId", String.class)
                .build());
  }
}
