package com.google.gerrit.server.project;

import com.google.gerrit.server.config.AllProjectsName;
import java.util.Optional;
import org.eclipse.jgit.lib.StoredConfig;

public interface AllProjectsConfigProvider {
  Optional<StoredConfig> get(AllProjectsName allProjectsName);
}
