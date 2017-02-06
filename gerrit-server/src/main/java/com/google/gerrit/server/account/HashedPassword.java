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
  private static Base64 codec = new Base64(1000, new byte[0], /* urlSafe */ false);
  private byte[] salt;
  private byte[] hashed;

  public String encode() {
    // NOSUBMIT - how to get rid of the line endings?
    return codec.encodeBase64String(salt).replace("\r\n", "") + ":" +
        codec.encodeBase64String(hashed).replace("\r\n", "");
  }

  public HashedPassword(byte[] hashed, byte[] salt) {
    this.salt = salt;
    this.hashed = hashed;

    // salt must be 128-byte.
    Preconditions.checkState(salt.length == 16);

    // Max 72 bytes as input to bouncycastle bcrypt.
    Preconditions.checkState(hashed.length <= 72);
  }

  public static HashedPassword decode(String encoded) {
    String[] fields = encoded.split(":");
    Preconditions.checkState(fields.length == 2);

    return new HashedPassword(codec.decodeBase64(fields[1]), codec.decodeBase64(fields[0]));
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
