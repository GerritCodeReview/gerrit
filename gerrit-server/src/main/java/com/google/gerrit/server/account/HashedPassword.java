package com.google.gerrit.server.account;

import com.google.common.base.Preconditions;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import org.apache.commons.codec.binary.Base64;
import org.bouncycastle.crypto.generators.BCrypt;
import org.bouncycastle.util.Arrays;

/**
 * HashedPassword holds logic for salted, hashed passwords. It uses BCrypt from BouncyCastle, which
 * truncates passwords at 72 bytes.
 */
public class HashedPassword {
  private static final String ALGORITHM = "bcrypt";
  private static SecureRandom secureRandom = new SecureRandom();
  private static Base64 codec = new Base64(-1);

  /**
   * The decode method decodes a hashed password encoded with {@link #encode}. It throw a runtime
   * exception for malformed input.
   */
  public static HashedPassword decode(String encoded) {
    Preconditions.checkState(encoded.startsWith(ALGORITHM + ":"));
    String[] fields = encoded.split(":");
    Preconditions.checkState(fields.length == 4);
    int cost = Integer.parseInt(fields[1]);
    return new HashedPassword(codec.decodeBase64(fields[3]), codec.decodeBase64(fields[2]), cost);
  }

  private static byte[] hashPassword(String password, byte[] salt, int cost) {
    byte pwBytes[] = password.getBytes(StandardCharsets.UTF_8);

    return BCrypt.generate(pwBytes, salt, cost);
  }

  public static HashedPassword fromPassword(String password) {
    byte[] salt = newSalt();
    final int cost = 10;
    return new HashedPassword(hashPassword(password, salt, cost), salt, cost);
  }

  private static byte[] newSalt() {
    byte bytes[] = new byte[16];
    secureRandom.nextBytes(bytes);
    return bytes;
  }

  private byte[] salt;
  private byte[] hashed;
  private int cost = 10;

  private HashedPassword(byte[] hashed, byte[] salt, int cost) {
    this.salt = salt;
    this.hashed = hashed;
    this.cost = cost;

    Preconditions.checkState(cost > 0);

    // salt must be 128 bit.
    Preconditions.checkState(salt.length == 16);
  }

  /** encode returns one-line string encoding the hash and salt. */
  public String encode() {
    return ALGORITHM
        + ":"
        + cost
        + ":"
        + codec.encodeToString(salt)
        + ":"
        + codec.encodeToString(hashed);
  }

  public boolean checkPassword(String password) {
    return Arrays.areEqual(hashPassword(password, salt, cost), hashed);
  }

}
