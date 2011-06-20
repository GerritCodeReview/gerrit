// Copyright (C) 2011 The Android Open Source Project
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
// limitations under the License.package com.google.gerrit.server.git;

package com.google.gerrit.server.git;

import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.server.cache.Cache;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.cache.EntryCreator;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class GitTagCacheImpl implements GitTagCache {
  private static final Logger log = LoggerFactory
      .getLogger(GitTagCacheImpl.class);
  private static final String CACHE_NAME = "gittags";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        final TypeLiteral<Cache<Repository, HashMap<Ref, List<Ref>>>> type =
            new TypeLiteral<Cache<Repository, HashMap<Ref, List<Ref>>>>() {};
        core(type, CACHE_NAME).populateWith(Loader.class);

        bind(GitTagCache.class).to(GitTagCacheImpl.class);
      }
    };
  }

  private final Cache<Repository, HashMap<Ref, List<Ref>>> cache;

  @Inject
  public GitTagCacheImpl(
      @Named(CACHE_NAME) final Cache<Repository, HashMap<Ref, List<Ref>>> cache) {
    this.cache = cache;
  }


  @Override
  public HashMap<Ref, List<Ref>> get(Repository gitRepo) {
    return cache.get(gitRepo);
  }

  @Override
  public void evict(Repository gitRepo) {
    cache.remove(gitRepo);
  }

  static class Loader extends EntryCreator<Repository, HashMap<Ref, List<Ref>>> {
    private RevObject peelTag(final RevWalk rw, final Ref tag)
        throws MissingObjectException, IOException {
      // Try to use the peeled object identity, because it may be
      // able to save us from parsing the tag object itself.
      //
      ObjectId target = tag.getPeeledObjectId();
      if (target == null) {
        target = tag.getObjectId();
      }
      RevObject o = rw.parseAny(target);
      while (o instanceof RevTag) {
        o = ((RevTag) o).getObject();
        rw.parseHeaders(o);
      }
      return o;
    }

    @Override
    public HashMap<Ref, List<Ref>> createEntry(Repository gitRepo) {
      HashMap<Ref, List<Ref>> result = new HashMap<Ref, List<Ref>>();
      final RevWalk rw = new RevWalk(gitRepo);

      try {
        Map<String, Ref> tags = gitRepo.getTags();
        Map<String, Ref> refs = gitRepo.getAllRefs();

        for (Ref tag : tags.values()) {
          RevObject tagPointee = peelTag(rw, tag);
          ArrayList<Ref> refsReach = new ArrayList<Ref>();

          for (Ref ref : refs.values()) {
            if (!ref.getName().startsWith(Constants.R_TAGS)
                && !PatchSet.isRef(ref.getName())) {
              RevCommit p = rw.parseCommit(ref.getObjectId());
              rw.markStart(p);

              do {
                if (p.equals(tagPointee)) {
                  while (ref.isSymbolic()) {
                    ref = ref.getTarget();
                  }
                  refsReach.add(ref);
                  break;
                }
                p = rw.next();
              } while (p != null);
            }
          }

          result.put(tag, refsReach);
        }
      } catch (MissingObjectException e) {
        log.error(e.toString());
      } catch (IOException e) {
        log.error(e.toString());
      } finally {
        rw.release();
      }

      return result;
    }
  }
}
