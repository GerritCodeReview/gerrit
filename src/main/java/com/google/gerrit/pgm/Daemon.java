// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.pgm;

import com.google.gerrit.server.ssh.GerritSshDaemon;
import com.google.gwtjsonrpc.server.XsrfException;
import com.google.gwtorm.client.OrmException;

import java.net.SocketException;


/** Run only the SSH daemon portions of Gerrit. */
public class Daemon {
  public static void main(final String[] argv) throws SocketException,
      OrmException, XsrfException {
    boolean slave = false;

    for (String arg : argv) {
      if ("--slave".equals(arg)) {
        slave = true;
      }
    }

    GerritSshDaemon.startSshd(slave);
  }
}
