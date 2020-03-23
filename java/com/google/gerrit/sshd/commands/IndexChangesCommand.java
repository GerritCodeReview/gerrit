// Copyright (C) 2016 The Android Open Source Project
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

import static com.google.gerrit.server.i18n.I18n.getText;

import com.google.gerrit.entities.Change;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.common.Input;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.restapi.change.Index;
import com.google.gerrit.sshd.ChangeArgumentParser;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import java.util.LinkedHashMap;
import java.util.Map;
import org.kohsuke.args4j.Argument;

@CommandMetaData(name = "changes", description = "Index changes")
final class IndexChangesCommand extends SshCommand {
  @Inject private Index index;

  @Inject private ChangeArgumentParser changeArgumentParser;

  @Argument(
      index = 0,
      required = true,
      multiValued = true,
      metaVar = "CHANGE",
      usage = "changes to index")
  void addChange(String token) {
    try {
      changeArgumentParser.addChange(token, changes, null, false);
    } catch (UnloggedFailure | StorageException | PermissionBackendException e) {
      writeError("warning", e.getMessage());
    }
  }

  private Map<Change.Id, ChangeResource> changes = new LinkedHashMap<>();

  @Override
  protected void run() throws UnloggedFailure {
    boolean ok = true;
    for (ChangeResource rsrc : changes.values()) {
      try {
        index.apply(rsrc, new Input());
      } catch (Exception e) {
        ok = false;
        writeError(
            "error",
            getText("sshd.command.index.changes.specified.failed", rsrc.getId(), e.getMessage()));
      }
    }
    if (!ok) {
      throw die(getText("sshd.command.index.changes.failed.and.die"));
    }
  }
}
