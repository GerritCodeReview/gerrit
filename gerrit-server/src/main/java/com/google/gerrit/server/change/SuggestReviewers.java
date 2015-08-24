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

import com.google.common.base.Function;
import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.GroupBaseInfo;
import com.google.gerrit.extensions.common.SuggestedReviewerInfo;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountControl;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.account.AccountVisibility;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.GroupMembers;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gwtorm.server.OrmException;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.Config;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SuggestReviewers implements RestReadView<ChangeResource> {
  public static class Module extends AbstractModule {
    @Override
    protected void configure() {
      DynamicItem.itemOf(binder(), ReviewerAnnotator.class);
    }
  }

  public interface ReviewerAnnotator {
    public String getAnnotation(SuggestedReviewerInfo ri, ChangeControl cc);
  }

  private static final String MAX_SUFFIX = "\u9fa5";
  private static final int DEFAULT_MAX_SUGGESTED = 10;
  private static final int DEFAULT_MAX_MATCHES = 100;
  private static final Ordering<SuggestedReviewerInfo> ORDERING =
      Ordering.natural().onResultOf(new Function<SuggestedReviewerInfo, String>() {
        @Nullable
        @Override
        public String apply(@Nullable SuggestedReviewerInfo suggestedReviewerInfo) {
          if (suggestedReviewerInfo == null) {
            return null;
          }
          return suggestedReviewerInfo.account != null
              ? MoreObjects.firstNonNull(suggestedReviewerInfo.account.email,
              Strings.nullToEmpty(suggestedReviewerInfo.account.name))
              : Strings.nullToEmpty(suggestedReviewerInfo.group.name);
        }
      });

  private final AccountLoader accountLoader;
  private final AccountControl accountControl;
  private final GroupMembers.Factory groupMembersFactory;
  private final AccountCache accountCache;
  private final Provider<ReviewDb> dbProvider;
  private final Provider<CurrentUser> currentUser;
  private final IdentifiedUser.GenericFactory identifiedUserFactory;
  private final GroupBackend groupBackend;
  private final boolean suggestAccounts;
  private final int suggestFrom;
  private final int maxAllowed;
  private int limit;
  private String query;
  private boolean useFullTextSearch;
  private final int fullTextMaxMatches;
  private final int maxSuggestedReviewers;
  private final ReviewerSuggestionCache reviewerSuggestionCache;
  private final DynamicItem<ReviewerAnnotator> annotatorProvider;

  @Option(name = "--limit", aliases = {"-n"}, metaVar = "CNT",
      usage = "maximum number of reviewers to list")
  public void setLimit(int l) {
    this.limit =
        l <= 0 ? maxSuggestedReviewers : Math.min(l,
            maxSuggestedReviewers);
  }

  @Option(name = "--query", aliases = {"-q"}, metaVar = "QUERY",
      usage = "match reviewers query")
  public void setQuery(String q) {
    this.query = q;
  }

  @Inject
  SuggestReviewers(AccountVisibility av,
      AccountLoader.Factory accountLoaderFactory,
      AccountControl.Factory accountControlFactory,
      AccountCache accountCache,
      GroupMembers.Factory groupMembersFactory,
      IdentifiedUser.GenericFactory identifiedUserFactory,
      Provider<CurrentUser> currentUser,
      Provider<ReviewDb> dbProvider,
      @GerritServerConfig Config cfg,
      GroupBackend groupBackend,
      ReviewerSuggestionCache reviewerSuggestionCache,
      DynamicItem<ReviewerAnnotator> annotatorProvider) {
    this.accountLoader = accountLoaderFactory.create(true);
    this.accountControl = accountControlFactory.get();
    this.accountCache = accountCache;
    this.groupMembersFactory = groupMembersFactory;
    this.dbProvider = dbProvider;
    this.identifiedUserFactory = identifiedUserFactory;
    this.currentUser = currentUser;
    this.groupBackend = groupBackend;
    this.reviewerSuggestionCache = reviewerSuggestionCache;
    this.maxSuggestedReviewers =
        cfg.getInt("suggest", "maxSuggestedReviewers", DEFAULT_MAX_SUGGESTED);
    this.limit = this.maxSuggestedReviewers;
    this.fullTextMaxMatches =
        cfg.getInt("suggest", "fullTextSearchMaxMatches",
            DEFAULT_MAX_MATCHES);
    String suggest = cfg.getString("suggest", null, "accounts");
    if ("OFF".equalsIgnoreCase(suggest)
        || "false".equalsIgnoreCase(suggest)) {
      this.suggestAccounts = false;
    } else {
      this.useFullTextSearch = cfg.getBoolean("suggest", "fullTextSearch", false);
      this.suggestAccounts = (av != AccountVisibility.NONE);
    }

    this.suggestFrom = cfg.getInt("suggest", null, "from", 0);
    this.maxAllowed = cfg.getInt("addreviewer", "maxAllowed",
        PostReviewers.DEFAULT_MAX_REVIEWERS);
    this.annotatorProvider = annotatorProvider;
  }

  private interface VisibilityControl {
    boolean isVisibleTo(Account.Id account) throws OrmException;
  }

  @Override
  public List<SuggestedReviewerInfo> apply(ChangeResource rsrc)
      throws BadRequestException, OrmException, IOException {
    if (Strings.isNullOrEmpty(query)) {
      throw new BadRequestException("missing query field");
    }

    if (!suggestAccounts || query.length() < suggestFrom) {
      return Collections.emptyList();
    }

    VisibilityControl visibilityControl = getVisibility(rsrc);
    List<AccountInfo> suggestedAccounts;
    if (useFullTextSearch) {
      suggestedAccounts = suggestAccountFullTextSearch(visibilityControl);
    } else {
      suggestedAccounts = suggestAccount(visibilityControl);
    }

    List<SuggestedReviewerInfo> reviewer = Lists.newArrayList();
    for (AccountInfo a : suggestedAccounts) {
      SuggestedReviewerInfo info = new SuggestedReviewerInfo();
      info.account = a;
      reviewer.add(info);
    }

    Project p = rsrc.getControl().getProject();
    for (GroupReference g : suggestAccountGroup(
        rsrc.getControl().getProjectControl())) {
      if (suggestGroupAsReviewer(p, g, visibilityControl)) {
        GroupBaseInfo info = new GroupBaseInfo();
        info.id = Url.encode(g.getUUID().get());
        info.name = g.getName();
        SuggestedReviewerInfo suggestedReviewerInfo = new SuggestedReviewerInfo();
        suggestedReviewerInfo.group = info;
        reviewer.add(suggestedReviewerInfo);
      }
    }

    reviewer = ORDERING.immutableSortedCopy(reviewer);
    if (reviewer.size() > limit) {
      reviewer = reviewer.subList(0, limit);
    }

    ReviewerAnnotator rap = annotatorProvider.get();
    if (rap != null) {
      for (SuggestedReviewerInfo sri : reviewer) {
        sri.annotation = rap.getAnnotation(sri, rsrc.getControl());
      }
    }
    return reviewer;
  }

  private VisibilityControl getVisibility(final ChangeResource rsrc) {
    if (rsrc.getControl().getRefControl().isVisibleByRegisteredUsers()) {
      return new VisibilityControl() {
        @Override
        public boolean isVisibleTo(Account.Id account) throws OrmException {
          return true;
        }
      };
    } else {
      return new VisibilityControl() {
        @Override
        public boolean isVisibleTo(Account.Id account) throws OrmException {
          IdentifiedUser who =
              identifiedUserFactory.create(dbProvider, account);
          // we can't use changeControl directly as it won't suggest reviewers
          // to drafts
          return rsrc.getControl().forUser(who).isRefVisible();
        }
      };
    }
  }

  private List<GroupReference> suggestAccountGroup(ProjectControl ctl) {
    return Lists.newArrayList(
        Iterables.limit(groupBackend.suggest(query, ctl), limit));
  }

  private List<AccountInfo> suggestAccount(VisibilityControl visibilityControl)
      throws OrmException {
    String a = query;
    String b = a + MAX_SUFFIX;

    Map<Account.Id, AccountInfo> r = new LinkedHashMap<>();
    Map<Account.Id, String> queryEmail = new HashMap<>();

    for (Account p : dbProvider.get().accounts()
        .suggestByFullName(a, b, limit)) {
      if (p.isActive()) {
        addSuggestion(r, p.getId(), visibilityControl);
      }
    }

    if (r.size() < limit) {
      for (Account p : dbProvider.get().accounts()
          .suggestByPreferredEmail(a, b, limit - r.size())) {
        if (p.isActive()) {
          addSuggestion(r, p.getId(), visibilityControl);
        }
      }
    }

    if (r.size() < limit) {
      for (AccountExternalId e : dbProvider.get().accountExternalIds()
          .suggestByEmailAddress(a, b, limit - r.size())) {
        if (!r.containsKey(e.getAccountId())) {
          Account p = accountCache.get(e.getAccountId()).getAccount();
          if (p.isActive()) {
            if (addSuggestion(r, p.getId(), visibilityControl)) {
              queryEmail.put(e.getAccountId(), e.getEmailAddress());
            }
          }
        }
      }
    }

    accountLoader.fill();
    for (Map.Entry<Account.Id, String> p : queryEmail.entrySet()) {
      AccountInfo info = r.get(p.getKey());
      if (info != null) {
        info.email = p.getValue();
      }
    }
    return new ArrayList<>(r.values());
  }

  private List<AccountInfo> suggestAccountFullTextSearch(
      VisibilityControl visibilityControl) throws IOException, OrmException {
    List<AccountInfo> results = reviewerSuggestionCache.search(
        query, fullTextMaxMatches);

    Iterator<AccountInfo> it = results.iterator();
    while (it.hasNext()) {
      Account.Id accountId = new Account.Id(it.next()._accountId);
      if (!(visibilityControl.isVisibleTo(accountId)
          && accountControl.canSee(accountId))) {
        it.remove();
      }
    }

    return results;
  }

  private boolean addSuggestion(Map<Account.Id, AccountInfo> map,
      Account.Id account, VisibilityControl visibilityControl)
      throws OrmException {
    if (!map.containsKey(account)
        // Can the suggestion see the change?
        && visibilityControl.isVisibleTo(account)
        // Can the account see the current user?
        && accountControl.canSee(account)) {
      map.put(account, accountLoader.get(account));
      return true;
    }
    return false;
  }

  private boolean suggestGroupAsReviewer(Project project,
      GroupReference group, VisibilityControl visibilityControl)
      throws OrmException, IOException {
    if (!PostReviewers.isLegalReviewerGroup(group.getUUID())) {
      return false;
    }

    try {
      Set<Account> members = groupMembersFactory
          .create(currentUser.get())
          .listAccounts(group.getUUID(), project.getNameKey());

      if (members.isEmpty()) {
        return false;
      }

      if (maxAllowed > 0 && members.size() > maxAllowed) {
        return false;
      }

      // require that at least one member in the group can see the change
      for (Account account : members) {
        if (visibilityControl.isVisibleTo(account.getId())) {
          return true;
        }
      }
    } catch (NoSuchGroupException e) {
      return false;
    } catch (NoSuchProjectException e) {
      return false;
    }

    return false;
  }
}
