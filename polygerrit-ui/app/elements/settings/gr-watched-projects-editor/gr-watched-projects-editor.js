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
(function() {
  'use strict';

  const NOTIFICATION_TYPES = [
    {name: 'Changes', key: 'notify_new_changes'},
    {name: 'Patches', key: 'notify_new_patch_sets'},
    {name: 'Comments', key: 'notify_all_comments'},
    {name: 'Submits', key: 'notify_submitted_changes'},
    {name: 'Abandons', key: 'notify_abandoned_changes'},
  ];

  Polymer({
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

    _canAddProject(project, text, filter) {
      if ((!project || !project.id) && !text) { return false; }

      // This will only be used if not using the auto complete
      if (!project && text) { return true; }

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

      if (!this._canAddProject(newProject, newProjectName, filter)) { return; }

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
    },
  });
})();
