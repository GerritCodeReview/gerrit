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
import {customElement, property, query} from 'lit/decorators';
import {
  AutocompleteQuery,
  GrAutocomplete,
  AutocompleteSuggestion,
} from '../../shared/gr-autocomplete/gr-autocomplete';
import {assertIsDefined} from '../../../utils/common-util';
import {ProjectWatchInfo, RepoName} from '../../../types/common';
import {getAppContext} from '../../../services/app-context';
import {css, html, LitElement} from 'lit';
import {sharedStyles} from '../../../styles/shared-styles';
import {formStyles} from '../../../styles/gr-form-styles';
import {when} from 'lit/directives/when';
import {fire} from '../../../utils/event-util';
import {PropertiesOfType} from '../../../utils/type-util';

type NotificationKey = PropertiesOfType<Required<ProjectWatchInfo>, boolean>;

const NOTIFICATION_TYPES: Array<{name: string; key: NotificationKey}> = [
  {name: 'Changes', key: 'notify_new_changes'},
  {name: 'Patches', key: 'notify_new_patch_sets'},
  {name: 'Comments', key: 'notify_all_comments'},
  {name: 'Submits', key: 'notify_submitted_changes'},
  {name: 'Abandons', key: 'notify_abandoned_changes'},
];

@customElement('gr-watched-projects-editor')
export class GrWatchedProjectsEditor extends LitElement {
  // Private but used in tests.
  @query('#newFilter')
  newFilter?: HTMLInputElement;

  // Private but used in tests.
  @query('#newProject')
  newProject?: GrAutocomplete;

  @property({type: Boolean})
  hasUnsavedChanges = false;

  @property({type: Array})
  projects?: ProjectWatchInfo[];

  @property({type: Array})
  projectsToRemove: ProjectWatchInfo[] = [];

  private readonly query: AutocompleteQuery = input =>
    this.getProjectSuggestions(input);

  private readonly restApiService = getAppContext().restApiService;

  static override get styles() {
    return [
      sharedStyles,
      formStyles,
      css`
        #watchedProjects .notifType {
          text-align: center;
          padding: 0 var(--spacing-s);
        }
        .notifControl {
          cursor: pointer;
          text-align: center;
        }
        .notifControl:hover {
          outline: 1px solid var(--border-color);
        }
        .projectFilter {
          color: var(--deemphasized-text-color);
          font-style: italic;
          margin-left: var(--spacing-l);
        }
        .newFilterInput {
          width: 100%;
        }
      `,
    ];
  }

  override render() {
    const types = NOTIFICATION_TYPES;
    return html` <div class="gr-form-styles">
      <table id="watchedProjects">
        <thead>
          <tr>
            <th>Repo</th>
            ${types.map(type => html`<th class="notifType">${type.name}</th>`)}
            <th></th>
          </tr>
        </thead>
        <tbody>
          ${(this.projects ?? []).map(project => this.renderProject(project))}
        </tbody>
        <tfoot>
          <tr>
            <th>
              <gr-autocomplete
                id="newProject"
                query=${this.query}
                threshold="1"
                allow-non-suggested-values
                tab-complete
                placeholder="Repo"
              ></gr-autocomplete>
            </th>
            <th colspan=${types.length}>
              <iron-input id="newFilterInput" class="newFilterInput">
                <input
                  id="newFilter"
                  class="newFilterInput"
                  placeholder="branch:name, or other search expression"
                />
              </iron-input>
            </th>
            <th>
              <gr-button link="" @click=${this.handleAddProject}>Add</gr-button>
            </th>
          </tr>
        </tfoot>
      </table>
    </div>`;
  }

  private renderProject(project: ProjectWatchInfo) {
    const types = NOTIFICATION_TYPES;
    return html` <tr>
      <td>
        ${project.project}
        ${when(
          project.filter,
          () => html`<div class="projectFilter">${project.filter}</div>`
        )}
      </td>
      ${types.map(type => this.renderNotifyControl(project, type.key))}
      <td>
        <gr-button
          link=""
          @click=${(_e: Event) => this.handleRemoveProject(project)}
          >Delete</gr-button
        >
      </td>
    </tr>`;
  }

