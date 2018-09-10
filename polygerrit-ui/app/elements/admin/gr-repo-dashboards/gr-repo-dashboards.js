/**
@license
Copyright (C) 2018 The Android Open Source Project

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

import '../../../styles/shared-styles.js';
import '../../core/gr-navigation/gr-navigation.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';

Polymer({
  _template: Polymer.html`
    <style include="shared-styles">
      :host {
        display: block;
        margin-bottom: 2em;
      }
      .loading #dashboards,
      #loadingContainer {
        display: none;
      }
      .loading #loadingContainer {
        display: block;
      }
    </style>
    <style include="gr-table-styles"></style>
    <table id="list" class\$="genericList [[_computeLoadingClass(_loading)]]">
      <tbody><tr class="headerRow">
        <th class="topHeader">Dashboard name</th>
        <th class="topHeader">Dashboard title</th>
        <th class="topHeader">Dashboard description</th>
        <th class="topHeader">Inherited from</th>
        <th class="topHeader">Default</th>
      </tr>
      <tr id="loadingContainer">
        <td>Loading...</td>
      </tr>
      </tbody><tbody id="dashboards">
        <template is="dom-repeat" items="[[_dashboards]]">
          <tr class="groupHeader">
            <td colspan="5">[[item.section]]</td>
          </tr>
          <template is="dom-repeat" items="[[item.dashboards]]">
            <tr class="table">
              <td class="name"><a href\$="[[_getUrl(item.project, item.sections)]]">[[item.path]]</a></td>
              <td class="title">[[item.title]]</td>
              <td class="desc">[[item.description]]</td>
              <td class="inherited">[[_computeInheritedFrom(item.project, item.defining_project)]]</td>
              <td class="default">[[_computeIsDefault(item.is_default)]]</td>
            </tr>
          </template>
        </template>
      </tbody>
    </table>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`,

  is: 'gr-repo-dashboards',

  properties: {
    repo: {
      type: String,
      observer: '_repoChanged',
    },
    _loading: {
      type: Boolean,
      value: true,
    },
    _dashboards: Array,
  },

  _repoChanged(repo) {
    this._loading = true;
    if (!repo) { return Promise.resolve(); }

    const errFn = response => {
      this.fire('page-error', {response});
    };

    this.$.restAPI.getRepoDashboards(this.repo, errFn).then(res => {
      if (!res) { return Promise.resolve(); }

      // Flatten 2 dimenional array, and sort by id.
      const dashboards = res.concat.apply([], res).sort((a, b) =>
          a.id > b.id);
      const customList = dashboards.filter(a => a.ref === 'custom');
      const defaultList = dashboards.filter(a => a.ref === 'default');
      const dashboardBuilder = [];
      if (customList.length) {
        dashboardBuilder.push({
          section: 'Custom',
          dashboards: customList,
        });
      }
      if (defaultList.length) {
        dashboardBuilder.push({
          section: 'Default',
          dashboards: defaultList,
        });
      }

      this._dashboards = dashboardBuilder;
      this._loading = false;
      Polymer.dom.flush();
    });
  },

  _getUrl(project, sections) {
    if (!project || !sections) { return ''; }

    return Gerrit.Nav.getUrlForCustomDashboard(project, sections);
  },

  _computeLoadingClass(loading) {
    return loading ? 'loading' : '';
  },

  _computeInheritedFrom(project, definingProject) {
    return project === definingProject ? '' : definingProject;
  },

  _computeIsDefault(isDefault) {
    return isDefault ? '✓' : '';
  }
});
