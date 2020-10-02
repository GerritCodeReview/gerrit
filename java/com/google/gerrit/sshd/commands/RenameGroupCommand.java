// Copyright (C) 2011 The Android Open Source Project
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

import com.google.gerrit.extensions.common.NameInput;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.server.group.GroupResource;
import com.google.gerrit.server.restapi.group.GroupsCollection;
import com.google.gerrit.server.restapi.group.PutName;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.kohsuke.args4j.Argument;

@CommandMetaData(name = "rename-group", description = "Rename an account group")
public class RenameGroupCommand extends SshCommand {
  @Argument(
      index = 0,
      required = true,
      metaVar = "GROUP",
      usage = "name of the group to be renamed")
  private String groupName;

  @Argument(index = 1, required = true, metaVar = "NEWNAME", usage = "new name of the group")
  private String newGroupName;

  @Inject private GroupsCollection groups;

  @Inject private PutName putName;

  @Override
  protected void run() throws Failure {
    enableGracefulStop();
    try {
      GroupResource rsrc = groups.parse(TopLevelResource.INSTANCE, IdString.fromDecoded(groupName));
      NameInput input = new NameInput();
      input.name = newGroupName;
      putName.apply(rsrc, input);
    } catch (RestApiException | IOException | ConfigInvalidException e) {
      throw die(e);
    }
  }
}
