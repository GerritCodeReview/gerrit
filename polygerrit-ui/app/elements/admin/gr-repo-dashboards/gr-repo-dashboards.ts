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

import '../../../styles/shared-styles';
import {flush} from '@polymer/polymer/lib/legacy/polymer.dom';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-repo-dashboards_html';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {customElement, property} from '@polymer/decorators';
import {RepoName, DashboardId, DashboardInfo} from '../../../types/common';
import {firePageError} from '../../../utils/event-util';
import {appContext} from '../../../services/app-context';
import {ErrorCallback} from '../../../api/rest';

interface DashboardRef {
  section: string;
  dashboards: DashboardInfo[];
}

@customElement('gr-repo-dashboards')
export class GrRepoDashboards extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
  static get template() {
    return htmlTemplate;
  }

  @property({type: String, observer: '_repoChanged'})
  repo?: RepoName;

  @property({type: Boolean})
  _loading = true;

  @property({type: Array})
  _dashboards?: DashboardRef[];

  private readonly restApiService = appContext.restApiService;

  _repoChanged(repo?: RepoName) {
    this._loading = true;
    if (!repo) {
      return Promise.resolve();
    }

    const errFn: ErrorCallback = response => {
      firePageError(response);
    };

    return this.restApiService
      .getRepoDashboards(repo, errFn)
      .then((res?: DashboardInfo[]) => {
        if (!res) {
          return;
        }

        // Group by ref and sort by id.
        const dashboards = res.concat
          .apply([], res)
          .sort((a, b) => (a.id < b.id ? -1 : 1));
        const dashboardsByRef: Record<string, DashboardInfo[]> = {};
        dashboards.forEach(d => {
          if (!dashboardsByRef[d.ref]) {
            dashboardsByRef[d.ref] = [];
          }
          dashboardsByRef[d.ref].push(d);
        });

        const dashboardBuilder: DashboardRef[] = [];
        Object.keys(dashboardsByRef)
          .sort()
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

  _getUrl(project: RepoName, id: DashboardId) {
    if (!project || !id) {
      return '';
    }

    return GerritNav.getUrlForRepoDashboard(project, id);
  }

  _computeLoadingClass(loading: boolean) {
    return loading ? 'loading' : '';
  }

  _computeInheritedFrom(project: RepoName, definingProject: RepoName) {
    return project === definingProject ? '' : definingProject;
  }

  _computeIsDefault(isDefault: boolean) {
    return isDefault ? '✓' : '';
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-repo-dashboards': GrRepoDashboards;
  }
}
