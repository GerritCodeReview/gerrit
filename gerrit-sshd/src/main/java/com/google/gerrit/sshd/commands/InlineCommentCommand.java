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

import com.google.gerrit.reviewdb.Patch;
import com.google.gerrit.reviewdb.PatchLineComment;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;

import org.apache.sshd.server.Environment;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class InlineCommentCommand extends PatchSetCommand {

  private final Set<PatchSet.Id> patchSetIds = new HashSet<PatchSet.Id>();

  @Argument(index = 0, required = true, metaVar = "{COMMIT | CHANGE,PATCHSET}", usage = "patch to review")
  void addPatchSetId(final String token) {
    try {
      patchSetIds.addAll(parsePatchSetId(token));
    } catch (UnloggedFailure e) {
      throw new IllegalArgumentException(e.getMessage(), e);
    } catch (OrmException e) {
      throw new IllegalArgumentException("database error", e);
    }
  }

  @Argument(index = 1, required = true, metaVar = "PATH", usage = "filepath for comment")
  private String file;

  @Argument(index = 2, required = true, metaVar = "LINENUMBER", usage = "linenumber for comment")
  private int lineNumber;

  @Argument(index = 3, required = true, metaVar = "MESSAGE", usage = "message to be added")
  private String comment;

  @Option(name = "--left", aliases = "-l", usage = "add comment to left side of diff viewer")
  private boolean left;

  @Inject
  private IdentifiedUser currentUser;

  @Override
  public final void start(final Environment env) {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Failure {
        parseCommandLine();
        generateInlineComments();
      }
    });
  }

  private void generateInlineComments() {

    List<PatchLineComment> patchLineComments = new ArrayList<PatchLineComment>();
    int side = left ? 0 : 1;
    for (PatchSet.Id id : patchSetIds) {
      PatchLineComment inlineComment =
        createPatchLineComment(comment, file, lineNumber, id, side);
      patchLineComments.add(inlineComment);
    }
    try {
      for (final PatchLineComment c : patchLineComments) {
        c.setStatus(PatchLineComment.Status.PUBLISHED);
        c.updated();
      }
      db.patchComments().insert(patchLineComments);
    } catch (OrmException e) {
      throw new IllegalArgumentException("database error", e);
    }

  }

  private PatchLineComment createPatchLineComment(String comment,
      String filename, int lineNumber, PatchSet.Id id, int side) {
    Patch.Key patchKey = new Patch.Key(id, filename);
    PatchLineComment.Key patchLineKey;
    String messageUUID = "";
    try {
      messageUUID = ChangeUtil.messageUUID(db);
    } catch (OrmException e) {
      throw new IllegalArgumentException("database error", e);
    }
    patchLineKey = new PatchLineComment.Key(patchKey, messageUUID);
    lineNumber = lineNumber > 0 ? lineNumber : 1;
    PatchLineComment inlineComment =
        new PatchLineComment(patchLineKey, lineNumber,
            currentUser.getAccountId(), null);

    inlineComment.setSide((short) side);
    inlineComment.setMessage(comment);
    return inlineComment;
  }

}
