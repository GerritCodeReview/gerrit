package com.google.gerrit.server.patch;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.server.util.time.TimeUtil;
import java.io.IOException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TreeFormatter;
import org.eclipse.jgit.revwalk.RevWalk;

public class CommitUtil {
  private CommitUtil() {}

  static class FileEntity {
    String name;
    String content;
    FileType type;

    enum FileType {
      REGULAR,
      SYMLINK
    }

    FileEntity(String name, String content) {
      this(name, content, FileType.REGULAR);
    }

    FileEntity(String name, String content, FileType type) {
      this.name = name;
      this.content = content;
      this.type = type;
    }
  }

  static ObjectId createCommit(
      Repository repo, @Nullable ObjectId parentCommit, ImmutableList<FileEntity> fileEntities)
      throws IOException {
    ObjectId treeId = createTree(repo, fileEntities);
    return parentCommit == null
        ? createCommitInRepo(repo, treeId)
        : createCommitInRepo(repo, treeId, parentCommit);
  }

  static ObjectId createCommitInRepo(Repository repo, ObjectId treeId, ObjectId... parents)
      throws IOException {
    try (ObjectInserter oi = repo.newObjectInserter()) {
      PersonIdent committer =
          new PersonIdent(new PersonIdent("Foo Bar", "foo.bar@baz.com"), TimeUtil.now());
      CommitBuilder cb = new CommitBuilder();
      cb.setTreeId(treeId);
      cb.setCommitter(committer);
      cb.setAuthor(committer);
      cb.setMessage("Test commit");
      if (parents != null && parents.length > 0) {
        cb.setParentIds(parents);
      }
      ObjectId commitId = oi.insert(cb);
      oi.flush();
      oi.close();
      return commitId;
    }
  }

  static ObjectId createTree(Repository repo, ImmutableList<FileEntity> fileEntities)
      throws IOException {
    try (ObjectInserter oi = repo.newObjectInserter();
        ObjectReader reader = repo.newObjectReader();
        RevWalk rw = new RevWalk(reader); ) {
      TreeFormatter formatter = new TreeFormatter();
      for (FileEntity fileEntity : fileEntities) {
        String fileName = fileEntity.name;
        String fileContent = fileEntity.content;
        ObjectId fileObjId = createBlob(repo, fileContent);
        if (fileEntity.type.equals(FileEntity.FileType.REGULAR)) {
          formatter.append(fileName, rw.lookupBlob(fileObjId));
        } else {
          formatter.append(fileName, FileMode.SYMLINK, fileObjId);
        }
      }
      ObjectId treeId = oi.insert(formatter);
      oi.flush();
      oi.close();
      return treeId;
    }
  }

  static ObjectId createBlob(Repository repo, String content) throws IOException {
    try (ObjectInserter oi = repo.newObjectInserter()) {
      ObjectId blobId = oi.insert(Constants.OBJ_BLOB, content.getBytes(UTF_8));
      oi.flush();
      oi.close();
      return blobId;
    }
  }

  static ObjectId createMergeCommit(
      Repository repo, ImmutableList<FileEntity> fileEntities, ObjectId parent1, ObjectId parent2)
      throws IOException {
    ObjectId treeId = createTree(repo, fileEntities);
    return createCommitInRepo(repo, treeId, parent1, parent2);
  }
}
