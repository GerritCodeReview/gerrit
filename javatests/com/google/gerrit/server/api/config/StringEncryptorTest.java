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

package com.google.gerrit.server.api.config;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.api.config.EncryptionConfig.ALGORITHM;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;

import java.security.GeneralSecurityException;
import javax.crypto.spec.SecretKeySpec;
import org.junit.Before;
import org.junit.Test;

public class StringEncryptorTest {

  private static final String PLAINTEXT_PASSWORD = "password_to_test";
  private static final String ENCRYPTION_KEY = "19dws7ao0KlIqpj4h1mhXQ==";

  private StringEncryptor encryptor;

  @Before
  public void setUp() throws Exception {
    KeyManager manager = createNiceMock(KeyManager.class);
    EncryptionConfig config = createNiceMock(EncryptionConfig.class);
    expect(manager.getSecretKey()).andReturn(ENCRYPTION_KEY);
    expect(manager.getSecretKeySpec())
        .andReturn(new SecretKeySpec(KeyManager.base64Decode(ENCRYPTION_KEY), ALGORITHM))
        .anyTimes();
    expect(config.transformation()).andReturn("AES/GCM/NoPadding").anyTimes();
    replay(manager);
    replay(config);
    encryptor = new StringEncryptor(config, manager);
  }

  @Test
  public void testEncrypt() throws GeneralSecurityException {
    String encrypted = encryptor.encrypt(PLAINTEXT_PASSWORD);
    assertThat(encrypted).isNotEqualTo(PLAINTEXT_PASSWORD);
  }

  @Test
  public void testDecrypt() throws GeneralSecurityException {
    String encrypted = encryptor.encrypt(PLAINTEXT_PASSWORD);
    String plaintext = encryptor.decrypt(encrypted);
    assertThat(plaintext).isEqualTo(PLAINTEXT_PASSWORD);
  }
}
