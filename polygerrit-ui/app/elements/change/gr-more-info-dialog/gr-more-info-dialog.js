// Copyright (C) 2017 The Android Open Source Project
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
    is: 'gr-more-info-dialog',

    /**
     * Fired when the user presses the close button.
     *
     * @event close
     */

    properties: {
      change: Object,
      commitInfo: Object,
    },

    hostAttributes: {
      role: 'dialog',
    },

    behaviors: [
      Gerrit.BaseUrlBehavior,
      Gerrit.PatchSetBehavior,
      Gerrit.URLEncodingBehavior,
    ],

    _handleCloseTap(e) {
      e.preventDefault();
      this.fire('close', null, {bubbles: false});
    },

    _getCommitOwnerOrCommitterUrl(owner) {
      if (!owner && !owner.email) {
        return '';
      }
      return this.getBaseUrl() + '/q/owner:' +
          this.encodeURL(owner.email, false);
    },

    _getCommitOwnerOrCommitter(owner) {
      const ownerName = owner && owner.name ? owner.name : '';
      const ownerEmail = owner && owner.email ? ' <' + owner.email + '>' : '';
      return ownerName + ownerEmail;
    },

    _computeCommitter(change) {
      if (!change.current_revision ||
          !change.revisions[change.current_revision]) {
        return '';
      }

      const rev = change.revisions[change.current_revision];

      if (!rev || !rev.uploader) {
        return '';
      }

      return rev.uploader;
    },

    _commitWebLink(link) {
      if (!link.web_links) {
        return '';
      }
      const webLinks = link.web_links;
      return webLinks.length ? webLinks : null;
    },
  });
})();
