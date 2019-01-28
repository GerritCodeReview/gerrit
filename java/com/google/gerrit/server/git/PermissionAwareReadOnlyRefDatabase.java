package com.google.gerrit.server.git;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackend.RefFilterOptions;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefRename;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;

/**
 * Wrapper around {@link RefDatabase} that filters all refs using {@link
 * com.google.gerrit.server.permissions.PermissionBackend}.
 */
public class PermissionAwareReadOnlyRefDatabase extends RefDatabase {

  private final PermissionBackend.ForProject forProject;
  private final Repository delegateRepository;

  @Inject
  PermissionAwareReadOnlyRefDatabase(
      Repository delegateRepository, PermissionBackend.ForProject forProject) {
    this.forProject = forProject;
    this.delegateRepository = delegateRepository;
  }

  @Override
  public void create() throws IOException {
    delegateRepository.getRefDatabase().create();
  }

  @Override
  public void close() {
    delegateRepository.getRefDatabase().close();
  }

  @Override
  public boolean isNameConflicting(String name) {
    throw new UnsupportedOperationException("PermissionAwareReadOnlyRefDatabase is read-only");
  }

  @Override
  public RefUpdate newUpdate(String name, boolean detach) {
    throw new UnsupportedOperationException("PermissionAwareReadOnlyRefDatabase is read-only");
  }

  @Override
  public RefRename newRename(String fromName, String toName) {
    throw new UnsupportedOperationException("PermissionAwareReadOnlyRefDatabase is read-only");
  }

  @Override
  public Ref getRef(String name) throws IOException {
    Ref ref = delegateRepository.getRefDatabase().getRef(name);
    if (ref == null) {
      return null;
    }

    Map<String, Ref> result;
    try {
      result =
          forProject.filter(ImmutableList.of(ref), delegateRepository, RefFilterOptions.defaults());
    } catch (PermissionBackendException e) {
      if (e.getCause() instanceof IOException) {
        throw (IOException) e.getCause();
      }
      throw new IOException(e);
    }
    if (result.isEmpty()) {
      return null;
    }
    return Iterables.getOnlyElement(result.values());
  }

  @Override
  public Map<String, Ref> getRefs(String prefix) throws IOException {
    Map<String, Ref> refs = delegateRepository.getRefDatabase().getRefs(prefix);
    if (refs.isEmpty()) {
      return refs;
    }

    Map<String, Ref> result;
    try {
      result = forProject.filter(refs, delegateRepository, RefFilterOptions.defaults());
    } catch (PermissionBackendException e) {
      throw new IOException("");
    }
    return result;
  }

  @Override
  public List<Ref> getAdditionalRefs() throws IOException {
    return delegateRepository.getRefDatabase().getAdditionalRefs();
  }

  @Override
  public Ref peel(Ref ref) throws IOException {
    return delegateRepository.getRefDatabase().peel(ref);
  }
}
