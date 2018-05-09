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

package com.google.gerrit.acceptance.api.config;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.server.api.config.StringEncryptor;
import com.google.gerrit.testing.ConfigSuite;
import com.google.inject.Inject;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(ConfigSuite.class)
@UseLocalDisk
@NoHttpd
public class StringEncryptorIT extends AbstractDaemonTest {

  @Inject private StringEncryptor encryptor;

  private static final String PLAINTEXT_PASSWORD = "password_to_test";
  private static String encoded;

  @Test
  public void testEncrypt() throws Exception {
    encoded = encryptor.encrypt(PLAINTEXT_PASSWORD);
    assertThat(encoded).isNotEqualTo(PLAINTEXT_PASSWORD);
  }

  @Test
  public void testDecrypt() throws Exception {
    String decoded = encryptor.decrypt(encoded);
    assertThat(decoded).isEqualTo(PLAINTEXT_PASSWORD);
  }
}
