// Copyright (C) 2016 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
(function() {
  'use strict';

  var NOTIFICATION_TYPES = [
    {name: 'Changes', key: 'notify_new_changes'},
    {name: 'Patches', key: 'notify_new_patch_sets'},
    {name: 'Comments', key: 'notify_all_comments'},
    {name: 'Submits', key: 'notify_submitted_changes'},
    {name: 'Abandons', key: 'notify_abandoned_changes'},
  ];

  Polymer({
    is: 'gr-watched-projects-editor',

    /**
     * Fired when a watched project is removed from the list.
     *
     * @event project-removed
     */

    properties: {
      projects: Array,
      _query: {
        type: Function,
        value: function() {
          return this._getProjectSuggestions.bind(this);
        },
      },
    },

    _getTypes: function() {
      return NOTIFICATION_TYPES;
    },

    _getTypeCount: function() {
      return this._getTypes().length;
    },

    _computeCheckboxChecked: function(project, key) {
      return project.hasOwnProperty(key);
    },

    _getProjectSuggestions: function(input) {
      return this.$.restAPI.getSuggestedProjects(input)
        .then(function(response) {
          var projects = [];
          for (var key in response) {
            projects.push({
              name: key,
              value: response[key],
            });
          }
          return projects;
        });
    },

    _handleRemoveProject: function(e) {
      var index = parseInt(e.target.getAttribute('data-index'), 10);
      var project = this.projects[index];
      this.splice('projects', index, 1);
      this.fire('project-removed', project);
    },

    _canAddProject: function(project, filter) {
      if (!project || !project.id) { return false; }

      // Check if the project with filter is already in the list. Compare
      // filters using == to coalesce null and undefined.
      for (var i = 0; i < this.projects.length; i++) {
        if (this.projects[i].project === project.id &&
            this.projects[i].filter == filter) {
          return false;
        }
      }

      return true;
    },

    _getNewProjectIndex: function(name, filter) {
      for (var i = 0; i < this.projects.length; i++) {
        if (this.projects[i].project > name ||
            (this.projects[i].project === name &&
                this.projects[i].filter > filter)) {
          break;
        }
      }
      return i;
    },

    _handleAddProject: function() {
      var newProject = this.$.newProject.value;
      var newProjectName = this.$.newProject.text;
      var filter = this.$.newFilter.value || null;

      if (!this._canAddProject(newProject, filter)) { return; }

      var insertIndex = this._getNewProjectIndex(newProjectName, filter);

      this.splice('projects', insertIndex, 0, {
        project: newProjectName,
        filter: filter,
        _is_local: true,
      });

      this.$.newProject.clear();
      this.$.newFilter.bindValue = '';
    },

    _handleCheckboxChange: function(e) {
      var index = parseInt(e.target.getAttribute('data-index'), 10);
      var key = e.target.getAttribute('data-key');
      var checked = e.target.checked;
      this.set(['projects', index, key], !!checked);
    },

    _handleNotifCellTap: function(e) {
      var checkbox = Polymer.dom(e.target).querySelector('input');
      if (checkbox) { checkbox.click(); }
    },
  });
})();
