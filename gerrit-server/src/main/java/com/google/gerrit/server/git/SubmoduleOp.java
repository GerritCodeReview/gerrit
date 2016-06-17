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
// limitations under the License.

package com.google.gerrit.server.git;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;
import com.google.gerrit.common.data.SubscribeSection;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.client.SubmoduleSubscription;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.BatchUpdate.Listener;
import com.google.gerrit.server.git.BatchUpdate.RepoContext;
import com.google.gerrit.server.git.CodeReviewCommit.CodeReviewRevWalk;
import com.google.gerrit.server.git.MergeOpRepoManager.OpenRepo;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gwtorm.server.OrmException;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEditor.DeletePath;
import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.RefSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class SubmoduleOp {

  /**
   * Only used for branches without code review changes
   */
  public static class RepoOnlyOp extends BatchUpdate.RepoOnlyOp {
    SubmoduleOp submoduleOp;
    Branch.NameKey branch;

    public RepoOnlyOp(SubmoduleOp submoduleOp, Branch.NameKey branch) {
      this.submoduleOp = submoduleOp;
      this.branch = branch;
    }

    @Override
    public void updateRepo(RepoContext ctx) throws Exception {
      final CodeReviewCommit c = submoduleOp.composeGitlinksCommit(branch, null);
      ctx.addRefUpdate(new ReceiveCommand(c.getParent(0), c, branch.get()));
      submoduleOp.addBranchTip(branch, c);
    }
  }

  public interface Factory {
    SubmoduleOp create(
        Collection<Branch.NameKey> updatedBranches, MergeOpRepoManager orm);
  }

  private static final Logger log = LoggerFactory.getLogger(SubmoduleOp.class);

  private final GitModules.Factory gitmodulesFactory;
  private final PersonIdent myIdent;
  private final ProjectCache projectCache;
  private final ProjectState.Factory projectStateFactory;
  private final boolean verboseSuperProject;
  private final boolean enableSuperProjectSubscriptions;
  Multimap<Branch.NameKey, SubmoduleSubscription> targets;
  public Collection<Branch.NameKey> updatedBranches;
  private final MergeOpRepoManager orm;
  private final Map<Branch.NameKey, CodeReviewCommit> branchTips;


  @AssistedInject
  public SubmoduleOp(
      GitModules.Factory gitmodulesFactory,
      @GerritPersonIdent PersonIdent myIdent,
      @GerritServerConfig Config cfg,
      ProjectCache projectCache,
      ProjectState.Factory projectStateFactory,
      @Assisted Collection<Branch.NameKey> updatedBranches,
      @Assisted MergeOpRepoManager orm) throws SubmoduleException {
    this.gitmodulesFactory = gitmodulesFactory;
    this.myIdent = myIdent;
    this.projectCache = projectCache;
    this.projectStateFactory = projectStateFactory;
    this.verboseSuperProject = cfg.getBoolean("submodule",
        "verboseSuperprojectUpdate", true);
    this.enableSuperProjectSubscriptions = cfg.getBoolean("submodule",
        "enableSuperProjectSubscriptions", true);
    this.orm = orm;
    this.updatedBranches = updatedBranches;
    this.targets = HashMultimap.create();
    this.branchTips = new HashMap<>();
    calculateSubscriptionMap();
  }

  private void calculateSubscriptionMap() throws SubmoduleException {
    if (!enableSuperProjectSubscriptions) {
      logDebug("Updating superprojects disabled");
      return;
    }

    logDebug("Calculating superprojects - submodules map");
    for (Branch.NameKey updatedBranch : updatedBranches) {
      logDebug("Now processing " + updatedBranch);
      Set<Branch.NameKey> checkedTargets = new HashSet<>();
      Set<Branch.NameKey> targetsToProcess = new HashSet<>();
      targetsToProcess.add(updatedBranch);

      while (!targetsToProcess.isEmpty()) {
        Set<Branch.NameKey> newTargets = new HashSet<>();
        for (Branch.NameKey b : targetsToProcess) {
          try {
            Collection<SubmoduleSubscription> subs =
                superProjectSubscriptionsForSubmoduleBranch(b);
            for (SubmoduleSubscription sub : subs) {
              Branch.NameKey dst = sub.getSuperProject();
              targets.put(dst, sub);
              newTargets.add(dst);
            }
          } catch (IOException e) {
            throw new SubmoduleException("Cannot find superprojects for " + b, e);
          }
        }
        logDebug("adding to done " + targetsToProcess);
        checkedTargets.addAll(targetsToProcess);
        logDebug("completely done with " + checkedTargets);

        Set<Branch.NameKey> intersection = new HashSet<>(checkedTargets);
        intersection.retainAll(newTargets);
        if (!intersection.isEmpty()) {
          throw new SubmoduleException(
              "Possible circular subscription involving " + updatedBranch);
        }

        targetsToProcess = newTargets;
      }
    }
  }

  private Collection<Branch.NameKey> getDestinationBranches(Branch.NameKey src,
      SubscribeSection s) throws IOException {
    Collection<Branch.NameKey> ret = new ArrayList<>();
    logDebug("Inspecting SubscribeSection " + s);
    for (RefSpec r : s.getRefSpecs()) {
      logDebug("Inspecting ref " + r);
      if (r.matchSource(src.get())) {
        if (r.getDestination() == null) {
          // no need to care for wildcard, as we matched already
          try {
            orm.openRepo(s.getProject(), false);
          } catch (NoSuchProjectException e) {
            // A project listed a non existent project to be allowed
            // to subscribe to it. Allow this for now.
            continue;
          }
          OpenRepo or = orm.getRepo(s.getProject());
          for (Ref ref : or.repo.getRefDatabase().getRefs(
              RefNames.REFS_HEADS).values()) {
            ret.add(new Branch.NameKey(s.getProject(), ref.getName()));
          }
        } else if (r.isWildcard()) {
          // refs/heads/*:refs/heads/*
          ret.add(new Branch.NameKey(s.getProject(),
              r.expandFromSource(src.get()).getDestination()));
        } else {
          // e.g. refs/heads/master:refs/heads/stable
          ret.add(new Branch.NameKey(s.getProject(), r.getDestination()));
        }
      }
    }
    logDebug("Returning possible branches: " + ret +
        "for project " + s.getProject());
    return ret;
  }

  private Collection<SubmoduleSubscription>
      superProjectSubscriptionsForSubmoduleBranch(Branch.NameKey srcBranch)
      throws IOException {
    logDebug("Calculating possible superprojects for " + srcBranch);
    Collection<SubmoduleSubscription> ret = new ArrayList<>();
    Project.NameKey srcProject = srcBranch.getParentKey();
    ProjectConfig cfg = projectCache.get(srcProject).getConfig();
    for (SubscribeSection s : projectStateFactory.create(cfg)
        .getSubscribeSections(srcBranch)) {
      logDebug("Checking subscribe section " + s);
      Collection<Branch.NameKey> branches =
          getDestinationBranches(srcBranch, s);
      for (Branch.NameKey targetBranch : branches) {
        Project.NameKey targetProject = targetBranch.getParentKey();
        try {
          orm.openRepo(targetProject, false);
          OpenRepo or = orm.getRepo(targetProject);
          ObjectId id = or.repo.resolve(targetBranch.get());
          if (id == null) {
            logDebug("The branch " + targetBranch + " doesn't exist.");
            continue;
          }
        } catch (NoSuchProjectException e) {
          logDebug("The project " + targetProject + " doesn't exist");
          continue;
        }
        GitModules m = gitmodulesFactory.create(targetBranch, orm);
        for (SubmoduleSubscription ss : m.subscribedTo(srcBranch)) {
          logDebug("Checking SubmoduleSubscription " + ss);
          if (projectCache.get(ss.getSubmodule().getParentKey()) != null) {
            logDebug("Adding SubmoduleSubscription " + ss);
            ret.add(ss);
          }
        }
      }
    }
    logDebug("Calculated superprojects for " + srcBranch + " are " + ret);
    return ret;
  }

  public void updateSuperProjects() throws SubmoduleException {
    SetMultimap<Project.NameKey, Branch.NameKey> dst = branchesByProject();
    LinkedHashSet<Project.NameKey> projects = getProjectsInOrder();
    try {
      for (Project.NameKey project : projects) {
        // get a new BatchUpdate for the project
        orm.openRepo(project, false);
        orm.getRepo(project).resetUpdate();
        for (Branch.NameKey branch : dst.get(project)) {
          SubmoduleOp.RepoOnlyOp op = new SubmoduleOp.RepoOnlyOp(this, branch);
          orm.getRepo(project).getUpdate().addRepoOnlyOp(op);
        }
      }
      BatchUpdate.execute(orm.batchUpdates(projects), new Listener());
    } catch (RestApiException | UpdateException | IOException | NoSuchProjectException e) {
      throw new SubmoduleException("Cannot update gitlinks", e);
    }
  }

  /**
   * Create a gitlink update commit on the tip of subscriber or modify the
   * {@param baseCommit} with gitlink update patch
   */
  public CodeReviewCommit composeGitlinksCommit(
      final Branch.NameKey subscriber, RevCommit baseCommit)
      throws IOException, SubmoduleException, OrmException {
    PersonIdent author = null;
    StringBuilder msgbuf = new StringBuilder("Update git submodules\n\n");
    boolean sameAuthorForAll = true;

    try {
      orm.openRepo(subscriber.getParentKey(), false);
    } catch (NoSuchProjectException | IOException e) {
      throw new SubmoduleException("Cannot access superproject", e);
    }

    OpenRepo or = orm.getRepo(subscriber.getParentKey());
    Ref r = or.repo.exactRef(subscriber.get());
    if (r == null) {
      throw new SubmoduleException(
          "The branch was probably deleted from the subscriber repository");
    }

    RevCommit currentCommit = (baseCommit != null) ? baseCommit :
        or.rw.parseCommit(or.repo.exactRef(subscriber.get()).getObjectId());
    or.rw.parseBody(currentCommit);

    DirCache dc = readTree(or.rw, currentCommit);
    DirCacheEditor ed = dc.editor();

    for (SubmoduleSubscription s : targets.get(subscriber)) {
      try {
        orm.openRepo(s.getSubmodule().getParentKey(), false);
      } catch (NoSuchProjectException | IOException e) {
        throw new SubmoduleException("Cannot access submodule", e);
      }
      OpenRepo subOr = orm.getRepo(s.getSubmodule().getParentKey());
      Repository subRepo = subOr.repo;

      Ref ref = subRepo.getRefDatabase().exactRef(s.getSubmodule().get());
      if (ref == null) {
        ed.add(new DeletePath(s.getPath()));
        continue;
      }

      ObjectId updateTo = ref.getObjectId();
      if (branchTips.containsKey(s.getSubmodule())) {
        updateTo = branchTips.get(s.getSubmodule());
      }
      RevWalk subOrRw = subOr.rw;
      final RevCommit newCommit = subOrRw.parseCommit(updateTo);

      subOrRw.parseBody(newCommit);
      if (author == null) {
        author = newCommit.getAuthorIdent();
      } else if (!author.equals(newCommit.getAuthorIdent())) {
        sameAuthorForAll = false;
      }

      DirCacheEntry dce = dc.getEntry(s.getPath());
      ObjectId oldId;
      if (dce != null) {
        if (!dce.getFileMode().equals(FileMode.GITLINK)) {
          String errMsg = "Requested to update gitlink " + s.getPath() + " in "
              + s.getSubmodule().getParentKey().get() + " but entry "
              + "doesn't have gitlink file mode.";
          throw new SubmoduleException(errMsg);
        }
        oldId = dce.getObjectId();
      } else {
        // This submodule did not exist before. We do not want to add
        // the full submodule history to the commit message, so omit it.
        oldId = updateTo;
      }

      ed.add(new PathEdit(s.getPath()) {
        @Override
        public void apply(DirCacheEntry ent) {
          ent.setFileMode(FileMode.GITLINK);
          ent.setObjectId(newCommit.getId());
        }
      });
      if (verboseSuperProject) {
        msgbuf.append("Project: " + s.getSubmodule().getParentKey().get());
        msgbuf.append(" " + s.getSubmodule().getShortName());
        msgbuf.append(" " + newCommit.getName());
        msgbuf.append("\n\n");

        try {
          subOrRw.resetRetain(subOr.canMergeFlag);
          subOrRw.markStart(newCommit);
          subOrRw.markUninteresting(subOrRw.parseCommit(oldId));
          for (RevCommit c : subOrRw) {
            subOrRw.parseBody(c);
            msgbuf.append(c.getFullMessage() + "\n\n");
          }
        } catch (IOException e) {
          throw new SubmoduleException("Could not perform a revwalk to "
              + "create superproject commit message", e);
        }
      }
    }
    ed.finish();


    ObjectInserter oi = or.repo.newObjectInserter();
    CodeReviewRevWalk rw = or.rw;
    ObjectId tree = dc.writeTree(oi);

    if (!sameAuthorForAll || author == null) {
      author = myIdent;
    }

    CommitBuilder commit = new CommitBuilder();
    commit.setTreeId(tree);
    if (baseCommit != null) {
      // modify the baseCommit
      commit.setParentIds(baseCommit.getParents());
      commit.setMessage(baseCommit.getFullMessage() + "\n\n" + msgbuf.toString());
      commit.setAuthor(baseCommit.getAuthorIdent());
    } else {
      // create a new commit
      commit.setParentId(currentCommit);
      commit.setMessage(msgbuf.toString());
      commit.setAuthor(author);
    }
    commit.setCommitter(myIdent);

    ObjectId id = oi.insert(commit);
    oi.flush();
    return rw.parseCommit(id);
  }

  private static DirCache readTree(RevWalk rw, ObjectId base)
      throws IOException {
    final DirCache dc = DirCache.newInCore();
    final DirCacheBuilder b = dc.builder();
    b.addTree(new byte[0], // no prefix path
        DirCacheEntry.STAGE_0, // standard stage
        rw.getObjectReader(), rw.parseTree(base));
    b.finish();
    return dc;
  }

  public SetMultimap<Project.NameKey, Branch.NameKey> branchesByProject() {
    SetMultimap<Project.NameKey, Branch.NameKey> ret = HashMultimap.create();
    for (Branch.NameKey branch : targets.keySet()) {
      ret.put(branch.getParentKey(), branch);
    }

    return ret;
  }

  public LinkedHashSet<Project.NameKey> getProjectsInOrder()
      throws SubmoduleException {
    Multimap<Project.NameKey, Project.NameKey> projectGraph =
        getProjectSubscriptionGraph();
    return topoSort(projectGraph);
  }

  public Set<Branch.NameKey> getAllBranches()
      throws SubmoduleException {
    Set<Branch.NameKey> branches = new HashSet<>();
    branches.addAll(targets.keySet());
    branches.addAll(updatedBranches);
    return branches;
  }

  /**
   * Extract the branch dependency graph from the subscriptions
   * @return branch dependency graph
   */
  public Multimap<Branch.NameKey, Branch.NameKey> getBranchSubscriptionGraph() {
    Multimap<Branch.NameKey, Branch.NameKey>  graph =
        HashMultimap.create();
    for (Branch.NameKey b : targets.keySet()) {
      Collection<SubmoduleSubscription> ss = targets.get(b);
      for (SubmoduleSubscription s : ss) {
        graph.put(b, s.getSubmodule());
      }
    }

    return graph;
  }

  /**
   * Extract the project dependency graph from the subscriptions
   * @return project dependency graph
   */
  public Multimap<Project.NameKey, Project.NameKey> getProjectSubscriptionGraph() {
    Multimap<Project.NameKey, Project.NameKey>  graph =
        HashMultimap.create();
    for (Branch.NameKey b : targets.keySet()) {
      Collection<SubmoduleSubscription> ss = targets.get(b);
      for (SubmoduleSubscription s : ss) {
        graph.put(b.getParentKey(), s.getSubmodule().getParentKey());
      }
    }

    return graph;
  }

  /**
   * Sort the projects by topological order
   *
   * @param graph the project dependency graph
   * @return sorted projects list from deepest submodule project to top level
   * super project
   */
  public static <T> LinkedHashSet<T> topoSort(Multimap<T, T> graph)
      throws SubmoduleException {
    LinkedHashSet<T> sorted = new LinkedHashSet<>();
    for (T p : graph.keySet()) {
      if (!sorted.contains(p)) {
        topoSortHelper(p, new HashSet<T>(), sorted, graph);
      }
    }
    return sorted;
  }

  private static <T> void topoSortHelper(T current, Set<T> currentVisited,
      LinkedHashSet<T> sorted, Multimap<T, T> graph) throws SubmoduleException {
    if (currentVisited.contains(current)) {
      throw new SubmoduleException(
          "Cyclic subscription in project: " + current);
    }

    if (!sorted.contains(current)) {
      currentVisited.add(current);
      for (T child : graph.get(current)) {
        topoSortHelper(child, currentVisited, sorted, graph);
      }
      currentVisited.remove(current);
      sorted.add(current);
    }
  }

  public void addBranchTip(Branch.NameKey branch, CodeReviewCommit tip) {
    branchTips.put(branch, tip);
  }

  private void logDebug(String msg, Object... args) {
    if (log.isDebugEnabled()) {
      log.debug("[" + orm.getSubmissionId() + "]" + msg, args);
    }
  }
}
