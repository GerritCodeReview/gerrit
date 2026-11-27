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

import com.google.common.io.ByteStreams;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.DefaultInput;
import com.google.gerrit.extensions.restapi.RawInput;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestCollectionModifyView;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.group.GroupResolver;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Arrays;
import org.eclipse.jgit.lib.Config;

public class UploadAttachment
    implements RestCollectionModifyView<
        ChangeResource, Attachments.AttachmentResource, UploadAttachment.Input> {
  public static class Input {
    @DefaultInput public RawInput content;
  }

  public static class Info {
    public final String id;

    Info(String id) {
      this.id = id;
    }
  }

  private final Config config;
  private final GroupResolver groups;
  private final AttachmentStore store;

  @Inject
  UploadAttachment(@GerritServerConfig Config config, GroupResolver groups, AttachmentStore store) {
    this.config = config;
    this.groups = groups;
    this.store = store;
  }

  @Override
  public Response<Info> apply(ChangeResource change, Input input)
      throws AuthException, BadRequestException, IOException {
    if (!config.getBoolean("attachments", "enabled", false)
        || !change.getUser().isIdentifiedUser()) {
      throw new AuthException("Attachment uploads are not enabled for this user");
    }
    String[] allowedGroups = config.getStringList("attachments", null, "allowedGroup");
    if (allowedGroups.length > 0
        && Arrays.stream(allowedGroups)
            .map(groups::parseId)
            .noneMatch(
                group ->
                    group != null
                        && change.getUser().getEffectiveGroups().contains(group.getGroupUUID()))) {
      throw new AuthException("Attachment uploads are not enabled for this user");
    }
    if (input == null || input.content == null) {
      throw new BadRequestException("An image is required");
    }
    if (input.content.getContentLength() > AttachmentStore.MAX_SIZE) {
      throw new BadRequestException("Image exceeds the 5 MiB limit");
    }
    byte[] bytes;
    try (var stream = input.content.getInputStream()) {
      bytes = ByteStreams.toByteArray(ByteStreams.limit(stream, AttachmentStore.MAX_SIZE + 1));
    }
    if (bytes.length > AttachmentStore.MAX_SIZE) {
      throw new BadRequestException("Image exceeds the 5 MiB limit");
    }
    return Response.ok(new Info(store.upload(change, bytes)));
  }
}
