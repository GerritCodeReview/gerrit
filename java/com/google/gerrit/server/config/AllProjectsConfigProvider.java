package com.google.gerrit.server.config;

import java.util.Optional;
import org.eclipse.jgit.lib.StoredConfig;

public interface AllProjectsConfigProvider {
  Optional<StoredConfig> get(AllProjectsName allProjectsName);
}
