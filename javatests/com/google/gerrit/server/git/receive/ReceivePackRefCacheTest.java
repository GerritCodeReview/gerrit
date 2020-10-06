// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.server.git.receive;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.RefNames;
import java.util.Map;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.junit.Test;

/** Tests for {@link ReceivePackRefCache}. */
public class ReceivePackRefCacheTest {

  @Test
  public void noCache_prefixDelegatesToRefDb() throws Exception {
    Ref ref = newRef("refs/changes/01/1/1", "badc0feebadc0feebadc0feebadc0feebadc0fee");
    RefDatabase mockRefDb = mock(RefDatabase.class);
    ReceivePackRefCache cache = ReceivePackRefCache.noCache(mockRefDb);
    when(mockRefDb.getRefsByPrefix(RefNames.REFS_HEADS)).thenReturn(ImmutableList.of(ref));

    assertThat(cache.byPrefix(RefNames.REFS_HEADS)).containsExactly(ref);
    verify(mockRefDb).getRefsByPrefix(RefNames.REFS_HEADS);
    verifyNoMoreInteractions(mockRefDb);
  }

  @Test
  public void noCache_exactRefDelegatesToRefDb() throws Exception {
    Ref ref = newRef("refs/changes/01/1/1", "badc0feebadc0feebadc0feebadc0feebadc0fee");
    RefDatabase mockRefDb = mock(RefDatabase.class);
    ReceivePackRefCache cache = ReceivePackRefCache.noCache(mockRefDb);
    when(mockRefDb.exactRef("refs/heads/master")).thenReturn(ref);

    assertThat(cache.exactRef("refs/heads/master")).isEqualTo(ref);
    verify(mockRefDb).exactRef("refs/heads/master");
    verifyNoMoreInteractions(mockRefDb);
  }

  @Test
  public void noCache_tipsFromObjectIdDelegatesToRefDb() throws Exception {
    Ref refBla = newRef("refs/bla", "badc0feebadc0feebadc0feebadc0feebadc0fee");
    String patchSetRef = RefNames.REFS_CHANGES + "01/1/1";
    Ref patchSet = newRef(patchSetRef, "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef");

    RefDatabase mockRefDb = mock(RefDatabase.class);
    ReceivePackRefCache cache = ReceivePackRefCache.noCache(mockRefDb);
    when(mockRefDb.getTipsWithSha1(ObjectId.zeroId()))
        .thenReturn(ImmutableSet.of(refBla, patchSet));

    assertThat(cache.patchSetIdsFromObjectId(ObjectId.zeroId()))
        .containsExactly(PatchSet.Id.fromRef(patchSetRef));
    verify(mockRefDb).getTipsWithSha1(ObjectId.zeroId());
    verifyNoMoreInteractions(mockRefDb);
  }

  @Test
  public void advertisedRefs_prefixScans() throws Exception {
    Ref refBla =
        new ObjectIdRef.Unpeeled(
            Ref.Storage.NEW,
            "refs/bla/1",
            ObjectId.fromString("badc0feebadc0feebadc0feebadc0feebadc0fee"),
            1);
    ReceivePackRefCache cache =
        ReceivePackRefCache.withAdvertisedRefs(() -> ImmutableMap.of(refBla.getName(), refBla));

    assertThat(cache.byPrefix("refs/bla")).containsExactly(refBla);
  }

  @Test
  public void advertisedRefs_prefixScansChangeId() throws Exception {
    Map<String, Ref> refs = setupTwoChanges();
    ReceivePackRefCache cache = ReceivePackRefCache.withAdvertisedRefs(() -> refs);

    assertThat(cache.byPrefix(RefNames.changeRefPrefix(Change.id(1))))
        .containsExactly(refs.get("refs/changes/01/1/1"));
  }

  @Test
  public void advertisedRefs_exactRef() throws Exception {
    Map<String, Ref> refs = setupTwoChanges();
    ReceivePackRefCache cache = ReceivePackRefCache.withAdvertisedRefs(() -> refs);

    assertThat(cache.exactRef("refs/changes/01/1/1")).isEqualTo(refs.get("refs/changes/01/1/1"));
  }

  @Test
  public void advertisedRefs_patchSetIdsFromObjectId() throws Exception {
    Map<String, Ref> refs = setupTwoChanges();
    ReceivePackRefCache cache = ReceivePackRefCache.withAdvertisedRefs(() -> refs);

    assertThat(
            cache.patchSetIdsFromObjectId(
                ObjectId.fromString("badc0feebadc0feebadc0feebadc0feebadc0fee")))
        .containsExactly(PatchSet.Id.fromRef("refs/changes/01/1/1"));
  }

  private static Ref newRef(String name, String sha1) {
    return new ObjectIdRef.Unpeeled(Ref.Storage.NEW, name, ObjectId.fromString(sha1), 1);
  }

  private Map<String, Ref> setupTwoChanges() {
    Ref ref1 = newRef("refs/changes/01/1/1", "badc0feebadc0feebadc0feebadc0feebadc0fee");
    Ref ref2 = newRef("refs/changes/02/2/1", "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef");
    return ImmutableMap.of(ref1.getName(), ref1, ref2.getName(), ref2);
  }
}
