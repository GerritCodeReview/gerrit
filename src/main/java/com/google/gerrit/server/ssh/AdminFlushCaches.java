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

import com.google.gerrit.client.data.GroupCache;
import com.google.gerrit.client.reviewdb.SystemConfig.LoginType;
import com.google.gerrit.client.rpc.Common;

import java.io.IOException;

/** Causes the caches to purge all entries and reload. */
class AdminFlushCaches extends AbstractCommand {
  @Override
  protected void run(String[] args) throws Failure {
    final GroupCache gc = Common.getGroupCache();
    if (gc.isAdministrator(getAccountId())) {
      gc.flush();
      Common.getProjectCache().flush();
      Common.getAccountCache().flush();
      SshUtil.flush();

      if (Common.getGerritConfig().getLoginType() == LoginType.OPENID) {
        flushCache("openid");
      }
    } else {
      throw new Failure(1, "fatal: Not a Gerrit administrator");
    }
  }

  private void flushCache(final String name) {
    try {
      getGerritServer().getCache(name).removeAll();
    } catch (Throwable e1) {
      try {
        err.write(("warning: cannot flush cache " + name + ": " + err
            .toString()).getBytes("UTF-8"));
      } catch (IOException e2) {
      }
    }
  }
}
