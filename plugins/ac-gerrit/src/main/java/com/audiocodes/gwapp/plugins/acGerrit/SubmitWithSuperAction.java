package com.audiocodes.gwapp.plugins.acGerrit;

import com.google.common.base.Joiner;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.git.LocalDiskRepositoryManager;
import com.google.gerrit.server.restapi.change.Submit;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.RepoContext;
import com.google.gerrit.server.update.RepoOnlyOp;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.submodule.SubmoduleWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

class SubmitWithSuperAction
    implements RestModifyView<RevisionResource, SubmitWithSuperAction.Input>,
        UiAction<RevisionResource> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final BatchUpdate.Factory updateFactory;
  private final LocalDiskRepositoryManager repoManager;
  private final PersonIdent serverIdent;
  private final Submit submit;
  private static final Pattern gwappIssuePattern = Pattern.compile("Issue: ([A-Z\\d]{2,}-\\d+)");
  private static final Pattern ippIssuePattern = Pattern.compile("\\[([A-Z\\d]{2,}-\\d+)\\]");

  static class Input {}

  public class SubmoduleException extends Exception {
    private static final long serialVersionUID = 1L;

    SubmoduleException(final String msg) {
      super(msg, null);
    }

    SubmoduleException(final String msg, final Throwable why) {
      super(msg, why);
    }
  }

  private Pattern getIssuePattern(String project) {
    return project == "IPP/SFB" ? ippIssuePattern : gwappIssuePattern;
  }

  @Inject
  SubmitWithSuperAction(
      BatchUpdate.Factory updateFactory,
      LocalDiskRepositoryManager repoManager,
      Submit submit,
      @GerritPersonIdent PersonIdent serverIdent) {
    this.updateFactory = updateFactory;
    this.repoManager = repoManager;
    this.submit = submit;
    this.serverIdent = serverIdent;
  }

  private RevCommit commitForBranch(Repository repo, RevWalk rw, BranchNameKey branch)
      throws IOException {
    Ref r = repo.exactRef(branch.branch());
    if (r != null) return rw.parseCommit(r.getObjectId());
    return null;
  }

  public void updateSubmodule(
      final BranchNameKey subBranch, IdentifiedUser caller, String superProjectName)
      throws IOException, SubmoduleException, UpdateException, RestApiException {
    final Project.NameKey superProject = Project.nameKey(superProjectName);
    final BranchNameKey superBranch = BranchNameKey.create(superProject, subBranch.branch());
    final Project.NameKey subProject = subBranch.project();
    final boolean updateOpenRGUGW = subProject.get().equals("TP/OpenSource/acLinuxUGW");
    Project.NameKey openRGUGWProject = null;
    BranchNameKey openRGUGWBranch = null;
    RevCommit openRGUGWCommit = null;
    if (updateOpenRGUGW) {
      openRGUGWProject = Project.nameKey("TP/OpenSource/OpenRG-UGW");
      openRGUGWBranch = BranchNameKey.create(openRGUGWProject, subBranch.branch());

      try (final Repository openRGUGWRepo = repoManager.openRepository(openRGUGWProject);
          final ObjectInserter ins = openRGUGWRepo.newObjectInserter()) {
        ObjectReader objReader = ins.newReader();
        final RevWalk rw = new RevWalk(objReader);
        final RevCommit subCommit = commitForBranch(openRGUGWRepo, rw, openRGUGWBranch);

        openRGUGWCommit =
            createCommit(
                caller,
                openRGUGWProject,
                openRGUGWBranch,
                openRGUGWRepo,
                ins,
                rw,
                subCommit,
                "Empty commit due to change in acLinuxUGW",
                subCommit.getTree().getId());
      }
    }
    try (final Repository subRepo = repoManager.openRepository(subProject);
        final Repository superRepo = repoManager.openRepository(superProject);
        final ObjectInserter ins = superRepo.newObjectInserter()) {
      ObjectReader objReader = ins.newReader();
      final RevWalk subRw = new RevWalk(subRepo);
      final RevCommit subCommit = commitForBranch(subRepo, subRw, subBranch);

      final RevWalk superRw = new RevWalk(objReader);

      RevCommit superCommit = commitForBranch(superRepo, superRw, superBranch);

      String path = getSubmodulePath(subBranch, superRepo, superCommit);

      StringBuilder commitMsg = new StringBuilder("Update " + path + " submodule\n");
      DirCache dc = readTree(superRw, superCommit);
      DirCacheEditor ed = dc.editor();
      if (openRGUGWCommit != null) {
        final String openRGUGWPath = getSubmodulePath(openRGUGWBranch, superRepo, superCommit);
        setSubmodule(superProject, openRGUGWCommit, openRGUGWPath, dc, ed);
      }
      DirCacheEntry dce = setSubmodule(superProject, subCommit, path, dc, ed);
      final RevCommit oldCommit = subRw.parseCommit(dce.getObjectId());
      subRw.parseBody(subCommit);
      ed.finish();
      createSubmoduleCommitMsg(commitMsg, subRw, subCommit, oldCommit, superProjectName);
      final ObjectId newTreeId = dc.writeTree(ins);

      // Gitlinks are already in the branch, return null
      if (newTreeId.equals(superCommit.getTree())) {
        throw new SubmoduleException("Tree unchanged");
      }

      createCommit(
          caller,
          superProject,
          superBranch,
          superRepo,
          ins,
          superRw,
          superCommit,
          commitMsg.toString(),
          newTreeId);
    }
  }

  private DirCacheEntry setSubmodule(
      final Project.NameKey superProject,
      final RevCommit subCommit,
      String path,
      DirCache dc,
      DirCacheEditor ed)
      throws SubmoduleException {
    DirCacheEntry dce = dc.getEntry(path);
    if (dce != null) {
      if (!dce.getFileMode().equals(FileMode.GITLINK)) {
        String errMsg =
            "Requested to update gitlink "
                + path
                + " in "
                + superProject
                + " but entry doesn't have gitlink file mode.";
        throw new SubmoduleException(errMsg);
      }
    }

    ed.add(
        new PathEdit(path) {
          @Override
          public void apply(DirCacheEntry ent) {
            ent.setFileMode(FileMode.GITLINK);
            ent.setObjectId(subCommit.getId());
          }
        });
    return dce;
  }

  private RevCommit createCommit(
      IdentifiedUser caller,
      final Project.NameKey project,
      final BranchNameKey branch,
      final Repository repo,
      final ObjectInserter ins,
      final RevWalk rw,
      RevCommit parentCommit,
      String commitMsg,
      ObjectId newTreeId)
      throws UpdateException, RestApiException, IOException {
    PersonIdent author = caller.newCommitterIdent(new Date(), serverIdent.getTimeZone());
    CommitBuilder commit = new CommitBuilder();
    commit.setTreeId(newTreeId);
    commit.setParentId(parentCommit);
    commit.setMessage(commitMsg.toString());
    commit.setAuthor(author);
    commit.setCommitter(author);
    ObjectId id = ins.insert(commit);
    final RevCommit finalCommit = rw.parseCommit(id);
    if (finalCommit == null) return null;
    try (BatchUpdate bu = updateFactory.create(project, caller, TimeUtil.nowTs())) {
      bu.setRepository(repo, rw, ins);
      bu.addRepoOnlyOp(
          new RepoOnlyOp() {
            @Override
            public void updateRepo(RepoContext ctx) throws Exception {
              ctx.addRefUpdate(
                  new ReceiveCommand(finalCommit.getParent(0), finalCommit, branch.branch()));
            }
          });
      bu.execute();
    }
    return finalCommit;
  }

  private String getSubmodulePath(
      final BranchNameKey subBranch, final Repository superRepo, RevCommit superCommit)
      throws IOException, SubmoduleException {
    try (SubmoduleWalk subWalk = new SubmoduleWalk(superRepo)) {
      AnyObjectId treeId = superCommit.getTree().getId();
      subWalk.setTree(treeId);
      subWalk.setRootTree(treeId);
      while (subWalk.next()) {
        String subUrl = subWalk.getModulesUrl();
        String subProjectName = subBranch.project().get();
        subProjectName = subProjectName.substring(subProjectName.lastIndexOf('/') + 1);
        if (subUrl.startsWith(".") && subUrl.endsWith("/" + subProjectName))
          return subWalk.getPath();
      }
    } catch (ConfigInvalidException e) {
      logger.atSevere().withCause(e).log("Invalid submodule configuration");
    }
    throw new SubmoduleException(
        "Repository " + subBranch.project().get() + " is not registered as a submodule");
  }

  private void createSubmoduleCommitMsg(
      StringBuilder msgbuf, RevWalk subRw, RevCommit newCommit, RevCommit oldCommit, String project)
      throws SubmoduleException {
    // newly created submodule gitlink, do not append whole history
    if (oldCommit == null) {
      return;
    }

    try {
      subRw.resetRetain(subRw.newFlag("CAN_MERGE"));
      subRw.markStart(newCommit);
      subRw.markUninteresting(oldCommit);
      TreeSet<String> allIssues = new TreeSet<>();
      for (RevCommit c : subRw) {
        subRw.parseBody(c);
        String subject = c.getShortMessage();
        String fullMessage = c.getFullMessage();
        Matcher m1 = getIssuePattern(project).matcher(fullMessage);
        ArrayList<String> issues = new ArrayList<>();
        while (m1.find()) {
          final String issue = m1.group(1);
          issues.add(issue);
          allIssues.add(issue);
        }
        msgbuf.append("\n- " + subject);
        if (!issues.isEmpty() && !project.equals("IPP/SFB"))
          msgbuf.append(" (" + Joiner.on(", ").join(issues) + ")");
      }
      if (!allIssues.isEmpty()) msgbuf.append("\n");
      for (String issue : allIssues) {
        msgbuf.append("\nIssue: " + issue);
      }
    } catch (IOException e) {
      throw new SubmoduleException(
          "Could not perform a revwalk to " + "create superproject commit message", e);
    }
  }

  private static DirCache readTree(RevWalk rw, ObjectId base) throws IOException {
    final DirCache dc = DirCache.newInCore();
    final DirCacheBuilder b = dc.builder();
    b.addTree(
        new byte[0], // no prefix path
        DirCacheEntry.STAGE_0, // standard stage
        rw.getObjectReader(),
        rw.parseTree(base));
    b.finish();
    return dc;
  }

  @Override
  public Response<String> apply(RevisionResource rsrc, Input input)
      throws ResourceConflictException {
    try {
      submit.apply(rsrc, new SubmitInput());
      ChangeResource changeRsrc = rsrc.getChangeResource();
      IdentifiedUser caller = changeRsrc.getUser().asIdentifiedUser();
      Change change = changeRsrc.getChange();
      final String projectName = change.getProject().get();
      final String parentProject = getParentProjectName(projectName);
      if (parentProject == null)
        throw new Exception("Could not determine parent project for " + projectName);
      updateSubmodule(change.getDest(), caller, parentProject);
    } catch (Exception e) {
      logger.atSevere().withCause(e).log("Error");
      throw new ResourceConflictException(e.getMessage());
    }
    return Response.ok("Done");
  }

  private String getParentProjectName(String project) {
    if (project.startsWith("TP/OpenSource") || project.equals("TP/TrunkPackLib")) {
      if (project.contains("u-boot")) return null;
      return "TP/GWApp";
    }
    if (project.equals("IPP/apps/emsc")
        || project.startsWith("IPP/RX50")
        || project.startsWith("IPP/Lib/Lib")
        || project.startsWith("IPP/C450/C450")
        || project.startsWith("IPP/445")
        || project.startsWith("IPP/OpenSource")) {
      return "IPP/SFB";
    }
    return null;
  }

  @Override
  public Description getDescription(RevisionResource resource) {
    final String project = resource.getChange().getProject().get();
    Description button = submit.getDescription(resource);
    if (button == null) return null;

    final String parentProject = getParentProjectName(project);
    if (parentProject != null) {
      button
          .setLabel("Submit-and-update-" + parentProject)
          .setTitle("Submit and update reference in the supermodule");
    } else return null;
    return button;
  }
}
