/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
(function() {
  'use strict';

  Polymer({
    is: 'gr-repo-plugin-config',

    properties: {
      /** @type {?} */
      repoConfig: {
        type: Object,
        notify: true,
      },
      _repoPluginConfig: Object,
    },

    attached() {
      if (!this.repoConfig && !this.repoConfig.plugin_config) { return; }
      this._repoPluginConfig = Object.keys(this.repoConfig.plugin_config)
       .map(key => {
         const plugin = this.repoConfig.plugin_config[key];
         plugin.name = key;
         return plugin;
       });
    },

    _formatPluginBooleanSelect(item) {
      if (!item) { return; }
      return [
        {
          label: 'Enforced',
          value: 'enforced',
        },
        {
          label: 'True',
          value: 'true',
        }, {
          label: 'False',
          value: 'false',
        },
      ];
    },

    _computePluginValue(conf, conf2, value) {
      if (!conf || !conf2) { return; }
      const name = conf.name;
      const name2 = conf2.key;
      //this.repoConfig.plugin_config[name][name2][value];
      this.set(`repoConfig.plugin_config.${name}.${name2}.value`, value);
      console.log(this.$.pluginSelect + conf.name.bindValue);
      console.log(value);
      console.log(this.repoConfig.plugin_config[name][name2]);

      return value;
      //console.log(conf);
    },

    _toArray(obj) {
      var array = [];
      for (let key in obj) {
        if (obj.hasOwnProperty(key)) {
          array.push({
            key: key,
            val: obj[key]
          });
        }
      }
      return array;
    },
  });
})();