  private renderNotifyControl(project: ProjectWatchInfo, key: NotificationKey) {
    return html` <td class="notifControl" @click=${this.handleNotifCellClick}>
      <input
        type="checkbox"
        data-key=${key}
        @change=${(e: Event) => this.handleCheckboxChange(project, key, e)}
        ?checked=${!!project[key]}
      />
    </td>`;
  }

  loadData() {
    return this.restApiService.getWatchedProjects().then(projs => {
      this.projects = projs;
    });
  }

  save() {
    let deletePromise: Promise<Response | undefined>;
    if (this.projectsToRemove.length) {
      deletePromise = this.restApiService.deleteWatchedProjects(
        this.projectsToRemove
      );
    } else {
      deletePromise = Promise.resolve(undefined);
    }

    return deletePromise
      .then(() => {
        if (this.projects) {
          return this.restApiService.saveWatchedProjects(this.projects);
        } else {
          return Promise.resolve(undefined);
        }
      })
      .then(projects => {
        this.projects = projects;
        this.projectsToRemove = [];
        this.setHasUnsavedChanges(false);
      });
  }

  // private but used in tests.
  getProjectSuggestions(input: string) {
    return this.restApiService.getSuggestedProjects(input).then(response => {
      const projects: AutocompleteSuggestion[] = [];
      for (const [name, project] of Object.entries(response ?? {})) {
        projects.push({name, value: project.id});
      }
      return projects;
    });
  }

  private handleRemoveProject(project: ProjectWatchInfo) {
    if (!this.projects) return;
    const index = this.projects.indexOf(project);
    if (index < 0) return;
    this.projects.splice(index, 1);
    this.projectsToRemove.push(project);
    this.requestUpdate();
    this.setHasUnsavedChanges(true);
  }

  // private but used in tests.
  canAddProject(
    project: string | null,
    text: string | null,
    filter: string | null
  ) {
    if (project === null && text === null) {
      return false;
    }

    // This will only be used if not using the auto complete
    if (!project && text) {
      return true;
    }

    if (!this.projects) return true;
    // Check if the project with filter is already in the list.
    for (let i = 0; i < this.projects.length; i++) {
      if (
        this.projects[i].project === project &&
        this.areFiltersEqual(this.projects[i].filter, filter)
      ) {
        return false;
      }
    }

    return true;
  }

  // private but used in tests.
  getNewProjectIndex(name: string, filter: string | null) {
    if (!this.projects) return;
    let i;
    for (i = 0; i < this.projects.length; i++) {
      const projectFilter = this.projects[i].filter;
      if (
        this.projects[i].project > name ||
        (this.projects[i].project === name &&
          this.isFilterDefined(projectFilter) &&
          this.isFilterDefined(filter) &&
          projectFilter! > filter!)
      ) {
        break;
      }
    }
    return i;
  }

  // Private but used in tests.
  handleAddProject() {
    assertIsDefined(this.newProject, 'newProject');
    assertIsDefined(this.newFilter, 'newFilter');
    const newProject = this.newProject.value;
    const newProjectName = this.newProject.text as RepoName;
    const filter = this.newFilter.value;

    if (!this.canAddProject(newProject, newProjectName, filter)) {
      return;
    }

    const insertIndex = this.getNewProjectIndex(newProjectName, filter);

    if (insertIndex !== undefined) {
      this.projects?.splice(insertIndex, 0, {
        project: newProjectName,
        filter,
        _is_local: true,
      });
      this.requestUpdate();
    }

    this.newProject.clear();
    this.newFilter.value = '';
    this.setHasUnsavedChanges(true);
  }

  private handleCheckboxChange(
    project: ProjectWatchInfo,
    key: NotificationKey,
    e: Event
  ) {
    const el = e.target as HTMLInputElement;
    const checked = el.checked;
    project[key] = !!checked;
    this.requestUpdate();
    this.setHasUnsavedChanges(true);
  }

  private handleNotifCellClick(e: Event) {
    if (e.target === null) return;
    const checkbox = (e.target as HTMLElement).querySelector('input');
    if (checkbox) {
      checkbox.click();
    }
  }

  private setHasUnsavedChanges(value: boolean) {
    this.hasUnsavedChanges = value;
    fire(this, 'has-unsaved-changes-changed', {value});
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
