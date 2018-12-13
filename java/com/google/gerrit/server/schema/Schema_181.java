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

package com.google.gerrit.server.schema;

import com.google.gerrit.gpg.PublicKeyStore;
import org.eclipse.jgit.lib.Repository;

public class Schema_181 implements NoteDbSchemaVersion {
  @Override
  public void upgrade(Arguments args, UpdateUI ui) throws Exception {
    ui.message("Rebuild GPGP note map to build subkey to master key map");
    try (Repository repo = args.repoManager.openRepository(args.allUsers);
        PublicKeyStore store = new PublicKeyStore(repo)) {
      store.rebuildSubkeyMasterKeyMap(null);
    }
  }
}
