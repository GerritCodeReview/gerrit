package com.google.gerrit.server;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gerrit.server.cache.proto.Cache.ChangeRefKeyProto;
import com.google.gerrit.server.cache.proto.Cache.ChangeRefStateProto;
import com.google.gerrit.testing.GerritBaseTests;
import com.google.protobuf.ByteString;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class ChangeRefCacheBenchmarkTest extends GerritBaseTests {

  @Test
  public void benchmark() throws Exception {
    ObjectId metaSha1 = ObjectId.fromString("1234567812345678123456781234567812345678");
    byte[] buf = new byte[Constants.OBJECT_ID_LENGTH];
    metaSha1.copyRawTo(buf, 0);

    ChangeRefKeyProto key =
        ChangeRefKeyProto.newBuilder()
            .setChangeId(12345)
            .setRepoKey("chromium/chromium/src/nested/repo")
            .build();
    ChangeRefStateProto.Builder valueBuilder =
        ChangeRefStateProto.newBuilder()
            .setMetaSha1(ByteString.copyFrom(buf))
            .setOwnerAccountId(12345)
            .addReviewerAccountId(123444)
            .addReviewerAccountId(33333)
            .addCcAccountId(12312312)
            .setDestinationBranch("refs/heads/stable-2.16")
            .setIsPrivate(true);
    ChangeRefStateProto value = valueBuilder.build();

    int cacheSize = 1000000;
    ChangeRefCache cache = new ChangeRefCache(cacheSize);
    for (int i = 0; i < cacheSize; i++) {
      cache.delegate.put(
          ChangeRefKeyProto.newBuilder()
              .setChangeId(i)
              .setRepoKey("chromium/chromium/src/nested/repo")
              .build(),
          valueBuilder.setOwnerAccountId(i).build());
    }

    Dumper.dumpHeap("/usr/local/google/home/hiesel/local-heap5.bin", true);

    System.err.println("Serialized key size: " + key.toByteArray().length + " bytes");
    System.err.println("Serialized value size: " + value.toByteArray().length + " bytes");

    for (int i = 0; i < 10; i++) {
      System.err.println("Deserializing 2M changes: " + deserialize(key, value, 2000000) + "ms");
      System.err.println("Deserializing 8M changes: " + deserialize(key, value, 8000000) + "ms");
    }
  }

  /** Deserialize both key and value numObjects times and return the duration in ms. */
  private static long deserialize(ChangeRefKeyProto key, ChangeRefStateProto value, int numObjects)
      throws Exception {
    byte[] keyBytes = key.toByteArray();
    byte[] valueBytes = value.toByteArray();

    long start = System.currentTimeMillis();
    for (int i = 0; i < numObjects; i++) {
      if (!ChangeRefKeyProto.parseFrom(keyBytes).equals(key)) {
        throw new RuntimeException("failed to parse key");
      }
      if (!ChangeRefStateProto.parseFrom(valueBytes).equals(value)) {
        throw new RuntimeException("failed to parse value");
      }
    }
    return System.currentTimeMillis() - start;
  }

  /** Make it easier to find the cache in the heapdump. */
  private static class ChangeRefCache {
    private final Cache<ChangeRefKeyProto, ChangeRefStateProto> delegate;

    ChangeRefCache(int size) {
      delegate = CacheBuilder.newBuilder().maximumSize(size).build();
    }
  }
}
