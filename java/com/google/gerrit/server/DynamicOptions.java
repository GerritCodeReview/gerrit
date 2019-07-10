// Copyright (C) 2016 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.server;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.server.plugins.DelegatingClassLoader;
import com.google.gerrit.util.cli.CmdLineParser;
import com.google.gerrit.util.cli.UnknownOptionHandler;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provider;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/** Helper class to define and parse options from plugins on ssh and RestAPI commands. */
public class DynamicOptions {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * To provide additional options, bind a DynamicBean. For example:
   *
   * <pre>
   *   bind(com.google.gerrit.server.DynamicOptions.DynamicBean.class)
   *       .annotatedWith(Exports.named(com.google.gerrit.sshd.commands.Query.class))
   *       .to(MyOptions.class);
   * </pre>
   *
   * To define the additional options, implement this interface. For example:
   *
   * <pre>
   *   public class MyOptions implements DynamicOptions.DynamicBean {
   *     {@literal @}Option(name = "--verbose", aliases = {"-v"}
   *             usage = "Make the operation more talkative")
   *     public boolean verbose;
   *   }
   * </pre>
   *
   * <p>The option will be prefixed by the plugin name. In the example above, if the plugin name was
   * my-plugin, then the --verbose option as used by the caller would be --my-plugin--verbose.
   *
   * <p>Additional options can be annotated with @RequiresOption which will cause them to be ignored
   * unless the required option is present. For example:
   *
   * <pre>
   *   {@literal @}RequiresOptions("--help")
   *   {@literal @}Option(name = "--help-as-json",
   *           usage = "display help text in json format")
   *   public boolean displayHelpAsJson;
   * </pre>
   */
  public interface DynamicBean {}

  /**
   * To provide additional options to a command in another classloader, bind a ClassNameProvider
   * which provides the name of your DynamicBean in the other classLoader.
   *
   * <p>Do this by binding to just the name of the command you are going to bind to so that your
   * classLoader does not load the command's class which likely is not in your classpath. To ensure
   * that the command's class is not in your classpath, you can exclude it during your build.
   *
   * <p>For example:
   *
   * <pre>
   *   bind(com.google.gerrit.server.DynamicOptions.DynamicBean.class)
   *       .annotatedWith(Exports.named( "com.google.gerrit.plugins.otherplugin.command"))
   *       .to(MyOptionsClassNameProvider.class);
   *
   *   static class MyOptionsClassNameProvider implements DynamicOptions.ClassNameProvider {
   *     @Override
   *     public String getClassName() {
   *       return "com.googlesource.gerrit.plugins.myplugin.CommandOptions";
   *     }
   *   }
   * </pre>
   */
  public interface ClassNameProvider extends DynamicBean {
    String getClassName();
  }

  /**
   * To provide additional Guice bindings for options to a command in another classloader, bind a
   * ModulesClassNamesProvider which provides the name of your Modules needed for your DynamicBean
   * in the other classLoader.
   *
   * <p>Do this by binding to the name of the command you are going to bind to and providing an
   * Iterable of Module names to instantiate and add to the Injector used to instantiate the
   * DynamicBean in the other classLoader. For example:
   *
   * <pre>
   *   bind(com.google.gerrit.server.DynamicOptions.DynamicBean.class)
   *       .annotatedWith(Exports.named(
   *           "com.google.gerrit.plugins.otherplugin.command"))
   *       .to(MyOptionsModulesClassNamesProvider.class);
   *
   *   static class MyOptionsModulesClassNamesProvider implements DynamicOptions.ClassNameProvider {
   *     @Override
   *     public String getClassName() {
   *       return "com.googlesource.gerrit.plugins.myplugin.CommandOptions";
   *     }
   *     @Override
   *     public Iterable<String> getModulesClassNames()() {
   *       return "com.googlesource.gerrit.plugins.myplugin.MyOptionsModule";
   *     }
   *   }
   * </pre>
   */
  public interface ModulesClassNamesProvider extends ClassNameProvider {
    Iterable<String> getModulesClassNames();
  }

