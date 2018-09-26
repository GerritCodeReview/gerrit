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

package com.google.gerrit.acceptance;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;

public class SshdModule extends AbstractModule {

  @Provides
  @Singleton
  KeyPairProvider createHostKey() {
    return getHostKeys();
  }

  private static SimpleGeneratorHostKeyProvider keys;

  private static synchronized KeyPairProvider getHostKeys() {
    if (keys == null) {
      keys = new SimpleGeneratorHostKeyProvider();
      keys.setAlgorithm("RSA");
      keys.loadKeys();
    }
    return keys;
  }
}
