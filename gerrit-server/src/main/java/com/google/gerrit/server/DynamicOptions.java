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

public class DynamicOptions {
  /**
   * To provide additional options, bind a DynamicBean. For example:
   *   bind(com.google.gerrit.server.DynamicOptions.DynamicBean.class)
   *       .annotatedWith(Exports.named(com.google.gerrit.sshd.commands.Query.class))
   *       .to(MyOptions.class);
   *
   * To define the additional options, implement this interface. For example:
   *   public class MyOptions implements DynamicOptions.DynamicBean {
   *     {@literal @}Option(name = "--verbose", aliases = {"-v"}
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

  /**
   * To include options from DynamicBeans, setup a DynamicMap and call this
   * parse method. For example:
   *
   *   DynamicMap.mapOf(binder(), DynamicOptions.DynamicBean.class);
   *
   * ...
   *
   *   protected void parseCommandLine(Object options) throws UnloggedFailure {
   *     final CmdLineParser clp = newCmdLineParser(options);
   *     DynamicOptions.parse(dynamicBeans, clp, options);
   *     ...
   *  }
   */
  public static void parse(DynamicMap<DynamicBean> dynamicBeans,
      CmdLineParser clp, Object bean) {
    for (String plugin : dynamicBeans.plugins()) {
      Provider<DynamicBean> provider = dynamicBeans.byPlugin(plugin)
          .get(bean.getClass().getCanonicalName());
      if (provider != null) {
        DynamicBean dynamicBean = provider.get();
        clp.parseWithPrefix(plugin, dynamicBean);
        if (bean instanceof BeanReceiver) {
          ((BeanReceiver) bean).setDynamicBean(plugin, dynamicBean);
        }
      }
    }
  }
}
