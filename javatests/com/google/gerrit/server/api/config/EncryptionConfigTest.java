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
import static com.google.gerrit.server.api.config.EncryptionConfig.BLOCK_MODE;
import static com.google.gerrit.server.api.config.EncryptionConfig.KEY_LENGTH;
import static com.google.gerrit.server.api.config.EncryptionConfig.PADDING;
import static com.google.gerrit.server.api.config.EncryptionConfig.SECTION;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;

import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;

public class EncryptionConfigTest {

  private Config gerritConfig;
  private EncryptionConfig encryptionCfg;

  @Before
  public void setUp() {
    gerritConfig = createNiceMock(Config.class);
  }

  @Test
  public void testKeyLengthDefault() throws Exception {
    expect(gerritConfig.getInt(SECTION, "keyLength", KEY_LENGTH)).andReturn(KEY_LENGTH);
    replay(gerritConfig);
    encryptionCfg = new EncryptionConfig(gerritConfig);
    assertThat(encryptionCfg.keyLength()).isEqualTo(KEY_LENGTH);
  }

  @Test
  public void testAlgorithmDefault() throws Exception {
    expect(gerritConfig.getString(SECTION, null, "algorithm")).andReturn(ALGORITHM);
    replay(gerritConfig);
    encryptionCfg = new EncryptionConfig(gerritConfig);
    assertThat(encryptionCfg.algorithm()).isEqualTo(ALGORITHM);
  }

  @Test
  public void testTransformationDefault() throws Exception {
    expect(gerritConfig.getString(SECTION, null, "algorithm")).andReturn(ALGORITHM);
    expect(gerritConfig.getString(SECTION, null, "padding")).andReturn(PADDING);
    replay(gerritConfig);
    encryptionCfg = new EncryptionConfig(gerritConfig);
    assertThat(encryptionCfg.transformation())
        .isEqualTo(ALGORITHM + "/" + BLOCK_MODE + "/" + PADDING);
  }

  @Test
  public void testKeyLength() throws Exception {
    expect(gerritConfig.getInt(SECTION, "keyLength", KEY_LENGTH)).andReturn(128);
    replay(gerritConfig);
    encryptionCfg = new EncryptionConfig(gerritConfig);
    assertThat(encryptionCfg.keyLength()).isEqualTo(128);
  }

  @Test
  public void testAlgorithm() throws Exception {
    expect(gerritConfig.getString(SECTION, null, "algorithm")).andReturn("testAlgo");
    replay(gerritConfig);
    encryptionCfg = new EncryptionConfig(gerritConfig);
    assertThat(encryptionCfg.algorithm()).isEqualTo("testAlgo");
  }

  @Test
  public void testHashAlgorithm() throws Exception {
    expect(gerritConfig.getString(SECTION, null, "algorithm")).andReturn("testAlgo");
    expect(gerritConfig.getString(SECTION, null, "padding")).andReturn("testPadding");
    replay(gerritConfig);
    encryptionCfg = new EncryptionConfig(gerritConfig);
    assertThat(encryptionCfg.transformation())
        .isEqualTo("testAlgo" + "/" + BLOCK_MODE + "/" + "testPadding");
  }
}