  /**
   * Implement this if your DynamicBean needs an opportunity to act on the Bean directly before or
   * after argument parsing.
   */
  public interface BeanParseListener extends DynamicBean {
    void onBeanParseStart(String plugin, Object bean);

    void onBeanParseEnd(String plugin, Object bean);
  }

  /**
   * The entity which provided additional options may need a way to receive a reference to the
   * DynamicBean it provided. To do so, the existing class should implement BeanReceiver (a setter)
   * and then provide some way for the plugin to request its DynamicBean (a getter.) For example:
   *
   * <pre>
   *   public class Query extends SshCommand implements DynamicOptions.BeanReceiver {
   *       public void setDynamicBean(String plugin, DynamicOptions.DynamicBean dynamicBean) {
   *         dynamicBeans.put(plugin, dynamicBean);
   *       }
   *
   *       public DynamicOptions.DynamicBean getDynamicBean(String plugin) {
   *         return dynamicBeans.get(plugin);
   *       }
   *   ...
   *   }
   * }
   * </pre>
   */
  public interface BeanReceiver extends UnknownOptionHandler {
    void setDynamicBean(String plugin, DynamicBean dynamicBean);

    /**
     * Returns the class that should be used for looking up exported DynamicBean bindings from
     * plugins. Override when a particular REST/SSH endpoint should respect DynamicBeans bound on a
     * different endpoint. For example, {@code GetDetail} is just a synonym for a variant of {@code
     * GetChange}, and it should respect any DynamicBeans on GetChange. GetChange}. So it should
     * return {@code GetChange.class} from this method.
     */
    default Class<? extends BeanReceiver> getExportedBeanReceiver() {
      return getClass();
    }

    @Override
    default boolean accept(String name, Optional<String> value) {
      // Ignore unknown plugin options, so that callers who set a plugin option do not fail if the
      // plugin is disabled.
      boolean isPluginOption = UnknownOptionHandler.isPluginOption(name);
      if (isPluginOption) {
        logger.atFine().log("Unknown plugin option %s is ignored.", name);
      }
      return isPluginOption;
    }
  }

  public interface BeanProvider {
    DynamicBean getDynamicBean(String plugin);
  }

  /**
   * MergedClassloaders allow us to load classes from both plugin classloaders. Store the merged
   * classloaders in a Map to avoid creating a new classloader for each invocation. Use a
   * WeakHashMap to avoid leaking these MergedClassLoaders once either plugin is unloaded. Since the
   * WeakHashMap only takes care of ensuring the Keys can get garbage collected, use WeakReferences
   * to store the MergedClassloaders in the WeakHashMap.
   *
   * <p>Outter keys are the bean plugin's classloaders (the plugin being extended)
   *
   * <p>Inner keys are the dynamicBeans plugin's classloaders (the extending plugin)
   *
   * <p>The value is the MergedClassLoader representing the merging of the outter and inner key
   * classloaders.
   */
  protected static Map<ClassLoader, Map<ClassLoader, WeakReference<ClassLoader>>> mergedClByCls =
      Collections.synchronizedMap(new WeakHashMap<>());

  protected Object bean;
  protected Map<String, DynamicBean> beansByPlugin;
  protected Injector injector;

  /**
   * Internal: For Gerrit to include options from DynamicBeans, setup a DynamicMap and instantiate
   * this class so the following methods can be called if desired:
   *
   * <pre>
   *    DynamicOptions pluginOptions = new DynamicOptions(bean, injector, dynamicBeans);
   *    pluginOptions.parseDynamicBeans(clp);
   *    pluginOptions.setDynamicBeans();
   *    pluginOptions.onBeanParseStart();
   *
   *    // parse arguments here:  clp.parseArgument(argv);
   *
   *    pluginOptions.onBeanParseEnd();
   * </pre>
   */
  public DynamicOptions(Object bean, Injector injector, DynamicMap<DynamicBean> dynamicBeans) {
    this.bean = bean;
    this.injector = injector;
    beansByPlugin = new HashMap<>();
    Class<?> beanClass =
        (bean instanceof BeanReceiver)
            ? ((BeanReceiver) bean).getExportedBeanReceiver()
            : getClass();
    for (String plugin : dynamicBeans.plugins()) {
      Provider<DynamicBean> provider =
          dynamicBeans.byPlugin(plugin).get(beanClass.getCanonicalName());
      if (provider != null) {
        beansByPlugin.put(plugin, getDynamicBean(bean, provider.get()));
      }
    }
  }

