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

package com.google.gerrit.server.git;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import org.eclipse.jgit.errors.PackProtocolException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.AdvertiseRefsHook;
import org.eclipse.jgit.transport.BaseReceivePack;
import org.eclipse.jgit.transport.ServiceMayNotContinueException;
import org.eclipse.jgit.transport.UploadPack;
import org.eclipse.jgit.transport.UploadPack.ReachableCommitRequestValidator;
import org.eclipse.jgit.transport.UploadPack.RequestValidator;

/**
 * Hook to omit Gerrit-specific refs during upload-pack ref advertisement.
 *
 * <p>If enabled by configuration, omits all change, automerge, etc. refs from advertisements. In
 * many repos, these vastly outnumber the usual heads and tags, sometimes by a factor of 100 or
 * more.
 *
 * <p>Implemented as an {@link AdvertiseRefsHook} instead of a {@link
 * org.eclipse.jgit.transport.RefFilter RefFilter} because the latter only runs after all {@code
 * AdvertiseRefsHook}s. This hook must run <em>before</em> the {@link VisibleRefFilter}, in order to
 * avoid unnecessary ACL checks on refs that won't be advertised.
 *
 * <p>A ref being <em>omitted</em> does not imply one way or the other whether the user has access
 * to see that ref, so this class also contains custom logic for determining whether a want that was
 * not present in the ref advertisement is valid. (The term <em>omit</em> is intentionally chosen to
 * avoid implying anything about permissions; a term like <em>omit</em> would be ambiguous.)
 */
public class OmitGerritRefsHook implements AdvertiseRefsHook, RequestValidator {
  private static final ImmutableList<String> OMIT_PREFIXES =
      ImmutableList.of(
          RefNames.REFS_CACHE_AUTOMERGE,
          RefNames.REFS_CHANGES,
          RefNames.REFS_DRAFT_COMMENTS,
          RefNames.REFS_SEQUENCES,
          RefNames.REFS_STARRED_CHANGES,
          RefNames.REFS_USERS);

  private static final Predicate<Ref> SHOULD_KEEP =
      r -> OMIT_PREFIXES.stream().noneMatch(p -> r.getName().startsWith(p));

  public interface Factory {
    OmitGerritRefsHook create(VisibleRefFilter visibleRefFilter);
  }

  private final RefAdvertisementStrategy strategy;
  private final VisibleRefFilter visibleRefFilter;

  private ListMultimap<ObjectId, String> omittedById;

  @Inject
  OmitGerritRefsHook(@GerritServerConfig Config cfg, @Assisted VisibleRefFilter visibleRefFilter) {
    this.strategy = RefAdvertisementStrategy.get(cfg);
    this.visibleRefFilter = visibleRefFilter;
  }

  @Override
  public void advertiseRefs(UploadPack up) throws ServiceMayNotContinueException {
    if (!enabled()) {
      return;
    }
    checkState(visibleRefFilter.getShowMetadata());
    omittedById = MultimapBuilder.hashKeys().arrayListValues(1).build();
    Map<String, Ref> toAdvertise = new HashMap<>();
    for (Ref r : HookUtil.ensureAllRefsAdvertised(up).values()) {
      String name = r.getName();
      if (SHOULD_KEEP.test(r)) {
        toAdvertise.put(name, r);
      } else {
        ObjectId id = r.getObjectId();
        if (id != null) {
          omittedById.put(id, name);
        }
        ObjectId peeled = r.getPeeledObjectId();
        if (peeled != null) {
          omittedById.put(peeled, name);
        }
      }
    }
    up.setAdvertisedRefs(toAdvertise);
  }

  private boolean enabled() {
    switch (strategy) {
      case ALL:
        return false;
      case OMIT_GERRIT:
        return true;
      default:
        throw new IllegalStateException();
    }
  }

  @Override
  public void advertiseRefs(BaseReceivePack receivePack) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void checkWants(UploadPack up, List<ObjectId> wants)
      throws PackProtocolException, IOException {
    if (omittedById == null) {
      return;
    }
    Set<ObjectId> nonTipWants =
        trimToNonTipWants(
            wants,
            omittedById,
            refs -> up.getRepository().getRefDatabase().exactRef(refs.toArray(new String[0])),
            visibleRefFilter::filter);
    if (!nonTipWants.isEmpty()) {
      // At this point, any remaining wants do not correspond to tips of refs that we can prove the
      // user has access to. They might, however, be reachable from some refs that the user has
      // access to.
      //
      // Note that ReachableCommitRequestValidator only considers the actual set of refs advertised,
      // i.e. after filtering out invisible refs. This means there is a small chance that the user
      // wants a ref that is reachable from a ref that was not advertised, which they are asking for
      // due to a race between non-bidi ls-remote and fetch.
      //
      // This race should never happen for the most important class of omitted refs, patch set refs,
      // because those are immutable. e other omitted refs are mostly NoteDb refs, and we just
      // hope that the user doesn't run into problems due to an update race between non-bidi ref
      // advertisement and fetch. The chances of losing a race are also lowered by the fact that
      // they are most likely to be only fetching one ref at a time.
      //
      // TODO(dborowitz): It might be reasonable to throw immediately in the bidi case, instead of
      // delegating. But the current implementation of UploadPack advertises
      // allow-reachable-sha1-in-want unconditionally if we provide a custom RequestValidator, so
      // the current behavior here is most closely aligned with that.
      new ReachableCommitRequestValidator().checkWants(up, new ArrayList<>(nonTipWants));
    }
  }

  @FunctionalInterface
  interface RefLookup<R> {
    Map<String, R> lookup(Collection<String> names) throws IOException;
  }

  static <R> Set<ObjectId> trimToNonTipWants(
      List<ObjectId> wants,
      ListMultimap<ObjectId, String> omittedById,
      RefLookup<R> refLookup,
      UnaryOperator<Map<String, R>> filter)
      throws IOException {
    Set<ObjectId> toValidate = new HashSet<>(wants);

    // Multimap of ref names -> wants asking for exactly that ref name. To get to this point, a want
    // must not have been in the set of actually advertised refs.
    ListMultimap<String, ObjectId> omittedRefToWants =
        MultimapBuilder.hashKeys(wants.size()).arrayListValues(1).build();
    for (ObjectId want : wants) {
      for (String ref : omittedById.get(want)) {
        omittedRefToWants.put(ref, want);
      }
    }

    // For each refname corresponding to a want, see if that ref is visible according to
    // VisibleRefFilter, and if so, remove all corresponding wants from the remaining set of wants
    // to validate. This catches all wants that _would have been_ advertised by VisibleRefFilter,
    // except that they were filtered out ahead of time by this hook.
    Set<String> refNamesToCheck = omittedRefToWants.keySet();
    Map<String, R> refsToCheck = refLookup.lookup(refNamesToCheck);
    Set<String> visible = filter.apply(refsToCheck).keySet();
    visible.forEach(r -> toValidate.removeAll(omittedRefToWants.get(r)));
    return toValidate;
  }
}
