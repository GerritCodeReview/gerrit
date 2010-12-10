// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.server.git;

import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.Project.SubmitType;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.UnmergedPathException;
import org.eclipse.jgit.lib.BlobBasedConfig;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.FileNotFoundException;
import java.io.IOException;

public class ProjectConfig {
  private static final String FILE_NAME = "project.config";

  private static final String PROJECT = "project";
  private static final String KEY_NAME = "name";
  private static final String KEY_DESCRIPTION = "description";

  private static final String ACCESS = "access";
  private static final String KEY_INHERIT_FROM = "inheritFrom";

  private static final String RECEIVE = "receive";
  private static final String KEY_REQUIRE_SIGNED_OFF_BY = "requireSignedOffBy";
  private static final String KEY_REQUIRE_CHANGE_ID = "requireChangeId";
  private static final String KEY_REQUIRE_CONTRIBUTOR_AGREEMENT =
      "requireContributorAgreement";

  private static final String SUBMIT = "submit";
  private static final String KEY_ACTION = "action";
  private static final String KEY_MERGE_CONTENT = "mergeContent";

  public static ProjectConfig read(Repository db) throws IOException,
      ConfigInvalidException {
    final Ref ref = db.getRef(GitRepositoryManager.REF_CONFIG);
    final ObjectId srcId = ref != null ? ref.getObjectId() : null;
    Config rc;
    if (srcId != null) {
      try {
        rc = new BlobBasedConfig(null, db, srcId, FILE_NAME);
      } catch (FileNotFoundException notFound) {
        rc = new Config();
      }
    } else {
      rc = new Config();
    }
    return new ProjectConfig(srcId, rc);
  }

  private final ObjectId srcCommit;
  private final Config config;
  private final Project project;

  private ProjectConfig(final ObjectId srcObjectId, final Config rc) {
    srcCommit = srcObjectId;
    config = rc;
    project = new Project();

    Project p = project;
    p.setName(rc.getString(PROJECT, null, KEY_NAME));
    p.setDescription(rc.getString(PROJECT, null, KEY_DESCRIPTION));
    p.setParentName(rc.getString(ACCESS, null, KEY_INHERIT_FROM));

    p.setUseContributorAgreements(rc.getBoolean(RECEIVE, null, //
        KEY_REQUIRE_CONTRIBUTOR_AGREEMENT, false));
    p.setUseSignedOffBy(rc.getBoolean(RECEIVE, null, //
        KEY_REQUIRE_SIGNED_OFF_BY, false));
    p.setRequireChangeID(rc.getBoolean(RECEIVE, null, //
        KEY_REQUIRE_CHANGE_ID, false));

    p.setSubmitType(rc.getEnum(SUBMIT, null, KEY_ACTION,
        SubmitType.MERGE_IF_NECESSARY));
    p.setUseContentMerge(rc.getBoolean(SUBMIT, null, KEY_MERGE_CONTENT, false));
  }

  private void updateConfig() {
    Config rc = config;
    Project p = project;

    set(PROJECT, null, KEY_NAME, p.getName());
    set(PROJECT, null, KEY_DESCRIPTION, p.getDescription());
    set(ACCESS, null, KEY_INHERIT_FROM, p.getParentName());

    rc.setBoolean(RECEIVE, null, KEY_REQUIRE_CONTRIBUTOR_AGREEMENT, //
        p.isUseContributorAgreements());
    rc.setBoolean(RECEIVE, null, KEY_REQUIRE_SIGNED_OFF_BY, //
        p.isUseSignedOffBy());
    rc.setBoolean(RECEIVE, null, KEY_REQUIRE_CHANGE_ID, p.isRequireChangeID());

    rc.setEnum(SUBMIT, null, KEY_ACTION, p.getSubmitType());
    rc.setBoolean(SUBMIT, null, KEY_MERGE_CONTENT, p.isUseContentMerge());
  }

  private void set(String section, String subsection, String name, String value) {
    if (value != null)
      config.setString(section, subsection, name, value);
    else
      config.unset(section, subsection, name);
  }

  public Project getProject() {
    return project;
  }

  public boolean commit(CommitBuilder commit, Repository db) throws IOException {
    final ObjectReader reader = db.newObjectReader();
    final ObjectInserter inserter = db.newObjectInserter();
    try {
      final RevWalk rw = new RevWalk(reader);
      final RevTree src = srcCommit != null ? rw.parseTree(srcCommit) : null;
      final ObjectId res = writeTree(reader, inserter, src);

      if (res.equals(src)) {
        // If there are no changes to the content, don't create the commit.
        return true;
      }

      commit.setTreeId(res);
      if (srcCommit != null) {
        commit.setParentId(srcCommit);
      }
      if (commit.getMessage() == null || "".equals(commit.getMessage())) {
        commit.setMessage("Updated project configuration\n");
      }

      RefUpdate ru = db.updateRef(GitRepositoryManager.REF_CONFIG);
      if (srcCommit != null) {
        ru.setExpectedOldObjectId(srcCommit);
      } else {
        ru.setExpectedOldObjectId(ObjectId.zeroId());
      }
      ru.setNewObjectId(inserter.insert(commit));
      ru.disableRefLog();
      inserter.flush();

      switch (ru.update(rw)) {
        case NEW:
        case FAST_FORWARD:
          return true;

        case LOCK_FAILURE:
          return false;

        default:
          throw new IOException("Cannot update "
              + GitRepositoryManager.REF_CONFIG + " in " + db.getDirectory()
              + ": " + ru.getResult());
      }
    } finally {
      inserter.release();
      reader.release();
    }
  }

  private ObjectId writeTree(ObjectReader reader, ObjectInserter inserter,
      RevTree srcTree) throws IOException, MissingObjectException,
      IncorrectObjectTypeException, UnmergedPathException {
    updateConfig();

    final ObjectId blobId =
        inserter.insert(Constants.OBJ_BLOB, Constants.encode(config.toText()));

    DirCache dc = readTree(reader, srcTree);
    DirCacheEditor editor = dc.editor();
    editor.add(new PathEdit(FILE_NAME) {
      @Override
      public void apply(DirCacheEntry ent) {
        ent.setFileMode(FileMode.REGULAR_FILE);
        ent.setObjectId(blobId);
      }
    });
    editor.finish();
    return dc.writeTree(inserter);
  }

  private static DirCache readTree(ObjectReader reader, RevTree tree)
      throws IOException, MissingObjectException, IncorrectObjectTypeException {
    DirCache dc = DirCache.newInCore();
    if (tree != null) {
      DirCacheBuilder b = dc.builder();
      b.addTree(new byte[0], DirCacheEntry.STAGE_0, reader, tree);
      b.finish();
    }
    return dc;
  }
}
