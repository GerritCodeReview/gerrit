/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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
import '@polymer/iron-input/iron-input';
import '../../shared/gr-autocomplete/gr-autocomplete';
import '../../shared/gr-button/gr-button';
import '../../../styles/gr-form-styles';
import '../../../styles/shared-styles';
import {dom, EventApi} from '@polymer/polymer/lib/legacy/polymer.dom';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-watched-projects-editor_html';
import {customElement, property} from '@polymer/decorators';
import {
  AutocompleteQuery,
  GrAutocomplete,
  AutocompleteSuggestion,
} from '../../shared/gr-autocomplete/gr-autocomplete';
import {hasOwnProperty} from '../../../utils/common-util';
import {ProjectWatchInfo} from '../../../types/common';
import {appContext} from '../../../services/app-context';
import {IronInputElement} from '@polymer/iron-input';

const NOTIFICATION_TYPES = [
  {name: 'Changes', key: 'notify_new_changes'},
  {name: 'Patches', key: 'notify_new_patch_sets'},
  {name: 'Comments', key: 'notify_all_comments'},
  {name: 'Submits', key: 'notify_submitted_changes'},
  {name: 'Abandons', key: 'notify_abandoned_changes'},
];

export interface GrWatchedProjectsEditor {
  $: {
    newFilter: HTMLInputElement;
    newFilterInput: IronInputElement;
    newProject: GrAutocomplete;
  };
}

@customElement('gr-watched-projects-editor')
export class GrWatchedProjectsEditor extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Boolean, notify: true})
  hasUnsavedChanges = false;

  @property({type: Array})
  _projects?: ProjectWatchInfo[];

  @property({type: Array})
  _projectsToRemove: ProjectWatchInfo[] = [];

  @property({type: Object})
  _query: AutocompleteQuery<string>;

  private readonly restApiService = appContext.restApiService;

  constructor() {
    super();
    this._query = input => this._getProjectSuggestions(input);
  }

  loadData() {
    return this.restApiService.getWatchedProjects().then(projs => {
      this._projects = projs;
    });
  }

  save() {
    let deletePromise: Promise<Response | undefined>;
    if (this._projectsToRemove.length) {
      deletePromise = this.restApiService.deleteWatchedProjects(
        this._projectsToRemove
      );
    } else {
      deletePromise = Promise.resolve(undefined);
    }

    return deletePromise
      .then(() => {
        if (this._projects) {
          return this.restApiService.saveWatchedProjects(this._projects);
        } else {
          return Promise.resolve(undefined);
        }
      })
      .then(projects => {
        this._projects = projects;
        this._projectsToRemove = [];
        this.hasUnsavedChanges = false;
      });
  }

  _getTypes() {
    return NOTIFICATION_TYPES;
  }

  _getTypeCount() {
    return this._getTypes().length;
  }

  _computeCheckboxChecked(project: ProjectWatchInfo, key: string) {
    return hasOwnProperty(project, key);
  }

  _getProjectSuggestions(input: string) {
    return this.restApiService.getSuggestedProjects(input).then(response => {
      const projects: AutocompleteSuggestion[] = [];
      for (const [name, project] of Object.entries(response ?? {})) {
        projects.push({name, value: project.id});
      }
      return projects;
    });
  }

  _handleRemoveProject(e: Event) {
    const el = (dom(e) as EventApi).localTarget as HTMLInputElement;
    const dataIndex = el.getAttribute('data-index');
    if (dataIndex === null || !this._projects) return;
    const index = Number(dataIndex);
    const project = this._projects[index];
    this.splice('_projects', index, 1);
    this.push('_projectsToRemove', project);
    this.hasUnsavedChanges = true;
  }

  _canAddProject(
    project: string | null | undefined,
    text: string | null,
    filter: string | null
  ) {
    if (!project && !text) return false;

    // This will only be used if not using the auto complete
    if (!project && text) {
      return true;
    }

    if (!this._projects) return true;
    // Check if the project with filter is already in the list.
    for (let i = 0; i < this._projects.length; i++) {
      if (
        this._projects[i].project === project &&
        this.areFiltersEqual(this._projects[i].filter, filter)
      ) {
        return false;
      }
    }

    return true;
  }

  _getNewProjectIndex(name: string, filter: string | null) {
    if (!this._projects) return;
    let i;
    for (i = 0; i < this._projects.length; i++) {
      const projectFilter = this._projects[i].filter;
      if (
        this._projects[i].project > name ||
        (this._projects[i].project === name &&
          this.isFilterDefined(projectFilter) &&
          this.isFilterDefined(filter) &&
          projectFilter! > filter!)
      ) {
        break;
      }
    }
    return i;
  }

  _handleAddProject() {
    const newProject = this.$.newProject.value;
    const newProjectName = this.$.newProject.text;
    const filter = this.$.newFilter.value || null;

    if (!this._canAddProject(newProject, newProjectName, filter)) {
      return;
    }

    const insertIndex = this._getNewProjectIndex(newProjectName, filter);

    if (insertIndex !== undefined) {
      this.splice('_projects', insertIndex, 0, {
        project: newProjectName,
        filter,
        _is_local: true,
      });
    }

    this.$.newProject.clear();
    this.$.newFilter.value = '';
    this.hasUnsavedChanges = true;
  }

  _handleCheckboxChange(e: Event) {
    const el = (dom(e) as EventApi).localTarget as HTMLInputElement;
    if (el === null) return;
    const dataIndex = el.getAttribute('data-index');
    const key = el.getAttribute('data-key');
    if (dataIndex === null || key === null) return;
    const index = Number(dataIndex);
    const checked = el.checked;
    this.set(['_projects', index, key], !!checked);
    this.hasUnsavedChanges = true;
  }

  _handleNotifCellClick(e: Event) {
    if (e.target === null) return;
    const checkbox = (e.target as HTMLElement).querySelector('input');
    if (checkbox) {
      checkbox.click();
    }
  }

  isFilterDefined(filter: string | null | undefined) {
    return filter !== null && filter !== undefined;
  }

  areFiltersEqual(
    filter1: string | null | undefined,
    filter2: string | null | undefined
  ) {
    // null and undefined are equal
    if (!this.isFilterDefined(filter1) && !this.isFilterDefined(filter2)) {
      return true;
    }
    return filter1 === filter2;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-watched-projects-editor': GrWatchedProjectsEditor;
  }
}
