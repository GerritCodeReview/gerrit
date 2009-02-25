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
import com.google.gerrit.client.reviewdb.Branch;
import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.reviewdb.ProjectRight;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.BaseServiceImplementation;
import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.client.rpc.InvalidNameException;
import com.google.gerrit.client.rpc.InvalidRevisionException;
import com.google.gerrit.client.rpc.NoSuchEntityException;
import com.google.gerrit.git.InvalidRepositoryException;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.gwtorm.client.OrmException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spearce.jgit.errors.IncorrectObjectTypeException;
import org.spearce.jgit.errors.MissingObjectException;
import org.spearce.jgit.lib.Constants;
import org.spearce.jgit.lib.LockFile;
import org.spearce.jgit.lib.ObjectId;
import org.spearce.jgit.lib.Ref;
import org.spearce.jgit.lib.RefUpdate;
import org.spearce.jgit.lib.Repository;
import org.spearce.jgit.revwalk.ObjectWalk;
import org.spearce.jgit.revwalk.RevCommit;

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

  public void listBranches(final Project.Id project,
      final AsyncCallback<List<Branch>> callback) {
    run(callback, new Action<List<Branch>>() {
      public List<Branch> run(ReviewDb db) throws OrmException, Failure {
        final ProjectCache.Entry e = Common.getProjectCache().get(project);
        if (e == null) {
          throw new Failure(new NoSuchEntityException());
        }
        assertCanRead(e.getProject().getNameKey());
        return db.branches().byProject(e.getProject().getNameKey()).toList();
      }
    });
  }

  public void deleteBranch(final Set<Branch.NameKey> ids,
      final AsyncCallback<Set<Branch.NameKey>> callback) {
    run(callback, new Action<Set<Branch.NameKey>>() {
      public Set<Branch.NameKey> run(ReviewDb db) throws OrmException, Failure {
        final Set<Branch.NameKey> deleted = new HashSet<Branch.NameKey>();
        final Set<Project.Id> owned = ids(myOwnedProjects(db));
        Boolean amAdmin = null;
        for (final Branch.NameKey k : ids) {
          final ProjectCache.Entry e;

          e = Common.getProjectCache().get(k.getParentKey());
          if (e == null) {
            throw new Failure(new NoSuchEntityException());
          }
          if (!owned.contains(e.getProject().getId())) {
            if (amAdmin == null) {
              amAdmin =
                  Common.getGroupCache().isAdministrator(Common.getAccountId());
            }
            if (!amAdmin) {
              throw new Failure(new NoSuchEntityException());
            }
          }
        }
        for (final Branch.NameKey k : ids) {
          final Branch m = db.branches().get(k);
          if (m == null) {
            continue;
          }
          final Repository r;

          try {
            r = server.getRepositoryCache().get(k.getParentKey().get());
          } catch (InvalidRepositoryException e) {
            throw new Failure(new NoSuchEntityException());
          }

          final RefUpdate.Result result;
          try {
            final RefUpdate u = r.updateRef(m.getName());
            u.setForceUpdate(true);
            result = u.delete();
          } catch (IOException e) {
            log.error("Cannot delete " + k, e);
            continue;
          }

          switch (result) {
            case NEW:
            case NO_CHANGE:
            case FAST_FORWARD:
            case FORCED:
              db.branches().delete(Collections.singleton(m));
              deleted.add(m.getNameKey());
              break;

            case REJECTED_CURRENT_BRANCH:
              log.warn("Cannot delete " + k + ": " + result.name());
              break;

            default:
              log.error("Cannot delete " + k + ": " + result.name());
              break;
          }
        }
        return deleted;
      }
    });
  }

  public void addBranch(final Project.Id projectId, final String branchName,
      final String startingRevision, final AsyncCallback<List<Branch>> callback) {
    run(callback, new Action<List<Branch>>() {
      public List<Branch> run(ReviewDb db) throws OrmException, Failure {
        String refname = branchName;
        if (!refname.startsWith(Constants.R_REFS)) {
          refname = Constants.R_HEADS + refname;
        }
        if (!Repository.isValidRefName(refname)) {
          throw new Failure(new InvalidNameException());
        }

        final Account me = Common.getAccountCache().get(Common.getAccountId());
        if (me == null) {
          throw new Failure(new NoSuchEntityException());
        }
        final ProjectCache.Entry pce = Common.getProjectCache().get(projectId);
        if (pce == null) {
          throw new Failure(new NoSuchEntityException());
        }
        assertAmProjectOwner(db, projectId);

        final String repoName = pce.getProject().getName();
        final Repository repo;
        try {
          repo = server.getRepositoryCache().get(repoName);
        } catch (InvalidRepositoryException e1) {
          throw new Failure(new NoSuchEntityException());
        }

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

        final Branch.NameKey name =
            new Branch.NameKey(pce.getProject().getNameKey(), refname);
        try {
          final RefUpdate u = repo.updateRef(refname);
          u.setExpectedOldObjectId(ObjectId.zeroId());
          u.setNewObjectId(revid);
          u.setRefLogIdent(ChangeUtil.toPersonIdent(me));
          u.setRefLogMessage("created via web", true);
          final RefUpdate.Result result = u.update(rw);
          switch (result) {
            case FAST_FORWARD:
            case NEW:
            case NO_CHANGE:
              break;
            default:
              log.error("Cannot create branch " + name + ": " + result.name());
              throw new Failure(new IOException(result.name()));
          }
        } catch (IOException err) {
          log.error("Cannot create branch " + name, err);
          throw new Failure(err);
        }

        final Branch.Id id = new Branch.Id(db.nextBranchId());
        final Branch newBranch = new Branch(name, id);
        db.branches().insert(Collections.singleton(newBranch));
        return db.branches().byProject(pce.getProject().getNameKey()).toList();
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
    for (final AccountGroup.Id groupId : Common.getGroupCache().getEffectiveGroups(me)) {
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
