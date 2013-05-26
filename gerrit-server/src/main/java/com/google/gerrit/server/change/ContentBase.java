package com.google.gerrit.server.change;

import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.RevisionEdit;
import com.google.inject.Provider;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;

public abstract class ContentBase implements RestReadView<FileResource> {

  protected GitRepositoryManager repoManager;
  protected Provider<CurrentUser> user;

  protected Repository openRepository(FileResource rsrc)
      throws RepositoryNotFoundException, IOException {
    Project.NameKey project =
        rsrc.getRevision().getControl().getProject().getNameKey();
    Repository repo = repoManager.openRepository(project);
    return repo;
  }

  protected RevCommit getCommit(FileResource rsrc, Repository repo, RevWalk rw)
      throws IOException, AuthException {
    if (rsrc.getRevision().getId().isEdit()) {
      RevCommit c =
          new RevisionEdit(checkIdentifiedUser(), rsrc.getRevision().getId())
              .get(repo, rw);
      if (c != null) {
        return c;
      }
    }
    return getPublishedCommit(rsrc, rw);
  }

  protected IdentifiedUser checkIdentifiedUser() throws AuthException {
    CurrentUser u = user.get();
    if (!(u instanceof IdentifiedUser)) {
      throw new AuthException("edits only available to authenticated users");
    }
    return (IdentifiedUser) u;
  }

  protected RevCommit getPublishedCommit(FileResource rsrc, RevWalk rw)
      throws IOException {
    return rw.parseCommit(ObjectId.fromString(rsrc.getRevision().getPatchSet()
        .getRevision().get()));
  }
}
