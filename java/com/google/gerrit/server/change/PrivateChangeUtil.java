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

package com.google.gerrit.server.change;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.update.RepoContext;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.ReceiveCommand;

public class PrivateChangeUtil {

  /**
   * Updates the symbolic ref of a private change {@code refs/private-changes/xx/xxxxx/meta} that
   * points to the canonical change meta ref {@code ref/changes/xx/xxxxx/meta}.
   */
  public static void updateSymlinkRef(Change.Id changeId, RepoContext ctx, boolean isPrivate)
      throws IOException {
    String privateMetaRef = RefNames.privateChangeMetaRef(changeId);
    String changeMetaRef = RefNames.changeMetaRef(changeId);
    if (isPrivate) {
      ctx.addRefUpdate(ReceiveCommand.link(ObjectId.zeroId(), changeMetaRef, privateMetaRef));
    } else {
      Optional<ObjectId> existingPrivateRef = ctx.getRepoView().getRef(privateMetaRef);
      if (existingPrivateRef.isPresent()) {
        ctx.addRefUpdate(ReceiveCommand.unlink(changeMetaRef, ObjectId.zeroId(), privateMetaRef));
      }
    }
  }

  private PrivateChangeUtil() {}
}
