package com.google.gerrit.server.git;

import org.eclipse.jgit.lib.Repository;

public interface WithDelegate<T extends Repository> {
  T getDelegate();
}
