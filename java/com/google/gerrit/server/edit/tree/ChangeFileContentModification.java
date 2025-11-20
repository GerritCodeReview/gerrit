// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.edit.tree;

import static com.google.gerrit.entities.Patch.FileMode.EXECUTABLE_FILE;
import static com.google.gerrit.entities.Patch.FileMode.GITLINK;
import static com.google.gerrit.entities.Patch.FileMode.REGULAR_FILE;
import static com.google.gerrit.entities.Patch.FileMode.SYMLINK;
import static java.util.Objects.requireNonNull;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteStreams;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.restapi.RawInput;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.InvalidObjectIdException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;

/** A {@code TreeModification} which changes the content of a file. */
public class ChangeFileContentModification implements TreeModification {
  private final String filePath;
  private final RawInput newContent;
  private final Integer newGitFileMode;

  public ChangeFileContentModification(String filePath, RawInput newContent) {
    this.filePath = filePath;
    this.newContent = requireNonNull(newContent, "new content required");
    this.newGitFileMode = null;
  }

  public ChangeFileContentModification(
      String filePath, RawInput newContent, @Nullable Integer newGitFileMode) {
    this.filePath = filePath;
    this.newContent = requireNonNull(newContent, "new content required");
    this.newGitFileMode = newGitFileMode;
  }

  @Override
  public List<DirCacheEditor.PathEdit> getPathEdits(
      Repository repository, ObjectId treeId, ImmutableList<? extends ObjectId> parents) {
    DirCacheEditor.PathEdit changeContentEdit =
        new ChangeContent(filePath, newContent, repository, newGitFileMode);
    return Collections.singletonList(changeContentEdit);
  }

  @Override
  public ImmutableSet<String> getFilePaths() {
    return ImmutableSet.of(filePath);
  }

  public RawInput getNewContent() {
    return newContent;
  }

  /** A {@code PathEdit} which changes the contents of a file. */
  private static class ChangeContent extends DirCacheEditor.PathEdit {
    private final String filePath;
    private final RawInput newContent;
    private final Repository repository;
    private final Integer newGitFileMode;

    ChangeContent(
        String filePath,
        RawInput newContent,
        Repository repository,
        @Nullable Integer newGitFileMode) {
      super(filePath);
      this.filePath = filePath;
      this.newContent = newContent;
      this.repository = repository;
      this.newGitFileMode = newGitFileMode;
    }

    private boolean isValidGitFileMode(int gitFileMode) {
      return gitFileMode == EXECUTABLE_FILE.getMode()
          || gitFileMode == REGULAR_FILE.getMode()
          || gitFileMode == GITLINK.getMode()
          || gitFileMode == SYMLINK.getMode();
    }

    @Override
    public void apply(DirCacheEntry dirCacheEntry) {
      try {
        if (newGitFileMode != null && newGitFileMode != 0) {
          if (!isValidGitFileMode(newGitFileMode)) {
            throw new InvalidFileModeException(
                String.format("GitFileMode %s is invalid", newGitFileMode), newGitFileMode);
          }

          FileMode fileMode = FileMode.fromBits(newGitFileMode);
          dirCacheEntry.setFileMode(fileMode);
        }
        if (dirCacheEntry.getFileMode() == FileMode.GITLINK) {
          dirCacheEntry.setLength(0);
          dirCacheEntry.setLastModified(Instant.EPOCH);

          try {
            ObjectId newObjectId = ObjectId.fromString(getNewContentBytes(), 0);
            dirCacheEntry.setObjectId(newObjectId);
          } catch (InvalidObjectIdException e) {
            throw new InvalidGitLinkException(
                String.format("The content for gitlink '%s' must be a valid SHA1.", filePath), e);
          }
        } else {
          if (dirCacheEntry.getRawMode() == 0) {
            dirCacheEntry.setFileMode(FileMode.REGULAR_FILE);
          }
          ObjectId newBlobObjectId = createNewBlobAndGetItsId();
          dirCacheEntry.setObjectId(newBlobObjectId);
        }
      } catch (IOException e) {
        String message =
            String.format("Could not change the content of %s", dirCacheEntry.getPathString());
        throw new IllegalStateException(message, e);
      }
    }

    private ObjectId createNewBlobAndGetItsId() throws IOException {
      try (ObjectInserter objectInserter = repository.newObjectInserter()) {
        ObjectId blobObjectId = createNewBlobAndGetItsId(objectInserter);
        objectInserter.flush();
        return blobObjectId;
      }
    }

    private ObjectId createNewBlobAndGetItsId(ObjectInserter objectInserter) throws IOException {
      long contentLength = newContent.getContentLength();
      if (contentLength < 0) {
        return objectInserter.insert(OBJ_BLOB, getNewContentBytes());
      }
      try {
        InputStream contentInputStream = newContent.getInputStream();
        return objectInserter.insert(OBJ_BLOB, contentLength, contentInputStream);
      } catch (EOFException e) {
        if (e.getMessage().equals(JGitText.get().shortReadOfBlock)) {
          throw new BadContentLengthException(
              String.format(
                  "The provided content length %s for file %s doesn't match with the length of the"
                      + " provided content",
                  contentLength, filePath),
              e);
        }
        throw e;
      }
    }

    private byte[] getNewContentBytes() throws IOException {
      return ByteStreams.toByteArray(newContent.getInputStream());
    }
  }
}
