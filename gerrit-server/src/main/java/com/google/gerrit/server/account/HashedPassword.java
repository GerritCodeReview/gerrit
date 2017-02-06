package com.google.gerrit.server.account;

import com.google.common.base.Preconditions;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import org.apache.commons.codec.binary.Base64;
import org.bouncycastle.crypto.generators.BCrypt;
import org.bouncycastle.util.Arrays;

/**
 * HashedPassword holds logic for salted, hashed passwords.
 */
public class HashedPassword {

  private static SecureRandom secureRandom = new SecureRandom();

  private byte[] salt;
  private byte[] hashed;

  public String encode() {
    return Base64.encodeBase64String(salt) + ":" +
        Base64.encodeBase64String(hashed);
  }

  public HashedPassword(byte[] hashed, byte[] salt) {
    this.salt = salt;
    this.hashed = hashed;
    // salt must be 128-byte.
    Preconditions.checkState(this.salt.length == 16);

    // Max 72 bytes as input to bouncycastle bcrypt.
    Preconditions.checkState(this.hashed.length <= 72);
  }

  public static HashedPassword decode(String encoded) {
    String[] fields = encoded.split(":");
    Preconditions.checkState(fields.length == 2);

    return new HashedPassword(Base64.decodeBase64(fields[0]), Base64.decodeBase64(fields[1]));
  }

  public boolean checkPassword(String password) {
    return Arrays.areEqual(hashPassword(password, salt), hashed);
  }

  private static byte[] hashPassword(String password, byte []salt) {
    byte pwBytes[] = password.getBytes(StandardCharsets.UTF_8);
    final int cost = 10;
    return BCrypt.generate(pwBytes, salt, cost);
  }

  public static HashedPassword fromPassword(String password) {
    byte []salt = newSalt();

    return new HashedPassword(hashPassword(password, salt), salt);
  }

  private static byte[] newSalt() {
    byte bytes[] = new byte[16];
    secureRandom.nextBytes(bytes);
    return bytes;
  }
}
