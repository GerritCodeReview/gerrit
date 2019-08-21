/**
 * @license
 * Copyright (C) 2019 The Android Open Source Project
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
    is: 'gr-repo-notifications',

    properties: {
      repo: {
        type: String,
        observer: '_repoChanged',
      },

      _editing: {
        type: Boolean,
        value: false,
      },

      _loading: {
        type: Boolean,
        value: true,
      },

      _saving: {
        type: Boolean,
        value: false,
      },

      _notifications: {
        type: Array,
        observer: '_notificationsChanged',
      },

      _editableNotifications: {
        type: Array,
      },
    },

    behaviors: [
      Gerrit.FireBehavior,
    ],

    _computeLoadingClass(loading) {
      return loading ? 'loading' : '';
    },

    _computeMainClass(editing) {
      return editing ? 'editing' : '';
    },

    _repoChanged(repo) {
      this._loading = true;
      return this._reload(repo);
    },

    _editOrCancel(editing) {
      return editing ? 'Cancel' : 'Edit';
    },

    _reload(repo) {
      if (!repo) {
        this._loading = false;
        return Promise.resolve();
      }

      const errFn = response => {
        this.fire('page-error', {response});
      };

      return this.$.restAPI.notifications
          .getRepoNotifications(this.repo, errFn)
          .then(res => {
            this._loading = false;
            if (!res) {
              this._notifications = [];
              return Promise.resolve();
            }
            this._description = res.description;
            this._notifications = this._getNotifications(res.notify_configs);
          });
    },

    _handleEdit() {
      this._editing = true;
    },

    _handleSave() {
      this._saving = true;
      const changes = this._collectNotificationChanges();
      const errFn = response => {
        this.fire('server-error', {response});
      };
      // TODO: remove _saving in case of error
      this.$.restAPI.notifications.changeRepoNotifications(this.repo, this._description, changes.removals, changes.additions, errFn).then((res) => {
        this._saving = false;
        if (!res) {
          return;
        }
        this._editing = false;
      });
    },

    _handleCancel() {
      this._editing = false;
      for (let i = this._editableNotifications.length - 1; i >= 0; i--) {
        const notification = this._editableNotifications[i];
        if (notification.added) {
          this.splice('_editableNotifications', i, 1);
          continue;
        }
        if (notification.deleted) {
          this.set(['_editableNotifications', i, 'deleted'], false);
        }
        if (notification.modified) {
          this.set(['_editableNotifications', i, 'editable_data'],
              this._createEditableData(notification.original_data));
          this.set(['_editableNotifications', i, 'modified'], false);
        }
      }
    },

    _getNotifications(notifyConfig) {
      const result = [];
      for (const notificationName in notifyConfig) {
        const notification = notifyConfig[notificationName];
        result.push({
          name: notificationName,
          filter: notification.filter,
          header: notification.header,
          emails: notification.emails,
          groups: notification.groups,
          notify_new_changes: !!notification.notify_new_changes,
          notify_new_patch_sets: !!notification.notify_new_patch_sets,
          notify_all_comments: !!notification.notify_all_comments,
          notify_submitted_changes: !!notification.notify_submitted_changes,
          notify_abandoned_changes: !!notification.notify_abandoned_changes,
        });
      }
      return result;
      //return [{name: 'abc', filter: 'sadfsadfsdafsf', header: 'BCC', emails: ['abc@def'], groups: ['group1', 'group2']}  ];
    },

    _handleCreateNotification() {
      this.push('_editableNotifications', {
        editable_data: {name: '', filter: '', header: 'BCC', emails: [], groups: []},
        original_data: null,
        modified: false,
        added: true,
        deleted: false,
      });
      Polymer.dom.flush();
    },

    _handleRemoveNotification(e) {
      const el = Polymer.dom(e).localTarget;
      const index = parseInt(el.getAttribute('data-index'), 10);
      const notification = this._editableNotifications[index];
      if (notification.added) {
        this.splice('_editableNotifications', index, 1);
        return;
      }
      this.set(['_editableNotifications', index, 'editable_data'],
          this._createEditableData(notification.original_data));
      this.set(['_editableNotifications', index, 'modified'], false);
      this.set(['_editableNotifications', index, 'deleted'], true);
    },

    _handleUndoRemoveNotification(e) {
      const el = Polymer.dom(e).localTarget;
      const index = parseInt(el.getAttribute('data-index'), 10);
      this.set(['_editableNotifications', index, 'deleted'], false);
    },

    _notificationsChanged() {
      this._editableNotifications = this._createEditableItems();
    },

    _handleNotificationChanged(e) {
      this._getNotification(e).modified = true;
    },

    _getNotificationIndex(e) {

    },

    _getNotification(e) {
      const el = Polymer.dom(e).localTarget;
      const index = parseInt(el.getAttribute('data-index'), 10);
      return this._editableNotifications[index];
      // const editable_data = e.detail.section;
      // for (const notification of this._editableNotifications) {
      //   if (notification.original_data.name === editable_data.id) {
      //     return notification;
      //   }
      // }
      // return null;
    },

    _createEditableItems() {
      return this._notifications.map(notification => {
        return {
          editable_data: this._createEditableData(notification),
          original_data: notification,
          modified: false,
          added: false,
          deleted: false,
        };
      });
    },

    _createEditableData(originalData) {
      return {
        id: originalData.name,
        name: originalData.name,
        filter: originalData.filter,
        header: originalData.header,
        emails: originalData.emails.map(email => { return {email}; }),
        groups: originalData.groups,

        notify_new_changes: originalData.notify_new_changes,
        notify_new_patch_sets: originalData.notify_new_patch_sets,
        notify_all_comments: originalData.notify_all_comments,
        notify_submitted_changes: originalData.notify_submitted_changes,
        notify_abandoned_changes: originalData.notify_abandoned_changes,

      };
    },

    _collectNotificationChanges() {
      const notificationsToDelete = [];
      const notificationsToAdd = [];
      for (const item of this._editableNotifications) {
        if (item.added) {
          notificationsToAdd.push(this._getNotificationConfigInfo(item.editable_data));
          continue;
        }
        if (item.deleted) {
          notificationsToDelete.push(item.original_data.name);
          continue;
        }
        if (item.modified) {
          notificationsToDelete.push(item.original_data.name);
          notificationsToAdd.push(this._getNotificationConfigInfo(item.editable_data));
        }
      }
      return {
        removals: notificationsToDelete,
        additions: notificationsToAdd,
      };
    },

    _getNotificationConfigInfo(editableData) {
      let filter = editableData.filter;
      if (filter) {
        filter = filter.trim();
        if (filter === '') {
          filter = null;
        }
      }
      return {
        name: editableData.name,
        filter,
        header: editableData.header,
        emails: editableData.emails.map(emailInfo => emailInfo.email),
        group_ids: editableData.groups.map(groupInfo => groupInfo.id),
        notify_new_changes: editableData.notify_new_changes,
        notify_new_patch_sets: editableData.notify_new_patch_sets,
        notify_all_comments: editableData.notify_all_comments,
        notify_submitted_changes: editableData.notify_submitted_changes,
        notify_abandoned_changes: editableData.notify_abandoned_changes,
      };
    }


  });
})();
