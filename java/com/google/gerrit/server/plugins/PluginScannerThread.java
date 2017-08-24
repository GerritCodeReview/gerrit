// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.plugins;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

class PluginScannerThread extends Thread {
  private final CountDownLatch done = new CountDownLatch(1);
  private final PluginLoader loader;
  private final long checkFrequencyMillis;

  PluginScannerThread(PluginLoader loader, long checkFrequencyMillis) {
    this.loader = loader;
    this.checkFrequencyMillis = checkFrequencyMillis;
    setDaemon(true);
    setName("PluginScanner");
  }

  @Override
  public void run() {
    for (; ; ) {
      try {
        if (done.await(checkFrequencyMillis, TimeUnit.MILLISECONDS)) {
          return;
        }
      } catch (InterruptedException e) {
        // Ignored
      }
      loader.rescan();
    }
  }

  void end() {
    done.countDown();
    try {
      join();
    } catch (InterruptedException e) {
      // Ignored
    }
  }
}
