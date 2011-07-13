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

package com.google.gerrit.server.mail;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListEntry;
import com.google.gerrit.server.patch.Text;
import com.google.gerrit.server.ssh.SshInfo;

import com.jcraft.jsch.HostKey;

import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Sends an email alerting a user to a new change for them to review. */
public abstract class NewChangeSender extends ChangeEmail {
  private final SshInfo sshInfo;
  private final Set<Account.Id> reviewers = new HashSet<Account.Id>();
  private final Set<Account.Id> extraCC = new HashSet<Account.Id>();
  private Repository db;
  private ObjectReader reader;

  protected NewChangeSender(EmailArguments ea, String anonymousCowardName,
      SshInfo sshInfo, Change c) {
    super(ea, anonymousCowardName, c, "newchange");
    this.sshInfo = sshInfo;
  }

  public void addReviewers(final Collection<Account.Id> cc) {
    reviewers.addAll(cc);
  }

  public void addExtraCC(final Collection<Account.Id> cc) {
    extraCC.addAll(cc);
  }

  @Override
  protected void init() throws EmailException {
    super.init();

    setHeader("Message-ID", getChangeMessageThreadId());

    add(RecipientType.TO, reviewers);
    add(RecipientType.CC, extraCC);
    rcptToAuthors(RecipientType.CC);
  }

  @Override
  protected void formatChange() throws EmailException {
    appendText(velocifyFile("NewChange.vm"));
  }

  public List<String> getReviewerNames() {
    if (reviewers.isEmpty()) {
      return null;
    }
    List<String> names = new ArrayList<String>();
    for (Account.Id id : reviewers) {
      names.add(getNameFor(id));
    }
    return names;
  }

  public String getSshHost() {
    final List<HostKey> hostKeys = sshInfo.getHostKeys();
    if (hostKeys.isEmpty()) {
      return null;
    }

    final String host = hostKeys.get(0).getHost();
    if (host.startsWith("*:")) {
      return getGerritHost() + host.substring(1);
    }
    return host;
  }

  /** Show patch set as unified difference.  */
  public String getUnifiedDiff() {
    StringBuilder detail = new StringBuilder();
    if (patchSet != null) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      DiffFormatter df = new DiffFormatter(out);
      db = openRepository();
      reader = db.newObjectReader();
      PatchList patchList = getPatchList();
      for (PatchListEntry p : patchList.getPatches()) {
        if (Patch.COMMIT_MSG.equals(p.getNewName())) {
          continue;
        }
        detail.append("\n");
        List<String> headers = p.getHeaderLines();
        for (String hdr : headers) {
          detail.append(hdr);
        }
        try {
          String filename = p.getNewName();
          RawText aText = resolve(patchList.getOldId(), filename);
          RawText bText = resolve(patchList.getNewId(), filename);
          df.format(toEditList(p.getEdits()), aText, bText);
          detail.append(out.toString());
        } catch (IOException err) {

        }
      }
      detail.append("\n");
    }
    return detail.toString();
  }

  private Text resolve(final ObjectId objId, String path) throws IOException {
    Text text;
    try {
      final TreeWalk tw = find(objId, reader, path);
      ObjectId id;
      FileMode mode;

      if (tw != null) {
        id = tw.getObjectId(0);
        mode = tw.getFileMode(0);
      } else {
        id = ObjectId.zeroId();
        mode = FileMode.MISSING;
      }

      if (mode.getObjectType() == Constants.OBJ_BLOB) {
        text =  new Text(Text.asByteArray(db.open(id, Constants.OBJ_BLOB)));
      } else {
        text = Text.EMPTY;
      }
    }
    catch (IOException err) {
      throw new IOException("Cannot read " + objId.name() + ":" + path, err);
    }
    return text;
  }

  private TreeWalk find(final ObjectId objId, ObjectReader reader, String path) throws
    MissingObjectException, IncorrectObjectTypeException, CorruptObjectException, IOException {
    final RevWalk rw = new RevWalk(reader);
    final RevTree tree = rw.parseTree(objId);
    return TreeWalk.forPath(reader, path, tree);
  }

  private EditList toEditList(List<Edit> edits) {
    EditList editList = new EditList(edits.size());
    for (int i = 0; i < edits.size(); ++i) {
      editList.add(edits.get(i));
    }
    return editList;
  }

  private Repository openRepository() {
    try {
      return args.server.openRepository(change.getProject());
    } catch (RepositoryNotFoundException e) {
      return null;
    }
  }
}
