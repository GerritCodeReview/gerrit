
package com.google.gerrit.server.api.config;

import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import org.eclipse.jgit.lib.Config;
import wiremock.com.google.common.base.Strings;

public class EncryptionConfig {

  private static final int KEY_LENGTH = 128;
  private static final int ITERATION_COUNT = 1000;
  private static final String ALGORITHM = "AES";
  private static final String HASH_ALGORITHM = "PBKDF2WithHmacSHA512";
  private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";

  public static final String SECTION = "encryption";

  private final Config gerritConfig;

  @Inject
  EncryptionConfig(@GerritServerConfig Config config) {
    this.gerritConfig = config;
  }

  int keyLength() {
    String key = gerritConfig.getString(SECTION, null, "keyLength");
    if (Strings.isNullOrEmpty(key)) {
      return KEY_LENGTH;
    }
    return Integer.parseInt(key);
  }

  int iterations() {
    String iterations = gerritConfig.getString(SECTION, null, "iterations");
    if (Strings.isNullOrEmpty(iterations)) {
      return ITERATION_COUNT;
    }
    return Integer.parseInt(iterations);
  }

  String algorithm() {
    String algorithm = gerritConfig.getString(SECTION, null, "algorithm");
    if (Strings.isNullOrEmpty(algorithm)) {
      return ALGORITHM;
    }
    return algorithm;
  }

  String hashAlgorithm() {
    String hashAlgorithm = gerritConfig.getString(SECTION, null, "hashAlgorithm");
    if (Strings.isNullOrEmpty(hashAlgorithm)) {
      return HASH_ALGORITHM;
    }
    return hashAlgorithm;
  }

  String transformation() {
    String transformation = gerritConfig.getString(SECTION, null, "transformation");
    if (Strings.isNullOrEmpty(transformation)) {
      return TRANSFORMATION;
    }
    return transformation;
  }
}
