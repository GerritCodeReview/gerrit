package com.google.gerrit.server.auth.oauth;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.gerrit.server.cache.testing.SerializedClassSubject.assertThatSerializedClass;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.extensions.auth.oauth.OAuthToken;
import com.google.gerrit.server.cache.CacheSerializer;
import com.google.gerrit.server.cache.proto.Cache.OAuthTokenProto;
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
                .setExpiresAt(12345L)
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
            .setExpiresAt(12345L)
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

  /**
   * See {@link com.google.gerrit.server.cache.testing.SerializedClassSubject} for background and
   * what to do if this test fails.
   */
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
