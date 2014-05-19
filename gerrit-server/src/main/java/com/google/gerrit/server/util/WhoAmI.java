// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.util;

import com.google.gerrit.server.CurrentUser;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WhoAmI implements Runnable {
  private static Logger log = LoggerFactory.getLogger(WhoAmI.class);
  private Provider<CurrentUser> userProvider;

  @Inject
  public WhoAmI(Provider<CurrentUser> userProvider) {
    this.userProvider = userProvider;
  }

  @Override
  public void run() {
    CurrentUser user = userProvider.get();
    log.debug(String.format("[%d][%s] User identified: %b",
        Thread.currentThread().getId(),
        Thread.currentThread().getName(),
        user.isIdentifiedUser()));
  }
}