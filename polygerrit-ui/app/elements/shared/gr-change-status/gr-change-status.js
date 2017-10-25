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

  const CHANGE_STATES = {
    merged: 'Merged',
    abandoned: 'Abandoned',
    merge_conflict: 'Merge Conflict',
    wip: 'WIP',
    private: 'Private',
  };

  Polymer({
    is: 'gr-change-status',

    properties: {
      status: {
        type: String,
        observer: '_updateChipDetails',
      },
      tooltipText: String,
      hasTooltip: {
        type: Boolean,
        reflectToAttribute: true,
        computed: '_determineHasTooltip(title)',
      },
    },

    _determineHasTooltip(title) {
      return !!title;
    },

    _computeStatusString(status) {
      if (status === CHANGE_STATES.wip) {
        return 'Work in Progress';
      }
      return status;
    },

    _toClassName(str) {
      return str.toLowerCase().replace(' ', '-');
    },

    _updateChipDetails(status) {
      this.classList.add(this._toClassName(status));

      switch (status) {
        case CHANGE_STATES.wip:
          this.tooltipText = 'Change is not ready to be reviewed or ' +
              'submitted. Does not appear in dashboards, and email ' +
              'notifications are silenced until review is started.';
          break;
        case CHANGE_STATES.private:
          this.tooltipText = 'Change is only viewable to you and your ' +
              'reviewers (or anyone with View Private Changes permission).';
          break;
      }
    },
  });
})();
