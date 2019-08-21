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

      _readonly: {
        type: Boolean,
        computed: '_computeReadOnly(_editing, _saving)',
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

    _computeReadOnly(_editing, _saving) {
      return _saving || !_editing;
    },

    _repoChanged(repo) {
      this._loading = true;
      this._editing = false;
      this._saving = false;
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
              this._description = null;
              return Promise.resolve();
            }
            // API requires to send description back even if it is not changed.
            this._description = res.description;
            this._notifications = this._getNotifications(res.notify_configs);
          });
    },

    _getNotifications(notifyConfig) {
      const result = [];
      for (const notificationName in notifyConfig) {
        if (!notifyConfig.hasOwnProperty((notificationName))) {
          continue;
        }
        const notification = notifyConfig[notificationName];
        result.push({
          editable_data: this._createEditableData(notification),
          original_data: notification,
          modified: false,
          deleted: false,
        });
      }
      result.sort((a, b) => a.localeCompare(b));
      return result;
    },

    _handleEdit() {
      this._editing = true;
    },

    _handleCancel() {
      this._editing = false;
      for (let i = this._notifications.length - 1; i >= 0; i--) {
        const notification = this._notifications[i];
        if (!notification.original_data) {
          // Newly added notification
          this.splice('_notifications', i, 1);
          continue;
        }
        if (notification.deleted) {
          this.set(['_notifications', i, 'deleted'], false);
        }
        if (notification.modified) {
          this.set(['_notifications', i, 'editable_data'],
              this._createEditableData(notification.original_data));
          this.set(['_notifications', i, 'modified'], false);
        }
      }
    },

    _handleAddNotification() {
      this.push('_notification', {
        editable_data: {
          name: '',
          filter: '',
          header: 'BCC',
          emails: [],
          groups: [],
        },
        original_data: null,
        modified: false,
        deleted: false,
      });
    },

    _handleRemoveNotification(e) {
      const el = Polymer.dom(e).localTarget;
      const index = parseInt(el.getAttribute('data-index'), 10);
      const notification = this._editableNotifications[index];
      if (!notification.original_data) {
        this.splice('_notifications', index, 1);
        return;
      }
      this.set(['_notifications', index, 'editable_data'],
          this._createEditableData(notification.original_data));
      this.set(['_notifications', index, 'modified'], false);
      this.set(['_notifications', index, 'deleted'], true);
    },

    _handleUndoRemoveNotification(e) {
      const index = this._getNotificiationIndexFromEvent(e);
      this.set(['_notifications', index, 'deleted'], false);
    },

    _handleNotificationChanged(e) {
      const index = this._getNotificiationIndexFromEvent(e);
      this._set(['_notifications', index, 'modified'], true);
    },

    _getNotificiationIndexFromEvent(e) {
      const el = Polymer.dom(e).localTarget;
      return parseInt(el.getAttribute('data-index'), 10);
    },

    _createEditableData(originalData) {
      const emails = originalData.emails
          .map(emailInfo => { return {email: emailInfo.email}; });
      const groups = originalData.groups
          .map(groupInfo=> {
            return {id: groupInfo.group_id, name: groupInfo.name};
          });
      return {
        name: originalData.name,
        filter: originalData.filter,
        header: originalData.header,
        emails,
        groups,
        notification_types: {
          notify_new_changes: originalData.notify_new_changes,
          notify_new_patch_sets: originalData.notify_new_patch_sets,
          notify_all_comments: originalData.notify_all_comments,
          notify_submitted_changes: originalData.notify_submitted_changes,
          notify_abandoned_changes: originalData.notify_abandoned_changes,
        },
      };
    },

    _handleSave() {
      this._saving = true;
      const changes = this._collectNotificationChanges();
      const errFn = response => {
        this.fire('server-error', {response});
      };
      this.$.restAPI.notifications.changeRepoNotifications(
          this.repo,
          this._description,
          changes.removals,
          changes.additions,
          errFn)
          .then(res => {
            this._saving = false;
            if (!res) {
              return;
            }
            this._editing = false;
          });
    },

    _collectNotificationChanges() {
      const notificationsToDelete = [];
      const notificationsToAdd = [];
      for (const item of this._editableNotifications) {
        if (!item.original_data) {
          notificationsToAdd.push(
              this._getNotificationConfigInfo(item.editable_data));
          continue;
        }
        if (item.deleted) {
          notificationsToDelete.push(item.original_data.name);
          continue;
        }
        if (item.modified) {
          notificationsToDelete.push(item.original_data.name);
          notificationsToAdd.push(
              this._getNotificationConfigInfo(item.editable_data));
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
      return Object.assign({
        name: editableData.name,
        filter,
        header: editableData.header,
        emails: editableData.emails.map(emailInfo => emailInfo.email),
        group_ids: editableData.groups.map(groupInfo => groupInfo.id),
      }, editableData.notification_types);
    },
  });
})();
