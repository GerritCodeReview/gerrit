package com.google.gerrit.sshd.commands;

import com.google.gerrit.common.PlugInClassLoader;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.sshd.CommandModule;
import com.google.gerrit.sshd.CommandName;
import com.google.gerrit.sshd.Commands;
import com.google.inject.Inject;
import com.google.inject.ProvisionException;

import org.apache.sshd.server.Command;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;

import java.io.IOException;
import java.util.Set;

public class MasterPluginsModule extends CommandModule {

  @Inject
  private SitePaths site;

  @Inject
  private PlugInClassLoader plugInClassLoader;

  @Override
  protected void configure() {
    Config config = loadConfig();
    final CommandName gerrit = Commands.named("gerrit");
    Set<String> sshCommandNames = config.getNames("plugins");
    for (String name : sshCommandNames) {
      String clazz = config.getString("plugins", null, name);
      try {
        Class<?> c = plugInClassLoader.loadClass(clazz);
        if (Command.class.isAssignableFrom(c)) {
          command(gerrit, name).to((Class<Command>) c);
        } else {
          System.out.println("Class " + clazz + " is not subtype of org.apache.sshd.server.Command");
        }
      } catch (ClassNotFoundException e) {
        throw new ProvisionException("Could not load plugin '" + name + "'", e);
      }
    }
  }

  private Config loadConfig() {
    FileBasedConfig config = new FileBasedConfig(site.gerrit_config, FS.DETECTED);
    try {
      config.load();
      return config;
    } catch (IOException e) {
      throw new ProvisionException(e.getMessage(), e);
    } catch (ConfigInvalidException e) {
      throw new ProvisionException(e.getMessage(), e);
    }
  }

}
