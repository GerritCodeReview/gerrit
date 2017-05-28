// Copyright (C) 2017 The Android Open Source Project
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
(function() {
  'use strict';

  Polymer({
    is: 'gr-admin-plugin-list',

    properties: {
      /**
       * URL params passed from the router.
       */
      params: {
        type: Object,
        observer: '_paramsChanged',
      },

      _plugins: Array,
    },

    behaviors: [
      Gerrit.BaseUrlBehavior,
      Gerrit.URLEncodingBehavior,
    ],

    _paramsChanged(value) {
      return this.$.restAPI.getPlugins()
          .then(plugins => {
            if (!plugins) {
              this._plugins = [];
              return;
            }
            this._plugins = Object.keys(plugins)
             .map(key => {
               const plugin = plugins[key];
               plugin.name = key;
               return plugin;
             });
          });
    },

    _status(item) {
      return item.disabled === true ? 'Disabled' : 'Enabled';
    },

    _getUrl(item) {
      return this.getBaseUrl() + '/' + this.encodeURL(item, true);
    },
  });
})();
