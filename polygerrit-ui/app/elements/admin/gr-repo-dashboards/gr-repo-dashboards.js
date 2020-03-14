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
import '../../../scripts/bundled-polymer.js';

import '../../../behaviors/fire-behavior/fire-behavior.js';
import '../../../styles/shared-styles.js';
import '../../core/gr-navigation/gr-navigation.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import {flush} from '@polymer/polymer/lib/legacy/polymer.dom.js';
import {mixinBehaviors} from '@polymer/polymer/lib/legacy/class.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-repo-dashboards_html.js';

/**
 * @appliesMixin Gerrit.FireMixin
 * @extends Polymer.Element
 */
class GrRepoDashboards extends mixinBehaviors( [
  Gerrit.FireBehavior,
], GestureEventListeners(
    LegacyElementMixin(
        PolymerElement))) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-repo-dashboards'; }

  static get properties() {
    return {
      repo: {
        type: String,
        observer: '_repoChanged',
      },
      _loading: {
        type: Boolean,
        value: true,
      },
      _dashboards: Array,
    };
  }

  _repoChanged(repo) {
    this._loading = true;
    if (!repo) { return Promise.resolve(); }

    const errFn = response => {
      this.fire('page-error', {response});
    };

    this.$.restAPI.getRepoDashboards(this.repo, errFn).then(res => {
      if (!res) { return Promise.resolve(); }

      // Group by ref and sort by id.
      const dashboards = res.concat.apply([], res).sort((a, b) =>
        (a.id < b.id ? -1 : 1));
      const dashboardsByRef = {};
      dashboards.forEach(d => {
        if (!dashboardsByRef[d.ref]) {
          dashboardsByRef[d.ref] = [];
        }
        dashboardsByRef[d.ref].push(d);
      });

      const dashboardBuilder = [];
      Object.keys(dashboardsByRef).sort()
          .forEach(ref => {
            dashboardBuilder.push({
              section: ref,
              dashboards: dashboardsByRef[ref],
            });
          });

      this._dashboards = dashboardBuilder;
      this._loading = false;
      flush();
    });
  }

  _getUrl(project, id) {
    if (!project || !id) { return ''; }

    return Gerrit.Nav.getUrlForRepoDashboard(project, id);
  }

  _computeLoadingClass(loading) {
    return loading ? 'loading' : '';
  }

  _computeInheritedFrom(project, definingProject) {
    return project === definingProject ? '' : definingProject;
  }

  _computeIsDefault(isDefault) {
    return isDefault ? '✓' : '';
  }
}

customElements.define(GrRepoDashboards.is, GrRepoDashboards);
