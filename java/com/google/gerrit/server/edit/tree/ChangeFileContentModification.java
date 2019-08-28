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

import static java.util.Objects.requireNonNull;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.common.io.ByteStreams;
import com.google.gerrit.extensions.restapi.RawInput;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.InvalidObjectIdException;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

/** A {@code TreeModification} which changes the content of a file. */
public class ChangeFileContentModification implements TreeModification {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final String filePath;
  private final RawInput newContent;

  public ChangeFileContentModification(String filePath, RawInput newContent) {
    this.filePath = filePath;
    this.newContent = requireNonNull(newContent, "new content required");
  }

  @Override
  public List<DirCacheEditor.PathEdit> getPathEdits(Repository repository, RevCommit baseCommit) {
    DirCacheEditor.PathEdit changeContentEdit = new ChangeContent(filePath, newContent, repository);
    return Collections.singletonList(changeContentEdit);
  }

  @Override
  public String getFilePath() {
    return filePath;
  }

  @VisibleForTesting
  RawInput getNewContent() {
    return newContent;
  }

  /** A {@code PathEdit} which changes the contents of a file. */
  private static class ChangeContent extends DirCacheEditor.PathEdit {

    private final RawInput newContent;
    private final Repository repository;

    ChangeContent(String filePath, RawInput newContent, Repository repository) {
      super(filePath);
      this.newContent = newContent;
      this.repository = repository;
    }

    @Override
    public void apply(DirCacheEntry dirCacheEntry) {
      try {
        if (dirCacheEntry.getFileMode() == FileMode.GITLINK) {
          dirCacheEntry.setLength(0);
          dirCacheEntry.setLastModified(0);
          ObjectId newObjectId = ObjectId.fromString(getNewContentBytes(), 0);
          dirCacheEntry.setObjectId(newObjectId);
        } else {
          if (dirCacheEntry.getRawMode() == 0) {
            dirCacheEntry.setFileMode(FileMode.REGULAR_FILE);
          }
          ObjectId newBlobObjectId = createNewBlobAndGetItsId();
          dirCacheEntry.setObjectId(newBlobObjectId);
        }
        // Previously, these two exceptions were swallowed. To improve the
        // situation, we log them now. However, we should think of a better
        // approach.
      } catch (IOException e) {
        String message =
            String.format("Could not change the content of %s", dirCacheEntry.getPathString());
        logger.atSevere().withCause(e).log(message);
      } catch (InvalidObjectIdException e) {
        logger.atSevere().withCause(e).log("Invalid object id in submodule link");
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
      InputStream contentInputStream = newContent.getInputStream();
      return objectInserter.insert(OBJ_BLOB, contentLength, contentInputStream);
    }

    private byte[] getNewContentBytes() throws IOException {
      return ByteStreams.toByteArray(newContent.getInputStream());
    }
  }
}
