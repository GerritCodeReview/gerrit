// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.server.approval;

import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.converter.PatchSetApprovalProtoConverter;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.proto.Entities;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.cache.proto.Cache;
import com.google.gerrit.server.cache.serialize.ObjectIdCacheSerializer;
import com.google.gerrit.server.cache.serialize.ProtobufSerializer;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.ByteString;
import java.util.concurrent.ExecutionException;

/** @see ApprovalCache */
public class ApprovalCacheImpl implements ApprovalCache {
  private static final String CACHE_NAME = "approvals";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        bind(ApprovalCache.class).to(ApprovalCacheImpl.class);
        persist(
                CACHE_NAME,
                Cache.PatchSetApprovalsKeyProto.class,
                Cache.AllPatchSetApprovalsProto.class)
            .version(2)
            .loader(Loader.class)
            .keySerializer(new ProtobufSerializer<>(Cache.PatchSetApprovalsKeyProto.parser()))
            .valueSerializer(new ProtobufSerializer<>(Cache.AllPatchSetApprovalsProto.parser()));
      }
    };
  }

  private final LoadingCache<Cache.PatchSetApprovalsKeyProto, Cache.AllPatchSetApprovalsProto>
      cache;

  @Inject
  ApprovalCacheImpl(
      @Named(CACHE_NAME)
          LoadingCache<Cache.PatchSetApprovalsKeyProto, Cache.AllPatchSetApprovalsProto> cache) {
    this.cache = cache;
  }

  @Override
  public Iterable<PatchSetApproval> get(ChangeNotes notes, PatchSet.Id psId) {
    try {
      return fromProto(
          cache.get(
              Cache.PatchSetApprovalsKeyProto.newBuilder()
                  .setChangeId(notes.getChangeId().get())
                  .setPatchSetId(psId.get())
                  .setProject(notes.getProjectName().get())
                  .setId(
                      ByteString.copyFrom(
                          ObjectIdCacheSerializer.INSTANCE.serialize(notes.getMetaId())))
                  .build()));
    } catch (ExecutionException e) {
      throw new StorageException(e);
    }
  }

  @Singleton
  static class Loader
      extends CacheLoader<Cache.PatchSetApprovalsKeyProto, Cache.AllPatchSetApprovalsProto> {
    private final ApprovalInference approvalInference;
    private final ChangeNotes.Factory changeNotesFactory;

    @Inject
    Loader(ApprovalInference approvalInference, ChangeNotes.Factory changeNotesFactory) {
      this.approvalInference = approvalInference;
      this.changeNotesFactory = changeNotesFactory;
    }

    @Override
    public Cache.AllPatchSetApprovalsProto load(Cache.PatchSetApprovalsKeyProto key)
        throws Exception {
      Change.Id changeId = Change.id(key.getChangeId());
      return toProto(
          approvalInference.forPatchSet(
              changeNotesFactory.createChecked(
                  Project.nameKey(key.getProject()),
                  changeId,
                  ObjectIdCacheSerializer.INSTANCE.deserialize(key.getId().toByteArray())),
              PatchSet.id(changeId, key.getPatchSetId()),
              null
              /* revWalk= */ ,
              null
              /* repoConfig= */ ));
    }
  }

  private static Iterable<PatchSetApproval> fromProto(Cache.AllPatchSetApprovalsProto proto) {
    ImmutableList.Builder<PatchSetApproval> builder = ImmutableList.builder();
    for (Entities.PatchSetApproval psa : proto.getApprovalList()) {
      builder.add(PatchSetApprovalProtoConverter.INSTANCE.fromProto(psa));
    }
    return builder.build();
  }

  private static Cache.AllPatchSetApprovalsProto toProto(Iterable<PatchSetApproval> autoValue) {
    Cache.AllPatchSetApprovalsProto.Builder builder = Cache.AllPatchSetApprovalsProto.newBuilder();
    for (PatchSetApproval psa : autoValue) {
      builder.addApproval(PatchSetApprovalProtoConverter.INSTANCE.toProto(psa));
    }
    return builder.build();
  }
}
