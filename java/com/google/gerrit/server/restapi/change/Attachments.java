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

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.server.change.ChangeResource;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;

@Singleton
public class Attachments
    implements ChildCollection<ChangeResource, Attachments.AttachmentResource> {
  public static final TypeLiteral<RestView<AttachmentResource>> ATTACHMENT_KIND =
      new TypeLiteral<>() {};

  public static class AttachmentResource implements RestResource {
    final ChangeResource change;
    final String id;

    AttachmentResource(ChangeResource change, String id) {
      this.change = change;
      this.id = id;
    }
  }

  private final DynamicMap<RestView<AttachmentResource>> views;

  @Inject
  Attachments(DynamicMap<RestView<AttachmentResource>> views) {
    this.views = views;
  }

  @Override
  public DynamicMap<RestView<AttachmentResource>> views() {
    return views;
  }

  @Override
  public RestReadView<ChangeResource> list() throws ResourceNotFoundException {
    throw new ResourceNotFoundException();
  }

  @Override
  public AttachmentResource parse(ChangeResource change, IdString id)
      throws ResourceNotFoundException {
    if (!id.get().matches("[0-9a-f]{40}")) {
      throw new ResourceNotFoundException(id);
    }
    return new AttachmentResource(change, id.get());
  }
}
