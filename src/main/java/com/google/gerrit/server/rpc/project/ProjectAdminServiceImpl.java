// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.server.rpc.project;

import com.google.gerrit.client.admin.ProjectAdminService;
import com.google.gerrit.client.admin.ProjectDetail;
import com.google.gerrit.client.reviewdb.AccountGroup;
import com.google.gerrit.client.reviewdb.ApprovalCategory;
import com.google.gerrit.client.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.client.reviewdb.Branch;
import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.reviewdb.ProjectRight;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.InvalidNameException;
import com.google.gerrit.client.rpc.InvalidRevisionException;
import com.google.gerrit.client.rpc.NoSuchEntityException;
import com.google.gerrit.git.ReplicationQueue;
import com.google.gerrit.server.BaseServiceImplementation;
import com.google.gerrit.server.GerritServer;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.WildProjectName;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectState;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spearce.jgit.errors.IncorrectObjectTypeException;
import org.spearce.jgit.errors.MissingObjectException;
import org.spearce.jgit.errors.RepositoryNotFoundException;
import org.spearce.jgit.lib.Constants;
import org.spearce.jgit.lib.ObjectId;
import org.spearce.jgit.lib.Ref;
import org.spearce.jgit.lib.RefUpdate;
import org.spearce.jgit.lib.Repository;
import org.spearce.jgit.revwalk.ObjectWalk;
import org.spearce.jgit.revwalk.RevCommit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class ProjectAdminServiceImpl extends BaseServiceImplementation implements
    ProjectAdminService {
  private final Logger log = LoggerFactory.getLogger(getClass());
  private final GerritServer server;
  private final ProjectCache projectCache;
  private final Project.NameKey wildProject;
  private final ProjectControl.Factory projectControlFactory;
  private final ReplicationQueue replication;
  private final Provider<IdentifiedUser> identifiedUser;

  private final ListBranches.Factory listBranchesFactory;
  private final ProjectDetailFactory.Factory projectDetailFactory;

  @Inject
  ProjectAdminServiceImpl(final Provider<ReviewDb> schema,
      final GerritServer gs, final ProjectCache pc, final ReplicationQueue rq,
      final Provider<IdentifiedUser> currentUser,
      @WildProjectName final Project.NameKey wp,
      final ProjectControl.Factory projectControlFactory,
      final ListBranches.Factory listBranchesFactory,
      final ProjectDetailFactory.Factory projectDetailFactory) {
    super(schema, currentUser);
    this.server = gs;
    this.projectCache = pc;
    this.replication = rq;
    this.wildProject = wp;
    this.identifiedUser = currentUser;
    this.projectControlFactory = projectControlFactory;

    this.listBranchesFactory = listBranchesFactory;
    this.projectDetailFactory = projectDetailFactory;
  }

  public void ownedProjects(final AsyncCallback<List<Project>> callback) {
    run(callback, new Action<List<Project>>() {
      public List<Project> run(ReviewDb db) throws OrmException {
        final IdentifiedUser user = identifiedUser.get();
        final List<Project> result;
        if (user.isAdministrator()) {
          result = db.projects().all().toList();
        } else {
          final HashSet<Project.NameKey> seen = new HashSet<Project.NameKey>();
          result = new ArrayList<Project>();
          for (final AccountGroup.Id groupId : user.getEffectiveGroups()) {
            for (final ProjectRight r : db.projectRights().byCategoryGroup(
                ApprovalCategory.OWN, groupId)) {
              final Project.NameKey name = r.getProjectNameKey();
              if (!seen.add(name)) {
                continue;
              }
              try {
                ProjectControl c = projectControlFactory.controlFor(name);
                if (c.isOwner()) {
                  result.add(c.getProject());
                }
              } catch (NoSuchProjectException e) {
                continue;
              }
            }
          }
        }
        Collections.sort(result, new Comparator<Project>() {
          public int compare(final Project a, final Project b) {
            return a.getName().compareTo(b.getName());
          }
        });
        return result;
      }
    });
  }

  public void projectDetail(final Project.NameKey projectName,
      final AsyncCallback<ProjectDetail> callback) {
    projectDetailFactory.create(projectName).to(callback);
  }

  public void changeProjectSettings(final Project update,
      final AsyncCallback<ProjectDetail> callback) {
    final Project.NameKey projectName = update.getNameKey();
    run(callback, new Action<ProjectDetail>() {
      public ProjectDetail run(final ReviewDb db) throws OrmException, Failure {
        assertAmProjectOwner(db, projectName);
        final Project proj = db.projects().get(projectName);
        if (proj == null) {
          throw new Failure(new NoSuchEntityException());
        }
        proj.copySettingsFrom(update);
        db.projects().update(Collections.singleton(proj));
        projectCache.evict(proj);

        if (!wildProject.equals(projectName)) {
          server.setProjectDescription(projectName.get(), update
              .getDescription());
        }

        try {
          return projectDetailFactory.create(projectName).call();
        } catch (NoSuchEntityException e) {
          throw new Failure(e);
        }
      }
    });
  }

  public void deleteRight(final Project.NameKey name,
      final Set<ProjectRight.Key> keys, final AsyncCallback<VoidResult> callback) {
    run(callback, new Action<VoidResult>() {
      public VoidResult run(final ReviewDb db) throws OrmException, Failure,
          NoSuchProjectException {
        final ProjectControl control = projectControlFactory.ownerFor(name);
        if (!control.isOwner()) {
          throw new NoSuchProjectException(name);
        }
        for (final ProjectRight.Key k : keys) {
          if (!name.equals(k.getProjectNameKey())) {
            throw new Failure(new NoSuchEntityException());
          }
        }
        for (final ProjectRight.Key k : keys) {
          final ProjectRight m = db.projectRights().get(k);
          if (m != null) {
            db.projectRights().delete(Collections.singleton(m));
          }
        }
        projectCache.evict(control.getProject());
        return VoidResult.INSTANCE;
      }
    });
  }

  public void addRight(final Project.NameKey projectName,
      final ApprovalCategory.Id categoryId, final String groupName,
      final short amin, final short amax,
      final AsyncCallback<ProjectDetail> callback) {
    final short min, max;
    if (amin <= amax) {
      min = amin;
      max = amax;
    } else {
      min = amax;
      max = amin;
    }

    run(callback, new Action<ProjectDetail>() {
      public ProjectDetail run(ReviewDb db) throws OrmException, Failure {
        assertAmProjectOwner(db, projectName);
        final Project proj = db.projects().get(projectName);
        if (proj == null) {
          throw new Failure(new NoSuchEntityException());
        }

        if (wildProject.equals(projectName)
            && ApprovalCategory.OWN.equals(categoryId)) {
          // Giving out control of the WILD_PROJECT to other groups beyond
          // Administrators is dangerous. Having control over WILD_PROJECT
          // is about the same as having Administrator access as users are
          // able to affect grants in all projects on the system.
          //
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
            new ProjectRight.Key(projectName, categoryId, group.getId());
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

        projectCache.evict(proj);
        try {
          return projectDetailFactory.create(projectName).call();
        } catch (NoSuchEntityException e) {
          throw new Failure(e);
        }
      }
    });
  }

  public void listBranches(final Project.NameKey projectName,
      final AsyncCallback<List<Branch>> callback) {
    listBranchesFactory.create(projectName).to(callback);
  }

  public void deleteBranch(final Project.NameKey projectName,
      final Set<Branch.NameKey> ids,
      final AsyncCallback<Set<Branch.NameKey>> callback) {
    run(callback, new Action<Set<Branch.NameKey>>() {
      public Set<Branch.NameKey> run(ReviewDb db) throws OrmException, Failure {
        for (Branch.NameKey k : ids) {
          if (!projectName.equals(k.getParentKey())) {
            throw new Failure(new NoSuchEntityException());
          }
        }
        final ProjectControl c;
        try {
          c = projectControlFactory.controlFor(projectName);
        } catch (NoSuchProjectException e) {
          throw new Failure(new NoSuchEntityException());
        }
        if (!c.isOwner()) {
          throw new Failure(new NoSuchEntityException());
        }
        final Set<Branch.NameKey> deleted = new HashSet<Branch.NameKey>();
        for (final Branch.NameKey k : ids) {
          final Branch m = db.branches().get(k);
          if (m == null) {
            continue;
          }
          final Repository r;

          try {
            r = server.openRepository(k.getParentKey().get());
          } catch (RepositoryNotFoundException e) {
            throw new Failure(new NoSuchEntityException());
          }

          final RefUpdate.Result result;
          try {
            final RefUpdate u = r.updateRef(m.getName());
            u.setForceUpdate(true);
            result = u.delete();
          } catch (IOException e) {
            log.error("Cannot delete " + k, e);
            r.close();
            continue;
          }

          final Branch.NameKey mKey = m.getNameKey();
          switch (result) {
            case NEW:
            case NO_CHANGE:
            case FAST_FORWARD:
            case FORCED:
              db.branches().delete(Collections.singleton(m));
              deleted.add(mKey);
              replication.scheduleUpdate(mKey.getParentKey(), m.getName());
              break;

            case REJECTED_CURRENT_BRANCH:
              log.warn("Cannot delete " + k + ": " + result.name());
              break;

            default:
              log.error("Cannot delete " + k + ": " + result.name());
              break;
          }
          r.close();
        }
        return deleted;
      }
    });
  }

  public void addBranch(final Project.NameKey projectName,
      final String branchName, final String startingRevision,
      final AsyncCallback<List<Branch>> callback) {
    run(callback, new Action<List<Branch>>() {
      public List<Branch> run(ReviewDb db) throws OrmException, Failure {
        String refname = branchName;
        if (!refname.startsWith(Constants.R_REFS)) {
          refname = Constants.R_HEADS + refname;
        }
        if (!Repository.isValidRefName(refname)) {
          throw new Failure(new InvalidNameException());
        }

        final ProjectState pce = projectCache.get(projectName);
        if (pce == null) {
          throw new Failure(new NoSuchEntityException());
        }
        assertAmProjectOwner(db, projectName);

        final String repoName = pce.getProject().getName();
        final Branch.NameKey name =
            new Branch.NameKey(pce.getProject().getNameKey(), refname);
        final Repository repo;
        try {
          repo = server.openRepository(repoName);
        } catch (RepositoryNotFoundException e1) {
          throw new Failure(new NoSuchEntityException());
        }

        try {
          // Convert the name given by the user into a valid object.
          //
          final ObjectId revid;
          try {
            revid = repo.resolve(startingRevision);
            if (revid == null) {
              throw new Failure(new InvalidRevisionException());
            }
          } catch (IOException err) {
            log.error("Cannot resolve \"" + startingRevision + "\" in "
                + repoName, err);
            throw new Failure(new InvalidRevisionException());
          }

          // Ensure it is fully connected in this repository. If not,
          // we can't safely create a ref to it as objects are missing
          //
          final RevCommit revcommit;
          final ObjectWalk rw = new ObjectWalk(repo);
          try {
            try {
              revcommit = rw.parseCommit(revid);
              rw.markStart(revcommit);
            } catch (IncorrectObjectTypeException err) {
              throw new Failure(new InvalidRevisionException());
            }
            for (final Ref r : repo.getAllRefs().values()) {
              try {
                rw.markUninteresting(rw.parseAny(r.getObjectId()));
              } catch (MissingObjectException err) {
                continue;
              }
            }
            rw.checkConnectivity();
          } catch (IncorrectObjectTypeException err) {
            throw new Failure(new InvalidRevisionException());
          } catch (MissingObjectException err) {
            throw new Failure(new InvalidRevisionException());
          } catch (IOException err) {
            log.error("Repository " + repoName + " possibly corrupt", err);
            throw new Failure(new InvalidRevisionException());
          }

          try {
            final RefUpdate u = repo.updateRef(refname);
            u.setExpectedOldObjectId(ObjectId.zeroId());
            u.setNewObjectId(revid);
            u.setRefLogIdent(identifiedUser.get().newPersonIdent());
            u.setRefLogMessage("created via web from " + startingRevision,
                false);
            final RefUpdate.Result result = u.update(rw);
            switch (result) {
              case FAST_FORWARD:
              case NEW:
              case NO_CHANGE:
                replication.scheduleUpdate(name.getParentKey(), refname);
                break;
              default: {
                final String msg =
                    "Cannot create branch " + name + ": " + result.name();
                log.error(msg);
                throw new Failure(new IOException(result.name()));
              }
            }
          } catch (IOException err) {
            log.error("Cannot create branch " + name, err);
            throw new Failure(err);
          }
        } finally {
          repo.close();
        }

        final Branch newBranch = new Branch(name);
        db.branches().insert(Collections.singleton(newBranch));
        return db.branches().byProject(pce.getProject().getNameKey()).toList();
      }
    });
  }

  private void assertAmProjectOwner(final ReviewDb db,
      final Project.NameKey projectName) throws Failure {
    try {
      if (!projectControlFactory.controlFor(projectName).isOwner()) {
        throw new Failure(new NoSuchEntityException());
      }
    } catch (NoSuchProjectException e) {
      throw new Failure(new NoSuchEntityException());
    }
  }
}
