// Copyright 2008 Google Inc.
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

package com.google.gerrit.server;

import com.google.gerrit.client.admin.ProjectAdminService;
import com.google.gerrit.client.admin.ProjectDetail;
import com.google.gerrit.client.data.GroupCache;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.AccountGroup;
import com.google.gerrit.client.reviewdb.ApprovalCategory;
import com.google.gerrit.client.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.reviewdb.ProjectRight;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.BaseServiceImplementation;
import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.client.rpc.NoSuchEntityException;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.gwtorm.client.OrmException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ProjectAdminServiceImpl extends BaseServiceImplementation
    implements ProjectAdminService {
  private final GroupCache groupCache;

  public ProjectAdminServiceImpl(final GerritServer server) {
    super(server.getDatabase());
    groupCache = server.getGroupCache();
  }

  public void ownedProjects(final AsyncCallback<List<Project>> callback) {
    run(callback, new Action<List<Project>>() {
      public List<Project> run(ReviewDb db) throws OrmException {
        final List<Project> result;
        if (groupCache.isAdministrator(Common.getAccountId())) {
          result = db.projects().all().toList();
        } else {
          result = myOwnedProjects(db);
          Collections.sort(result, new Comparator<Project>() {
            public int compare(final Project a, final Project b) {
              return a.getName().compareTo(b.getName());
            }
          });
        }
        return result;
      }
    });
  }

  public void projectDetail(final Project.Id projectId,
      final AsyncCallback<ProjectDetail> callback) {
    run(callback, new Action<ProjectDetail>() {
      public ProjectDetail run(ReviewDb db) throws OrmException, Failure {
        assertAmProjectOwner(db, projectId);
        final Project proj = db.projects().get(projectId);
        if (proj == null) {
          throw new Failure(new NoSuchEntityException());
        }

        final ProjectDetail d = new ProjectDetail();
        d.load(db, proj);
        return d;
      }
    });
  }

  public void changeProjectDescription(final Project.Id projectId,
      final String description, final AsyncCallback<VoidResult> callback) {
    run(callback, new Action<VoidResult>() {
      public VoidResult run(final ReviewDb db) throws OrmException, Failure {
        assertAmProjectOwner(db, projectId);
        final Project proj = db.projects().get(projectId);
        if (proj == null) {
          throw new Failure(new NoSuchEntityException());
        }
        proj.setDescription(description);
        db.projects().update(Collections.singleton(proj));
        return VoidResult.INSTANCE;
      }
    });
  }

  public void changeProjectOwner(final Project.Id projectId,
      final String newOwnerName, final AsyncCallback<VoidResult> callback) {
    run(callback, new Action<VoidResult>() {
      public VoidResult run(final ReviewDb db) throws OrmException, Failure {
        assertAmProjectOwner(db, projectId);
        final Project project = db.projects().get(projectId);
        if (project == null) {
          throw new Failure(new NoSuchEntityException());
        }

        final AccountGroup owner =
            db.accountGroups().get(new AccountGroup.NameKey(newOwnerName));
        if (owner == null) {
          throw new Failure(new NoSuchEntityException());
        }

        project.setOwnerGroupId(owner.getId());
        db.projects().update(Collections.singleton(project));
        return VoidResult.INSTANCE;
      }
    });
  }

  public void deleteRight(final Set<ProjectRight.Key> keys,
      final AsyncCallback<VoidResult> callback) {
    run(callback, new Action<VoidResult>() {
      public VoidResult run(final ReviewDb db) throws OrmException, Failure {
        final Set<Project.Id> owned = ids(myOwnedProjects(db));
        Boolean amAdmin = null;
        for (final ProjectRight.Key k : keys) {
          if (!owned.contains(k.getProjectId())) {
            if (amAdmin == null) {
              amAdmin = groupCache.isAdministrator(Common.getAccountId());
            }
            if (!amAdmin) {
              throw new Failure(new NoSuchEntityException());
            }
          }
        }
        for (final ProjectRight.Key k : keys) {
          final ProjectRight m = db.projectRights().get(k);
          if (m != null) {
            db.projectRights().delete(Collections.singleton(m));
          }
        }
        return VoidResult.INSTANCE;
      }
    });
  }

  public void addRight(final Project.Id projectId,
      final ApprovalCategory.Id categoryId, final String groupName,
      final short min, final short max,
      final AsyncCallback<ProjectDetail> callback) {
    run(callback, new Action<ProjectDetail>() {
      public ProjectDetail run(ReviewDb db) throws OrmException, Failure {
        assertAmProjectOwner(db, projectId);
        final Project proj = db.projects().get(projectId);
        if (proj == null) {
          throw new Failure(new NoSuchEntityException());
        }

        final ApprovalCategory cat = db.approvalCategories().get(categoryId);
        if (cat == null) {
          throw new Failure(new NoSuchEntityException());
        }

        if (db.approvalCategoryValues().get(
            new ApprovalCategoryValue.Id(categoryId, min)) == null) {
          throw new Failure(new NoSuchEntityException());
        }

        if (db.approvalCategoryValues().get(
            new ApprovalCategoryValue.Id(categoryId, max)) == null) {
          throw new Failure(new NoSuchEntityException());
        }

        final AccountGroup group =
            db.accountGroups().get(new AccountGroup.NameKey(groupName));
        if (group == null) {
          throw new Failure(new NoSuchEntityException());
        }

        final ProjectRight.Key key =
            new ProjectRight.Key(projectId, categoryId, group.getId());
        ProjectRight pr = db.projectRights().get(key);
        if (pr == null) {
          pr = new ProjectRight(key);
          pr.setMinValue(min);
          pr.setMaxValue(max);
          db.projectRights().insert(Collections.singleton(pr));
        } else {
          pr.setMinValue(min);
          pr.setMaxValue(max);
          db.projectRights().update(Collections.singleton(pr));
        }

        final ProjectDetail d = new ProjectDetail();
        d.load(db, proj);
        return d;
      }
    });
  }

  private void assertAmProjectOwner(final ReviewDb db,
      final Project.Id projectId) throws OrmException, Failure {
    final Project project = db.projects().get(projectId);
    if (project == null) {
      throw new Failure(new NoSuchEntityException());
    }
    final Account.Id me = Common.getAccountId();
    if (!groupCache.isInGroup(me, project.getOwnerGroupId())
        && !groupCache.isAdministrator(me)) {
      throw new Failure(new NoSuchEntityException());
    }
  }

  private List<Project> myOwnedProjects(final ReviewDb db) throws OrmException {
    final Account.Id me = Common.getAccountId();
    final List<Project> own = new ArrayList<Project>();
    for (final AccountGroup.Id groupId : groupCache.getGroups(me)) {
      for (final Project g : db.projects().ownedByGroup(groupId)) {
        own.add(g);
      }
    }
    return own;
  }

  private static Set<Project.Id> ids(final Collection<Project> projectList) {
    final HashSet<Project.Id> r = new HashSet<Project.Id>();
    for (final Project project : projectList) {
      r.add(project.getId());
    }
    return r;
  }
}
