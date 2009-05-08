// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.server.ssh;

import com.google.gerrit.client.reviewdb.SystemConfig.LoginType;
import com.google.gerrit.client.rpc.Common;

import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;

/** Causes the caches to purge all entries and reload. */
class AdminFlushCaches extends AbstractCommand {
  PrintWriter p;

  @Override
  protected void run(String[] args) throws Failure,
      UnsupportedEncodingException {
    assertIsAdministrator();
    p = toPrintWriter(err);
    try {
      Common.getGroupCache().flush();
      Common.getProjectCache().flush();
      Common.getAccountCache().flush();

      if (Common.getGerritConfig().getLoginType() == LoginType.OPENID) {
        flushCache("openid");
      }

      try {
        getGerritServer().getDiffCache().flush();
      } catch (Throwable e1) {
        p.println("warning: cannot flush cache \"diff\": " + e1);
      }
    } finally {
      p.flush();
    }
  }

  private void flushCache(final String name) {
    try {
      getGerritServer().getCache(name).removeAll();
    } catch (Throwable e1) {
      p.println("warning: cannot flush cache \"" + name + "\": " + e1);
    }
  }
}
