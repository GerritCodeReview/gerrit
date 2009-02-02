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
import com.google.gerrit.client.data.ProjectCache;
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
import com.google.gerrit.git.InvalidRepositoryException;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.gwtorm.client.OrmException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spearce.jgit.lib.Constants;
import org.spearce.jgit.lib.LockFile;
import org.spearce.jgit.lib.Repository;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ProjectAdminServiceImpl extends BaseServiceImplementation
    implements ProjectAdminService {
  private final Logger log = LoggerFactory.getLogger(getClass());
  private final GerritServer server;

  ProjectAdminServiceImpl(final GerritServer gs) {
    server = gs;
  }

  public void ownedProjects(final AsyncCallback<List<Project>> callback) {
    run(callback, new Action<List<Project>>() {
      public List<Project> run(ReviewDb db) throws OrmException {
        final List<Project> result;
        if (Common.getGroupCache().isAdministrator(Common.getAccountId())) {
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
        final ProjectCache.Entry p = Common.getProjectCache().get(projectId);
        if (p == null) {
          throw new Failure(new NoSuchEntityException());
        }

        final ProjectDetail d = new ProjectDetail();
        d.load(db, p);
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
        Common.getProjectCache().invalidate(proj);

        if (!ProjectRight.WILD_PROJECT.equals(projectId)) {
          // Update git's description file, in case gitweb is being used
          //
          try {
            final Repository e;
            final LockFile f;

            e = server.getRepositoryCache().get(proj.getName());
            f = new LockFile(new File(e.getDirectory(), "description"));
            if (f.lock()) {
              String d = proj.getDescription();
              if (d != null) {
                d = d.trim() + "\n";
              } else {
                d = "";
              }
              f.write(Constants.encode(d));
              f.commit();
            }
          } catch (IOException e) {
            log.error("Cannot update description for " + proj.getName(), e);
          } catch (InvalidRepositoryException e) {
            log.error("Cannot update description for " + proj.getName(), e);
          }
        }

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
        if (ProjectRight.WILD_PROJECT.equals(projectId)) {
          // This is *not* a good idea to change away from administrators.
          //
          throw new Failure(new NoSuchEntityException());
        }

        final AccountGroup owner =
            db.accountGroups().get(new AccountGroup.NameKey(newOwnerName));
        if (owner == null) {
          throw new Failure(new NoSuchEntityException());
        }

        project.setOwnerGroupId(owner.getId());
        db.projects().update(Collections.singleton(project));
        Common.getProjectCache().invalidate(project);
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
              amAdmin =
                  Common.getGroupCache().isAdministrator(Common.getAccountId());
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
            Common.getProjectCache().invalidate(k.getProjectId());
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

        Common.getProjectCache().invalidate(proj);
        final ProjectDetail d = new ProjectDetail();
        d.load(db, Common.getProjectCache().get(projectId));
        return d;
      }
    });
  }

  private void assertAmProjectOwner(final ReviewDb db,
      final Project.Id projectId) throws Failure {
    final ProjectCache.Entry p = Common.getProjectCache().get(projectId);
    if (p == null) {
      throw new Failure(new NoSuchEntityException());
    }
    final Account.Id me = Common.getAccountId();
    if (!Common.getGroupCache().isInGroup(me, p.getProject().getOwnerGroupId())
        && !Common.getGroupCache().isAdministrator(me)) {
      throw new Failure(new NoSuchEntityException());
    }
  }

  private List<Project> myOwnedProjects(final ReviewDb db) throws OrmException {
    final Account.Id me = Common.getAccountId();
    final List<Project> own = new ArrayList<Project>();
    for (final AccountGroup.Id groupId : Common.getGroupCache().getGroups(me)) {
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
