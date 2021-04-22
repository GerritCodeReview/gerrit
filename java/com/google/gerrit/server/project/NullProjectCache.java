package com.google.gerrit.server.project;

import com.google.common.collect.ImmutableSortedSet;
import com.google.gerrit.entities.AccountGroup.UUID;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.exceptions.StorageException;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;

public class NullProjectCache implements ProjectCache {

  @Override
  public ProjectState getAllProjects() {
    throw new UnsupportedOperationException();
  }

  @Override
  public ProjectState getAllUsers() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Optional<ProjectState> get(NameKey projectName) throws StorageException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void evict(Project p) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void evict(NameKey p) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void remove(Project p) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void remove(NameKey name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ImmutableSortedSet<NameKey> all() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Set<UUID> guessRelevantGroupUUIDs() {
    throw new UnsupportedOperationException();
  }

  @Override
  public ImmutableSortedSet<NameKey> byName(String prefix) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void onCreateProject(NameKey newProjectName) throws IOException {
    throw new UnsupportedOperationException();
  }
}
