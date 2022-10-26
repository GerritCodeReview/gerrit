package com.google.gerrit.server.query.change;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dictionary-based encoding algorithm for combining a serverId/legacyChangeNum into a virtual
 * numeric id
 *
 * <p>TODO: To be reverted on master and stable-3.8
 */
@Singleton
public class ChangeNumberBitmapMaskAlgorithm implements ChangeNumberVirtualIdAlgorithm {
  /*
   * Bit-wise masks for representing the Change's VirtualId as combination of ServerId + ChangeNum:
   */
  private static final int CHANGE_NUM_BIT_LEN = 28; // Allows up to 268M changes
  private static final int LEGACY_ID_BIT_MASK = (1 << CHANGE_NUM_BIT_LEN) - 1;
  private static final int SERVER_ID_BIT_LEN =
      Integer.BYTES * 8 - CHANGE_NUM_BIT_LEN; // Allows up to 64 ServerIds

  private final ConcurrentHashMap<String, Integer> serverIdCodes;
  private final AtomicInteger serverIdSequence;

  @Inject
  public ChangeNumberBitmapMaskAlgorithm() {
    this.serverIdCodes = new ConcurrentHashMap<>(1 << SERVER_ID_BIT_LEN);
    this.serverIdSequence = new AtomicInteger(1);
  }

  @Override
  public int apply(String changeServerId, int changeNum) {
    if ((changeNum & LEGACY_ID_BIT_MASK) != changeNum) {
      throw new IllegalArgumentException(
          String.format("Change.Id %s is too large to be converted into a virtual id", changeNum));
    }

    int encodedServerId =
        serverIdCodes.computeIfAbsent(
            changeServerId, serverId -> serverIdSequence.incrementAndGet());
    int virtualId = (changeNum & LEGACY_ID_BIT_MASK) | (encodedServerId << CHANGE_NUM_BIT_LEN);

    return virtualId;
  }
}
