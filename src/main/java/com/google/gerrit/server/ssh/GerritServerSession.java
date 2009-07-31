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

import com.google.gerrit.server.ssh.SshScopes.Context;

import org.apache.mina.core.session.IoSession;
import org.apache.mina.transport.socket.SocketSessionConfig;
import org.apache.sshd.common.FactoryManager;
import org.apache.sshd.common.util.Buffer;
import org.apache.sshd.server.session.ServerSession;

import java.util.ArrayList;

class GerritServerSession extends ServerSession {
  final Context sessionContext;

  GerritServerSession(FactoryManager server, IoSession io, boolean keepAlive)
      throws Exception {
    super(server, io);
    if (io.getConfig() instanceof SocketSessionConfig) {
      final SocketSessionConfig c = (SocketSessionConfig) io.getConfig();
      c.setKeepAlive(keepAlive);
    }

    setAttribute(SshUtil.REMOTE_PEER, io.getRemoteAddress());
    setAttribute(SshUtil.ACTIVE, new ArrayList<AbstractCommand>(2));
    sessionContext =  new Context(this, null);
  }

  @Override
  protected void handleMessage(final Buffer buffer) throws Exception {
    try {
      SshScopes.current.set(sessionContext);
      super.handleMessage(buffer);
    } finally {
      SshScopes.current.set(null);
    }
  }
}
