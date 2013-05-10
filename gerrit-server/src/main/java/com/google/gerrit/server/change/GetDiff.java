// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.change;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gerrit.common.data.PatchScript;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.prettify.common.SparseFileContent;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountDiffPreference;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.patch.PatchScriptFactory;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.git.LargeObjectException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.diff.Edit;
import org.kohsuke.args4j.Option;

import java.util.List;
import java.util.Map;

public class GetDiff implements RestReadView<FileResource> {
  private final PatchScriptFactory.Factory patchScriptFactoryFactory;
  private final Provider<Revisions> revisions;

  @Option(name = "--base", metaVar = "revision-id")
  String base;

  @Inject
  GetDiff(PatchScriptFactory.Factory patchScriptFactoryFactory,
      Provider<Revisions> revisions) {
    this.patchScriptFactoryFactory = patchScriptFactoryFactory;
    this.revisions = revisions;
  }

  @Override
  public Object apply(FileResource resource)
      throws OrmException, NoSuchChangeException, LargeObjectException, ResourceNotFoundException {
    PatchSet.Id basePatchSet = null;
    if (base != null) {
      RevisionResource baseResource = revisions.get().parse(
          resource.getRevision().getChangeResource(), IdString.fromDecoded(base));
      basePatchSet = baseResource.getPatchSet().getId();
    }

    PatchScript ps = patchScriptFactoryFactory.create(
        resource.getRevision().getControl(),
        resource.getPatchKey().getFileName(),
        basePatchSet,
        resource.getPatchKey().getParentKey(),
        AccountDiffPreference.createDefault(new Account.Id(0)))
          .call();

    Content content = new Content(ps);

    for (Edit edit : ps.getEdits()) {
      if (edit.getType() == Edit.Type.EMPTY) {
        continue;
      }
      content.addAB(edit.getBeginA());

      checkState(content.nextA == edit.getBeginA(),
          "nextA = %d; want %d", content.nextA, edit.getBeginA());
      checkState(content.nextB == edit.getBeginB(),
          "nextB = %d; want %d", content.nextB, edit.getBeginB());
      switch (edit.getType()) {
        case DELETE:
          content.addA(edit.getEndA());
          break;
        case INSERT:
          content.addB(edit.getEndB());
          break;
        case REPLACE:
          content.addReplace(edit.getEndA(), edit.getEndB());
          // TODO: include intra line diffs
          break;
        case EMPTY:
        default:
          throw new IllegalStateException();
      }
    }
    content.addAB(ps.getA().size());

    Map<String, Object> out = Maps.newHashMap();
    //out.put("a", ImmutableMap.of("name", ps.getOldName(), "mode", ps.getFileModeA()));
    //out.put("b", ImmutableMap.of("name", ps.getNewName(), "mode", ps.getFileModeB()));
    out.put("content", content.lines);
    return out;
  }

  private static class Content {
    final List<ContentEntry> lines;
    final SparseFileContent fileA;
    final SparseFileContent fileB;

    int nextA;
    int nextB;

    Content(PatchScript ps) {
      lines = Lists.newArrayListWithExpectedSize(ps.getEdits().size() + 2);
      fileA = ps.getA();
      fileB = ps.getB();
    }

    void addAB(int end) {
      end = Math.min(end, fileA.size());
      if (nextA >= end) {
        return;
      }
      nextB += end - nextA;

      while (nextA < end) {
        if (fileA.contains(nextA)) {
          ContentEntry e = entry();
          e.ab = Lists.newArrayListWithCapacity(end - nextA);
          for (int i = nextA; i == nextA && i < end; i = fileA.next(i), nextA++) {
            e.ab.add(fileA.get(i));
          }
        } else {
          int endRegion = Math.min(end, (nextA == 0) ? fileA.first() : fileA.next(nextA));
          ContentEntry e = entry();
          e.skip = endRegion - nextA;
          nextA = endRegion;
        }
      }
    }

    void addA(int end) {
      int len = end - nextA;
      if (len <= 0) {
        return;
      }

      ContentEntry e = entry();
      e.a = Lists.newArrayListWithCapacity(len);
      for (; nextA < end; nextA++) {
        e.a.add(fileA.get(nextA));
      }
    }

    void addB(int end) {
      int len = end - nextB;
      if (len <= 0) {
        return;
      }
      ContentEntry e = entry();
      e.b = Lists.newArrayListWithCapacity(len);
      for (; nextB < end; nextB++) {
        e.b.add(fileB.get(nextB));
      }
    }

    public void addReplace(int endA, int endB) {
      int lenA = endA - nextA;
      int lenB = endB - nextB;
      checkState(lenA > 0 && lenB > 0);

      ContentEntry e = entry();
      e.a = Lists.newArrayListWithCapacity(endA);
      e.b = Lists.newArrayListWithCapacity(endB);

      for (; nextA < endA; nextA++) {
        e.a.add(fileA.get(nextA));
      }
      for (; nextB < endB; nextB++) {
        e.b.add(fileB.get(nextB));
      }
    }

    ContentEntry entry() {
      ContentEntry e = new ContentEntry();
      lines.add(e);
      return e;
    }
  }

  static final class ContentEntry {
    List<String> ab;
    List<String> a;
    List<String> b;
    Integer skip;
  }
}
