// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.server.update;

import com.google.gerrit.server.update.context.RefUpdateContext;
import com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType;
import java.io.IOException;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.revwalk.RevWalk;

public interface RefUpdateWrapper {
  default RefUpdateAutoClosable beginRefUpdate(RefUpdate u) {
    RefUpdateContext ctx = RefUpdateContext.open(RefUpdateType.CREATE_NEW_BRANCH);
    return new RefUpdateAutoClosable() {
      @Override
      public void close() {
        ctx.close();
      }
    };
  }

  default RefUpdate.Result wrapRefUpdate(RefUpdate u) throws IOException {
    try(RefUpdateAutoClosable ac = beginRefUpdate(u)) {
      return u.update();
    }
  }
  default RefUpdate.Result wrapRefUpdate(RefUpdate u, RevWalk rw) throws IOException {
    try(RefUpdateAutoClosable ac = beginRefUpdate(u)) {
      return u.update(rw);
    }
  }

  interface RefUpdateAutoClosable extends AutoCloseable {
    @Override
    void close();
  }
}
