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
import com.google.gerrit.client.reviewdb.AccountGroup;
import com.google.gerrit.client.reviewdb.AccountGroupMember;
import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.BaseServiceImplementation;
import com.google.gerrit.client.rpc.NoSuchEntityException;
import com.google.gerrit.client.rpc.RpcUtil;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.gwtorm.client.OrmException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ProjectAdminServiceImpl extends BaseServiceImplementation
    implements ProjectAdminService {
  private final AccountGroup.Id adminId;

  public ProjectAdminServiceImpl(final GerritServer server) {
    super(server.getDatabase());
    adminId = server.getAdminGroupId();
  }

  public void ownedProjects(final AsyncCallback<List<Project>> callback) {
    run(callback, new Action<List<Project>>() {
      public List<Project> run(ReviewDb db) throws OrmException {
        final List<Project> result;
        if (amAdmin(db)) {
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

  private static boolean amInGroup(final ReviewDb db,
      final AccountGroup.Id groupId) throws OrmException {
    return db.accountGroupMembers().get(
        new AccountGroupMember.Key(RpcUtil.getAccountId(), groupId)) != null;
  }

  private boolean amAdmin(final ReviewDb db) throws OrmException {
    return adminId != null && amInGroup(db, adminId);
  }

  private void assertAmProjectOwner(final ReviewDb db,
      final Project.Id projectId) throws OrmException, Failure {
    final Project project = db.projects().get(projectId);
    if (project == null) {
      throw new Failure(new NoSuchEntityException());
    }
    if (!amInGroup(db, project.getOwnerGroupId()) && !amAdmin(db)) {
      throw new Failure(new NoSuchEntityException());
    }
  }

  private static List<Project> myOwnedProjects(final ReviewDb db)
      throws OrmException {
    final List<Project> own = new ArrayList<Project>();
    for (final AccountGroupMember m : db.accountGroupMembers().byAccount(
        RpcUtil.getAccountId()).toList()) {
      for (final Project g : db.projects().ownedByGroup(m.getAccountGroupId())) {
        own.add(g);
      }
    }
    return own;
  }
}
