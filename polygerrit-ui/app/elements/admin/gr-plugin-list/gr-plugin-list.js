/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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
    is: 'gr-plugin-list',

    properties: {
      /**
       * URL params passed from the router.
       */
      params: {
        type: Object,
        observer: '_paramsChanged',
      },
      /**
       * Offset of currently visible query results.
       */
      _offset: {
        type: Number,
        value: 0,
      },
      _path: {
        type: String,
        readOnly: true,
        value: '/admin/plugins',
      },
      _plugins: Array,
      /**
       * Because  we request one more than the pluginsPerPage, _shownPlugins
       * maybe one less than _plugins.
       * */
      _shownPlugins: {
        type: Array,
        computed: 'computeShownItems(_plugins)',
      },
      _pluginsPerPage: {
        type: Number,
        value: 25,
      },
      _loading: {
        type: Boolean,
        value: true,
      },
      _filter: {
        type: String,
        value: '',
      },
    },

    behaviors: [
      Gerrit.FireBehavior,
      Gerrit.ListViewBehavior,
    ],

    attached() {
      this.fire('title-change', {title: 'Plugins'});
    },

    _paramsChanged(params) {
      this._loading = true;
      this._filter = this.getFilterValue(params);
      this._offset = this.getOffsetValue(params);

      return this._getPlugins(this._filter, this._pluginsPerPage,
          this._offset);
    },

    _getPlugins(filter, pluginsPerPage, offset) {
      const errFn = response => {
        this.fire('page-error', {response});
      };
      return this.$.restAPI.getPlugins(filter, pluginsPerPage, offset, errFn)
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
            this._loading = false;
          });
    },

    _status(item) {
      return item.disabled === true ? 'Disabled' : 'Enabled';
    },

    _computePluginUrl(id) {
      return this.getUrl('/', id);
    },
  });
})();
