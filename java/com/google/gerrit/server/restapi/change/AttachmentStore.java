// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.server.restapi.change;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.update.context.RefUpdateContext;
import com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TreeFormatter;
import org.eclipse.jgit.treewalk.TreeWalk;

class AttachmentStore {
  static final int MAX_SIZE = 5 * 1024 * 1024;
  static final int MAX_IMAGES_PER_CHANGE = 20;

  private final GitRepositoryManager gitManager;

  @Inject
  AttachmentStore(GitRepositoryManager gitManager) {
    this.gitManager = gitManager;
  }

  String upload(ChangeResource change, byte[] bytes) throws IOException, BadRequestException {
    if (imageType(bytes) == null) {
      throw new BadRequestException("Only PNG, JPEG, GIF and WebP images are supported");
    }
    try (Repository repo = gitManager.openRepository(change.getProject());
        ObjectInserter inserter = repo.newObjectInserter()) {
      ObjectId blob = inserter.idFor(Constants.OBJ_BLOB, bytes);
      boolean inserted = false;
      String refName = RefNames.refsAttachmentsPrefix(change.getId()) + "meta";
      for (int attempt = 0; attempt < 5; attempt++) {
        Ref oldRef = repo.exactRef(refName);
        Map<String, ObjectId> images = new TreeMap<>();
        if (oldRef != null) {
          try (TreeWalk walk = new TreeWalk(repo)) {
            walk.addTree(oldRef.getObjectId());
            while (walk.next()) {
              images.put(walk.getPathString(), walk.getObjectId(0));
            }
          }
        }
        if (images.containsKey(blob.name())) {
          return blob.name();
        }
        if (images.size() >= MAX_IMAGES_PER_CHANGE) {
          throw new BadRequestException("Too many attachments on this change");
        }
        if (!inserted) {
          inserter.insert(Constants.OBJ_BLOB, bytes);
          inserter.flush();
          inserted = true;
        }
        images.put(blob.name(), blob);
        TreeFormatter tree = new TreeFormatter();
        for (Map.Entry<String, ObjectId> image : images.entrySet()) {
          tree.append(image.getKey(), FileMode.REGULAR_FILE, image.getValue());
        }
        ObjectId newTree = inserter.insert(tree);
        inserter.flush();
        RefUpdate update = repo.updateRef(refName);
        update.setExpectedOldObjectId(oldRef == null ? ObjectId.zeroId() : oldRef.getObjectId());
        update.setNewObjectId(newTree);
        RefUpdate.Result result;
        try (RefUpdateContext ctx = RefUpdateContext.open(RefUpdateType.CHANGE_MODIFICATION)) {
          result = update.forceUpdate();
        }
        if (result == RefUpdate.Result.NEW || result == RefUpdate.Result.FORCED) {
          return blob.name();
        }
        if (result != RefUpdate.Result.LOCK_FAILURE && result != RefUpdate.Result.REJECTED) {
          throw new IOException("Cannot store image attachment: " + result);
        }
      }
      throw new IOException("Concurrent image uploads prevented this upload");
    }
  }

  byte[] read(ChangeResource change, String id) throws IOException, ResourceNotFoundException {
    try (Repository repo = gitManager.openRepository(change.getProject())) {
      Ref ref = repo.exactRef(RefNames.refsAttachmentsPrefix(change.getId()) + "meta");
      if (ref == null) {
        throw new ResourceNotFoundException("Attachment not found");
      }
      try (TreeWalk image = TreeWalk.forPath(repo, id, ref.getObjectId())) {
        if (image == null || !image.getObjectId(0).name().equals(id)) {
          throw new ResourceNotFoundException("Attachment not found");
        }
        return repo.open(image.getObjectId(0), Constants.OBJ_BLOB).getBytes(MAX_SIZE + 1);
      }
    }
  }

  static @Nullable String imageType(byte[] bytes) {
    if (bytes.length >= 8
        && Arrays.equals(
            Arrays.copyOf(bytes, 8),
            new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a})) {
      return "image/png";
    }
    if (bytes.length >= 3
        && (bytes[0] & 0xff) == 0xff
        && (bytes[1] & 0xff) == 0xd8
        && (bytes[2] & 0xff) == 0xff) {
      return "image/jpeg";
    }
    if (bytes.length >= 6
        && (new String(bytes, 0, 6, StandardCharsets.US_ASCII).equals("GIF87a")
            || new String(bytes, 0, 6, StandardCharsets.US_ASCII).equals("GIF89a"))) {
      return "image/gif";
    }
    if (bytes.length >= 12
        && new String(bytes, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
        && new String(bytes, 8, 4, StandardCharsets.US_ASCII).equals("WEBP")) {
      return "image/webp";
    }
    return null;
  }
}
