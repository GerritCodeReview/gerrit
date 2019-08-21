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

  const HEADER_TYPES = [
    {
      text: 'TO',
      value: 'TO',
    },
    {
      text: 'CC',
      value: 'CC',
    },
    {
      text: 'BCC',
      value: 'BCC',
    },
  ];

  const NOTIFICATION_TYPES = [
    {name: 'Changes', key: 'notify_new_changes'},
    {name: 'Patches', key: 'notify_new_patch_sets'},
    {name: 'Comments', key: 'notify_all_comments'},
    {name: 'Submits', key: 'notify_submitted_changes'},
    {name: 'Abandons', key: 'notify_abandoned_changes'},
  ];

  Polymer({
    is: 'gr-notification-section',

    properties: {
      /** @type {?} */
      section: {
        type: Object,
        notify: true,
      },

      _header_types: {
        type: Array,
        value: () => HEADER_TYPES,
      },

      _notification_types: {
        type: Array,
        value: () => NOTIFICATION_TYPES,
      },

      editing: {
        type: Boolean,
        value: false,
      },

      deleted: {
        type: Boolean,
        value: false,
      },

      name: String,

      _notifications: {
        type: Array,
      },
    },

    observers: [
      '_handleSectionContentChanged(section.*)',
    ],

    _handleSectionContentChanged(val) {
      if (val.path === 'section') {
        this._updateNotifications();
        return;
      }
      this.dispatchEvent(new CustomEvent('section-content-changed',
          {bubbles: true, composed: true}));
    },

    _updateNotifications() {
      const notifications = [];
      for (const notificationType of NOTIFICATION_TYPES) {
        notifications.push({
          name: notificationType.name,
          value: !!this.section[notificationType.key],
        });
      }
      this.set('_notifications', notifications);
    },

    _computeSectionClass(editing, deleted) {
      if (deleted) {
        return 'deleted';
      }
      if (editing) {
        return 'editing';
      }
      return '';
    },

    _handleNotifyTypeCheckboxChange(e) {
      const el = Polymer.dom(e).localTarget;
      const key = el.getAttribute('data-key');
      const checked = el.checked;
      this.set(['section', key], !!checked);
      this.hasUnsavedChanges = true;
    },

    _computeNotificationCheckboxChecked(section, key) {
      return !!section[key];
    },

    _handleRemoveSection() {
      this.dispatchEvent(new CustomEvent('remove-click',
          {bubbles: true, composed: true}));
    },

    _handleUndoRemoveSection() {
      this.dispatchEvent(new CustomEvent('undo-remove-click',
          {bubbles: true, composed: true}));
    },
  });
})();
