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

import static com.google.gerrit.server.api.config.EncryptionConfig.ALGORITHM;
import static com.google.gerrit.server.api.config.EncryptionConfig.KEY_LENGTH;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import com.google.gerrit.server.securestore.SecureStore;
import org.junit.Before;
import org.junit.Test;

public class KeyManagerTest {

  private SecureStore secureStore;

  @Before
  public void setUp() throws Exception {
    secureStore = createNiceMock(SecureStore.class);
    EncryptionConfig config = createNiceMock(EncryptionConfig.class);
    expect(config.keyLength()).andReturn(KEY_LENGTH);
    expect(config.algorithm()).andReturn(ALGORITHM);
    secureStore.set(
        anyObject(String.class),
        anyObject(String.class),
        anyObject(String.class),
        anyObject(String.class));
    expectLastCall();
    replay(secureStore);
    replay(config);

    new KeyManager(config, secureStore);
  }

  @Test
  public void testCreateSecretKey() {
    // verifying the call to secureStore.set(...) means key was created successfully.
    verify(secureStore);
  }
}
