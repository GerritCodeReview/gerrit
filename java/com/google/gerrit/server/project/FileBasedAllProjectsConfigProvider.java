package com.google.gerrit.server.project;

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import java.util.Optional;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;

public class FileBasedAllProjectsConfigProvider implements AllProjectsConfigProvider {
  private final SitePaths sitePaths;

  @VisibleForTesting
  @Inject
  public FileBasedAllProjectsConfigProvider(SitePaths sitePaths) {
    this.sitePaths = sitePaths;
  }

  @Override
  public Optional<StoredConfig> get(AllProjectsName allProjectsName) {
    return Optional.of(
        new FileBasedConfig(
            sitePaths
                .etc_dir
                .resolve(allProjectsName.get())
                .resolve(ProjectConfig.PROJECT_CONFIG)
                .toFile(),
            FS.DETECTED));
  }
}
