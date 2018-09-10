/**
@license
Copyright (C) 2017 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
import '../../../../@polymer/polymer/polymer-legacy.js';

import '../../../behaviors/gr-list-view-behavior/gr-list-view-behavior.js';
import '../../../styles/gr-table-styles.js';
import '../../../styles/shared-styles.js';
import '../../shared/gr-list-view/gr-list-view.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';

Polymer({
  _template: Polymer.html`
    <style include="shared-styles"></style>
    <style include="gr-table-styles"></style>
    <gr-list-view filter="[[_filter]]" items-per-page="[[_pluginsPerPage]]" items="[[_plugins]]" loading="[[_loading]]" offset="[[_offset]]" path="[[_path]]">
      <table id="list" class="genericList">
        <tbody><tr class="headerRow">
          <th class="name topHeader">Plugin Name</th>
          <th class="version topHeader">Version</th>
          <th class="status topHeader">Status</th>
        </tr>
        <tr id="loading" class\$="loadingMsg [[computeLoadingClass(_loading)]]">
          <td>Loading...</td>
        </tr>
        </tbody><tbody class\$="[[computeLoadingClass(_loading)]]">
          <template is="dom-repeat" items="[[_shownPlugins]]">
            <tr class="table">
              <td class="name">
                <template is="dom-if" if="[[item.index_url]]">
                  <a href\$="[[_computePluginUrl(item.index_url)]]">[[item.id]]</a>
                </template>
                <template is="dom-if" if="[[!item.index_url]]">
                  [[item.id]]
                </template>
              </td>
              <td class="version">[[item.version]]</td>
              <td class="status">[[_status(item)]]</td>
            </tr>
          </template>
        </tbody>
      </table>
    </gr-list-view>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`,

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
  }
});
