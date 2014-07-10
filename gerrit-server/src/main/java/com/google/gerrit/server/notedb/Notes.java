package com.google.gerrit.server.notedb;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.VersionedMetaData;
import com.google.gwtorm.server.OrmException;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;

/** View of contents at a single ref related to some change. **/
public abstract class Notes<T> extends VersionedMetaData {
  protected boolean loaded;
  protected GitRepositoryManager repoManager;
  protected Change change;

  public T load() throws OrmException {
    if (!loaded) {
      Repository repo;
      try {
        repo = repoManager.openRepository(change.getProject());
      } catch (IOException e) {
        throw new OrmException(e);
      }
      try {
        load(repo);
        loaded = true;
      } catch (ConfigInvalidException | IOException e) {
        throw new OrmException(e);
      } finally {
        repo.close();
      }
    }
    return getThis();
  }

  protected abstract T getThis();
}
