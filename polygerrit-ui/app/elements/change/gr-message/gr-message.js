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

  Polymer({
    is: 'gr-message',

    /**
     * Fired when this message's permalink is tapped.
     *
     * @event scroll-to
     */

    /**
     * Fired when this message's reply link is tapped.
     *
     * @event reply
     */

    listeners: {
      'tap': '_handleTap',
    },

    properties: {
      changeNum: Number,
      message: Object,
      comments: {
        type: Object,
        observer: '_commentsChanged',
      },
      expanded: {
        type: Boolean,
        value: true,
        reflectToAttribute: true,
      },
      showAvatar: {
        type: Boolean,
        value: false,
      },
      showReplyButton: {
        type: Boolean,
        value: false,
      },
      projectConfig: Object,
    },

    ready: function() {
      this.$.restAPI.getConfig().then(function(cfg) {
        this.showAvatar = !!(cfg && cfg.plugin && cfg.plugin.has_avatars) &&
            this.message && this.message.author;
      }.bind(this));
    },

    _commentsChanged: function(value) {
      this.expanded = Object.keys(value || {}).length > 0;
    },

    _handleTap: function(e) {
      if (this.expanded) { return; }
      this.expanded = true;
    },

    _handleNameTap: function(e) {
      if (!this.expanded) { return; }
      e.stopPropagation();
      this.expanded = false;
    },

    _computeClass: function(expanded, showAvatar) {
      var classes = [];
      classes.push(expanded ? 'expanded' : 'collapsed');
      classes.push(showAvatar ? 'showAvatar' : 'hideAvatar');
      return classes.join(' ');
    },

    _computeMessageHash: function(message) {
      return '#message-' + message.id;
    },

    _handleLinkTap: function(e) {
      e.preventDefault();

      this.fire('scroll-to', {message: this.message}, {bubbles: false});

      var hash = this._computeMessageHash(this.message);
      // Don't add the hash to the window history if it's already there.
      // Otherwise you mess up expected back button behavior.
      if (window.location.hash == hash) { return; }
      // Change the URL but don’t trigger a nav event. Otherwise it will
      // reload the page.
      page.show(window.location.pathname + hash, null, false);
    },

    _handleReplyTap: function(e) {
      e.preventDefault();
      this.fire('reply', {message: this.message});
    },
  });
})();