  @SuppressWarnings("unchecked")
  public DynamicBean getDynamicBean(Object bean, DynamicBean dynamicBean) {
    ClassLoader coreCl = getClass().getClassLoader();
    ClassLoader beanCl = bean.getClass().getClassLoader();

    ClassLoader loader = beanCl;
    if (beanCl != coreCl) { // bean from a plugin?
      ClassLoader dynamicBeanCl = dynamicBean.getClass().getClassLoader();
      if (beanCl != dynamicBeanCl) { // in a different plugin?
        loader = getMergedClassLoader(beanCl, dynamicBeanCl);
      }
    }

    String className = null;
    if (dynamicBean instanceof ClassNameProvider) {
      className = ((ClassNameProvider) dynamicBean).getClassName();
    } else if (loader != beanCl) { // in a different plugin?
      className = dynamicBean.getClass().getCanonicalName();
    }

    if (className != null) {
      try {
        List<Module> modules = new ArrayList<>();
        Injector modulesInjector = injector;
        if (dynamicBean instanceof ModulesClassNamesProvider) {
          modulesInjector = injector.createChildInjector();
          for (String moduleName :
              ((ModulesClassNamesProvider) dynamicBean).getModulesClassNames()) {
            Class<Module> mClass = (Class<Module>) loader.loadClass(moduleName);
            modules.add(modulesInjector.getInstance(mClass));
          }
        }
        return modulesInjector
            .createChildInjector(modules)
            .getInstance((Class<DynamicOptions.DynamicBean>) loader.loadClass(className));
      } catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }

    return dynamicBean;
  }

  protected ClassLoader getMergedClassLoader(ClassLoader beanCl, ClassLoader dynamicBeanCl) {
    Map<ClassLoader, WeakReference<ClassLoader>> mergedClByCl = mergedClByCls.get(beanCl);
    if (mergedClByCl == null) {
      mergedClByCl = Collections.synchronizedMap(new WeakHashMap<>());
      mergedClByCls.put(beanCl, mergedClByCl);
    }
    WeakReference<ClassLoader> mergedClRef = mergedClByCl.get(dynamicBeanCl);
    ClassLoader mergedCl = null;
    if (mergedClRef != null) {
      mergedCl = mergedClRef.get();
    }
    if (mergedCl == null) {
      mergedCl = new DelegatingClassLoader(beanCl, dynamicBeanCl);
      mergedClByCl.put(dynamicBeanCl, new WeakReference<>(mergedCl));
    }
    return mergedCl;
  }

  public void parseDynamicBeans(CmdLineParser clp) {
    for (Map.Entry<String, DynamicBean> e : beansByPlugin.entrySet()) {
      clp.parseWithPrefix("--" + e.getKey(), e.getValue());
    }
    clp.drainOptionQueue();
  }

  public void setDynamicBeans() {
    if (bean instanceof BeanReceiver) {
      BeanReceiver receiver = (BeanReceiver) bean;
      for (Map.Entry<String, DynamicBean> e : beansByPlugin.entrySet()) {
        receiver.setDynamicBean(e.getKey(), e.getValue());
      }
    }
  }

  public void onBeanParseStart() {
    for (Map.Entry<String, DynamicBean> e : beansByPlugin.entrySet()) {
      DynamicBean instance = e.getValue();
      if (instance instanceof BeanParseListener) {
        BeanParseListener listener = (BeanParseListener) instance;
        listener.onBeanParseStart(e.getKey(), bean);
      }
    }
  }

  public void onBeanParseEnd() {
    for (Map.Entry<String, DynamicBean> e : beansByPlugin.entrySet()) {
      DynamicBean instance = e.getValue();
      if (instance instanceof BeanParseListener) {
        BeanParseListener listener = (BeanParseListener) instance;
        listener.onBeanParseEnd(e.getKey(), bean);
      }
    }
  }
}
