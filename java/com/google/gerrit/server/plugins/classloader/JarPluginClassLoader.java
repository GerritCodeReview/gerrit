package com.google.gerrit.server.plugins.classloader;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.server.plugins.Plugin;
import com.google.gerrit.server.plugins.PluginLoader;
import com.google.gerrit.server.plugins.ServerPlugin;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

public class JarPluginClassLoader extends URLClassLoader {
  static {
    registerAsParallelCapable();
  }

  public interface Factory {
    JarPluginClassLoader create(URL[] urls, ClassLoader parent, @Nullable String dependencies);
  }

  private final PluginLoader loader;
  private final Set<String> dependencies;
  private final ClassLoader parent;

  @Inject
  private JarPluginClassLoader(
      PluginLoader loader,
      @Assisted URL[] urls,
      @Assisted ClassLoader parent,
      @Assisted @Nullable String dependencies) {
    super(urls);
    System.out.println("URLs" + Arrays.toString(urls));

    this.parent = parent;
    this.loader = loader;

    ImmutableSet.Builder<String> dependenciesBuilder = ImmutableSet.builder();
    if (dependencies != null)
      for (String dependency : dependencies.split(",")) {
        dependenciesBuilder.add(dependency.trim());
      }
    this.dependencies = dependenciesBuilder.build();
  }

  @Override
  protected Class<?> findClass(String name) throws ClassNotFoundException {
    System.out.println("#findClass: " + name);
    return findClass(name, true);
  }

  private Class<?> findClass(String name, boolean checkGlobal) throws ClassNotFoundException {
    // Cache, Local, Local, Parent (Gerrit), Other plugins

    {
      Optional<Class<?>> result = findLocalClass(name);
      if (result.isPresent()) {
        System.out.println("Found locally! " + name);
        return result.get();
      }
    }
    System.out.println("Was not found locally - " + name);

    if (checkGlobal) {
      Class<?> result;

      Iterable<Plugin> plugins = loader.getPlugins(false);
      for (Plugin p : plugins) {
        if (p instanceof ServerPlugin && dependencies.contains(p.getName())) {
          System.out.println("Found plugin " + p);
          System.out.println("I will check its classloader.");
          ClassLoader loader = ((ServerPlugin) p).getClassLoader();
          if (loader instanceof JarPluginClassLoader) {
            System.out.println("Loader is same type as me :O");
            result = ((JarPluginClassLoader) loader).findLocalClass(name).orElse(null);
          } else {
            System.out.println("Loader is of unknown type.");
            result = loader.loadClass(name);
          }

          if (result != null) {
            System.out.println("And I found it!");
            return result;
          }
        }
      }

      System.out.println("Calling parent for " + name);
      result = parent.loadClass(name);
      if (result != null) {
        System.out.println("Found via parent! " + name);
        return result;
      }
    }
    System.out.println("No class found for " + name);

    throw new ClassNotFoundException(name);
  }

  private Optional<Class<?>> findLocalClass(String name) {
    {
      Class<?> result = super.findLoadedClass(name);
      if (result != null) {
        return Optional.of(result);
      }
    }
    System.out.println("#findLocalClass: " + name);
    try {
      Optional<Class<?>> result = Optional.of(super.findClass(name));
      System.out.println("Found locally! " + name);
      return result;
    } catch (ClassNotFoundException | NoClassDefFoundError e) {
      System.out.println("Not found locally: " + name);
      return Optional.empty();
    }
  }
}
