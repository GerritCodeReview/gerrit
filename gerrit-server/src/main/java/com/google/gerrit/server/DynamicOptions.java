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

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.util.cli.CmdLineParser;
import com.google.inject.Provider;

import org.kohsuke.args4j.CmdLineException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
  * Helper class to define and parse options from plugins on ssh and
  * RestAPI commands.
  */
public class DynamicOptions {
  /**
   * To provide additional options, bind a DynamicBean. For example:
   *   bind(com.google.gerrit.server.DynamicOptions.DynamicBean.class)
   *       .annotatedWith(Exports.named(com.google.gerrit.sshd.commands.Query.class))
   *       .to(MyOptions.class);
   *
   * To define the additional options, implement this interface. For example:
   *   public class MyOptions implements DynamicOptions.DynamicBean {
   *     \@Option(name = "--verbose", aliases = {"-v"}
   *             usage = "Make the operation more talkative")
   *     public boolean verbose;
   *   }
   *
   * The option will be prefixed by the plugin name. In the example above,
   * if the plugin name was my-plugin, then the --verbose option as used by
   * the caller would be --my-plugin--verbose.
   */
  public interface DynamicBean {
  }

  /**
   * Implement this if your DynamicBean needs an opportunity to act on the
   * Bean directly before or after argument parsing.
   */
  public interface BeanParseListener extends DynamicBean {
    void onBeanParseStart(String plugin, Object bean);
    void onBeanParseEnd(String plugin, Object bean);
  }

  /**
   * The entity which provided additional options may need a way to
   * receive a reference to the DynamicBean it provided. To do so,
   * the existing class should implement BeanReceiver (a setter) and then
   * provide some way for the plugin to request its DynamicBean (a getter.)
   * For example:
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
   }
   */
  public interface BeanReceiver {
    void setDynamicBean(String plugin, DynamicBean dynamicBean);
  }

  Object bean;
  Map<String, DynamicBean> beansByPlugin;

  /**
   * Internal: For Gerrit to include options from DynamicBeans, setup a
   * DynamicMap and instantiate this class so the following methods can
   * be called if desired:
   *
   *    DynamicOptions pluginOptions = new DynamicOptions(bean, dynamicBeans);
   *    pluginOptions.parseDynamicBeans(clp);
   *    pluginOptions.setDynamicBeans();
   *    pluginOptions.onBeanParseStart();
   *
   *    // parse arguments here:  clp.parseArgument(argv);
   *
   *    pluginOptions.onBeanParseEnd();
   */
  public DynamicOptions(Object bean, DynamicMap<DynamicBean> dynamicBeans) {
    this.bean = bean;
    beansByPlugin = new HashMap<String, DynamicBean>();
    for (String plugin : dynamicBeans.plugins()) {
      Provider<DynamicBean> provider = dynamicBeans.byPlugin(plugin)
          .get(bean.getClass().getCanonicalName());
      if (provider != null) {
        beansByPlugin.put(plugin, provider.get());
      }
    }
  }

  public void parseDynamicBeans(CmdLineParser clp) {
    for (Entry<String, DynamicBean> e : beansByPlugin.entrySet()) {
      clp.parseWithPrefix(e.getKey(), e.getValue());
    }
  }

  public void setDynamicBeans() {
    if (bean instanceof BeanReceiver) {
      BeanReceiver receiver = (BeanReceiver) bean;
      for (Entry<String, DynamicBean> e : beansByPlugin.entrySet()) {
        receiver.setDynamicBean(e.getKey(), e.getValue());
      }
    }
  }

  public void onBeanParseStart() {
    for (Entry<String, DynamicBean> e : beansByPlugin.entrySet()) {
      DynamicBean instance = e.getValue();
      if (instance instanceof BeanParseListener) {
        BeanParseListener listener = (BeanParseListener) instance;
        listener.onBeanParseStart(e.getKey(), bean);
      }
    }
  }

  public void onBeanParseEnd() {
    for (Entry<String, DynamicBean> e : beansByPlugin.entrySet()) {
      DynamicBean instance = e.getValue();
      if (instance instanceof BeanParseListener) {
        BeanParseListener listener = (BeanParseListener) instance;
        listener.onBeanParseEnd(e.getKey(), bean);
      }
    }
  }
}
