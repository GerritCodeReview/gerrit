/**
@license
Copyright (C) 2016 The Android Open Source Project

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

import '../../shared/gr-autocomplete/gr-autocomplete.js';
import '../../shared/gr-button/gr-button.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import '../../../styles/gr-form-styles.js';
import '../../../styles/shared-styles.js';

const NOTIFICATION_TYPES = [
  {name: 'Changes', key: 'notify_new_changes'},
  {name: 'Patches', key: 'notify_new_patch_sets'},
  {name: 'Comments', key: 'notify_all_comments'},
  {name: 'Submits', key: 'notify_submitted_changes'},
  {name: 'Abandons', key: 'notify_abandoned_changes'},
];

Polymer({
  _template: Polymer.html`
    <style include="shared-styles"></style>
    <style include="gr-form-styles">
      #watchedProjects .notifType {
        text-align: center;
        padding: 0 0.4em;
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
        margin-left: 1em;
      }
      .newFilterInput {
        width: 100%;
      }
    </style>
    <div class="gr-form-styles">
      <table id="watchedProjects">
        <thead>
          <tr>
            <th>Repo</th>
            <template is="dom-repeat" items="[[_getTypes()]]">
              <th class="notifType">[[item.name]]</th>
            </template>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <template is="dom-repeat" items="[[_projects]]" as="project" index-as="projectIndex">
            <tr>
              <td>
                [[project.project]]
                <template is="dom-if" if="[[project.filter]]">
                  <div class="projectFilter">[[project.filter]]</div>
                </template>
              </td>
              <template is="dom-repeat" items="[[_getTypes()]]" as="type">
                <td class="notifControl" on-tap="_handleNotifCellTap">
                  <input type="checkbox" data-index\$="[[projectIndex]]" data-key\$="[[type.key]]" on-change="_handleCheckboxChange" checked\$="[[_computeCheckboxChecked(project, type.key)]]">
                </td>
              </template>
              <td>
                <gr-button link="" data-index\$="[[projectIndex]]" on-tap="_handleRemoveProject">Delete</gr-button>
              </td>
            </tr>
          </template>
        </tbody>
        <tfoot>
          <tr>
            <th>
              <gr-autocomplete id="newProject" query="[[_query]]" threshold="1" placeholder="Repo"></gr-autocomplete>
            </th>
            <th colspan\$="[[_getTypeCount()]]">
              <input id="newFilter" class="newFilterInput" is="iron-input" placeholder="branch:name, or other search expression">
            </th>
            <th>
              <gr-button link="" on-tap="_handleAddProject">Add</gr-button>
            </th>
          </tr>
        </tfoot>
      </table>
    </div>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`,

  is: 'gr-watched-projects-editor',

  properties: {
    hasUnsavedChanges: {
      type: Boolean,
      value: false,
      notify: true,
    },

    _projects: Array,
    _projectsToRemove: {
      type: Array,
      value() { return []; },
    },
    _query: {
      type: Function,
      value() {
        return this._getProjectSuggestions.bind(this);
      },
    },
  },

  loadData() {
    return this.$.restAPI.getWatchedProjects().then(projs => {
      this._projects = projs;
    });
  },

  save() {
    let deletePromise;
    if (this._projectsToRemove.length) {
      deletePromise = this.$.restAPI.deleteWatchedProjects(
          this._projectsToRemove);
    } else {
      deletePromise = Promise.resolve();
    }

    return deletePromise
        .then(() => {
          return this.$.restAPI.saveWatchedProjects(this._projects);
        })
        .then(projects => {
          this._projects = projects;
          this._projectsToRemove = [];
          this.hasUnsavedChanges = false;
        });
  },

  _getTypes() {
    return NOTIFICATION_TYPES;
  },

  _getTypeCount() {
    return this._getTypes().length;
  },

  _computeCheckboxChecked(project, key) {
    return project.hasOwnProperty(key);
  },

  _getProjectSuggestions(input) {
    return this.$.restAPI.getSuggestedProjects(input)
        .then(response => {
          const projects = [];
          for (const key in response) {
            if (!response.hasOwnProperty(key)) { continue; }
            projects.push({
              name: key,
              value: response[key],
            });
          }
          return projects;
        });
  },

  _handleRemoveProject(e) {
    const el = Polymer.dom(e).localTarget;
    const index = parseInt(el.getAttribute('data-index'), 10);
    const project = this._projects[index];
    this.splice('_projects', index, 1);
    this.push('_projectsToRemove', project);
    this.hasUnsavedChanges = true;
  },

  _canAddProject(project, filter) {
    if (!project || !project.id) { return false; }

    // Check if the project with filter is already in the list. Compare
    // filters using == to coalesce null and undefined.
    for (let i = 0; i < this._projects.length; i++) {
      if (this._projects[i].project === project.id &&
          this._projects[i].filter == filter) {
        return false;
      }
    }

    return true;
  },

  _getNewProjectIndex(name, filter) {
    let i;
    for (i = 0; i < this._projects.length; i++) {
      if (this._projects[i].project > name ||
          (this._projects[i].project === name &&
              this._projects[i].filter > filter)) {
        break;
      }
    }
    return i;
  },

  _handleAddProject() {
    const newProject = this.$.newProject.value;
    const newProjectName = this.$.newProject.text;
    const filter = this.$.newFilter.value || null;

    if (!this._canAddProject(newProject, filter)) { return; }

    const insertIndex = this._getNewProjectIndex(newProjectName, filter);

    this.splice('_projects', insertIndex, 0, {
      project: newProjectName,
      filter,
      _is_local: true,
    });

    this.$.newProject.clear();
    this.$.newFilter.bindValue = '';
    this.hasUnsavedChanges = true;
  },

  _handleCheckboxChange(e) {
    const el = Polymer.dom(e).localTarget;
    const index = parseInt(el.getAttribute('data-index'), 10);
    const key = el.getAttribute('data-key');
    const checked = el.checked;
    this.set(['_projects', index, key], !!checked);
    this.hasUnsavedChanges = true;
  },

  _handleNotifCellTap(e) {
    const checkbox = Polymer.dom(e.target).querySelector('input');
    if (checkbox) { checkbox.click(); }
  }
});
