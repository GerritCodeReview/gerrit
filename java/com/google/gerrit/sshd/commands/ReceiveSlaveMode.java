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

package com.google.gerrit.sshd.commands;

import static com.google.gerrit.sshd.CommandMetaData.Mode.MASTER_OR_SLAVE;

import com.google.gerrit.server.git.validators.ReplicationUserVerifier;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.inject.Inject;
import java.io.IOException;
import org.eclipse.jgit.transport.PacketLineOut;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;

/** Receive command when running in slave mode. */
@CommandMetaData(
  name = "receive-pack",
  description = "Standard Git server side command for client side git push",
  runsAt = MASTER_OR_SLAVE
)
class ReceiveSlaveMode extends Receive {
  @Inject private ReplicationUserVerifier replicationUserVerifier;

  @Override
  protected void runImpl() throws IOException, Failure {
    if (!replicationUserVerifier.isReplicationUser(currentUser)) {
      ServiceNotEnabledException ex = new ServiceNotEnabledException();

      PacketLineOut packetOut = new PacketLineOut(out);
      packetOut.setFlushOnEnd(true);
      packetOut.writeString("ERR " + ex.getMessage());
      packetOut.end();

      throw die(ex);
    }

    super.runImpl();
  }
}
