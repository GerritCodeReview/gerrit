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

    _getUrl(url) {
      if (!url) { return ''; }

      return Gerrit.Nav.navigateToRelativeUrl(url);
    },

    _computeLoadingClass(loading) {
      return loading ? 'loading' : '';
    },

    _computeInheritedFrom(project, definingProject) {
      return project === definingProject ? '' : definingProject;
    },

    _computeIsDefault(isDefault) {
      return isDefault ? '✓' : '';
    },
  });
})();
