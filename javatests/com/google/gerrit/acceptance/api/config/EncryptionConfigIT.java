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
import static com.google.gerrit.server.api.config.EncryptionConfig.ALGORITHM;
import static com.google.gerrit.server.api.config.EncryptionConfig.HASH_ALGORITHM;
import static com.google.gerrit.server.api.config.EncryptionConfig.ITERATION_COUNT;
import static com.google.gerrit.server.api.config.EncryptionConfig.KEY_LENGTH;
import static com.google.gerrit.server.api.config.EncryptionConfig.SECTION;
import static com.google.gerrit.server.api.config.EncryptionConfig.TRANSFORMATION;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GerritConfig;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.server.api.config.EncryptionConfig;
import com.google.gerrit.testing.ConfigSuite;
import com.google.inject.Inject;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(ConfigSuite.class)
@UseLocalDisk
@NoHttpd
public class EncryptionConfigIT extends AbstractDaemonTest {

  @Inject private EncryptionConfig config;

  @Test
  public void testKeyLengthDefault() throws Exception {
    assertThat(config.keyLength()).isEqualTo(KEY_LENGTH);
  }

  @Test
  public void testAlgorithmDefault() throws Exception {
    assertThat(config.algorithm()).isEqualTo(ALGORITHM);
  }

  @Test
  public void testHashAlgorithmDefault() throws Exception {
    assertThat(config.hashAlgorithm()).isEqualTo(HASH_ALGORITHM);
  }

  @Test
  public void testTransformationDefault() throws Exception {
    assertThat(config.transformation()).isEqualTo(TRANSFORMATION);
  }

  @Test
  public void testIterationsDefault() throws Exception {
    assertThat(config.iterations()).isEqualTo(ITERATION_COUNT);
  }

  @Test
  @GerritConfig(name = SECTION + ".keyLength", value = "1286")
  public void testKeyLength() throws Exception {
    assertThat(config.keyLength()).isEqualTo(1286);
  }

  @Test
  @GerritConfig(name = SECTION + ".algorithm", value = "testAlgo")
  public void testAlgorithm() throws Exception {
    assertThat(config.algorithm()).isEqualTo("testAlgo");
  }

  @Test
  @GerritConfig(name = SECTION + ".hashAlgorithm", value = "testHash")
  public void testHashAlgorithm() throws Exception {
    assertThat(config.hashAlgorithm()).isEqualTo("testHash");
  }

  @Test
  @GerritConfig(name = SECTION + ".transformation", value = "testtransformation")
  public void testTransformation() throws Exception {
    assertThat(config.transformation()).isEqualTo("testtransformation");
  }

  @Test
  @GerritConfig(name = SECTION + ".iterations", value = "2000")
  public void testIterations() throws Exception {
    assertThat(config.iterations()).isEqualTo(2000);
  }
}
