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

  const CHANGE_SIZE = {
    XS: 10,
    SMALL: 50,
    MEDIUM: 250,
    LARGE: 1000,
  };

  /**
   * @appliesMixin Gerrit.BaseUrlMixin
   * @appliesMixin Gerrit.ChangeTableMixin
   * @appliesMixin Gerrit.PathListMixin
   * @appliesMixin Gerrit.RESTClientMixin
   * @appliesMixin Gerrit.URLEncodingMixin
   */
  class GrChangeListItem extends Polymer.mixinBehaviors( [
    Gerrit.BaseUrlBehavior,
    Gerrit.ChangeTableBehavior,
    Gerrit.PathListBehavior,
    Gerrit.RESTClientBehavior,
    Gerrit.URLEncodingBehavior,
  ], Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element))) {
    static get is() { return 'gr-change-list-item'; }

    static get properties() {
      return {
        visibleChangeTableColumns: Array,
        labelNames: {
          type: Array,
        },

        /** @type {?} */
        change: Object,
        changeURL: {
          type: String,
          computed: '_computeChangeURL(change)',
        },
        statuses: {
          type: Array,
          computed: 'changeStatuses(change)',
        },
        showStar: {
          type: Boolean,
          value: false,
        },
        showNumber: Boolean,
        _changeSize: {
          type: String,
          computed: '_computeChangeSize(change)',
        },
        _dynamicCellEndpoints: {
          type: Array,
        },
      };
    }

    attached() {
      super.attached();
      Gerrit.awaitPluginsLoaded().then(() => {
        this._dynamicCellEndpoints = Gerrit._endpoints.getDynamicEndpoints(
            'change-list-item-cell');
      });
    }

    _computeChangeURL(change) {
      return Gerrit.Nav.getUrlForChange(change);
    }

    _computeLabelTitle(change, labelName) {
      const label = change.labels[labelName];
      if (!label) { return 'Label not applicable'; }
      const significantLabel = label.rejected || label.approved ||
          label.disliked || label.recommended;
      if (significantLabel && significantLabel.name) {
        return labelName + '\nby ' + significantLabel.name;
      }
      return labelName;
    }

    _computeLabelClass(change, labelName) {
      const label = change.labels[labelName];
      // Mimic a Set.
      const classes = {
        cell: true,
        label: true,
      };
      if (label) {
        if (label.approved) {
          classes['u-green'] = true;
        }
        if (label.value == 1) {
          classes['u-monospace'] = true;
          classes['u-green'] = true;
        } else if (label.value == -1) {
          classes['u-monospace'] = true;
          classes['u-red'] = true;
        }
        if (label.rejected) {
          classes['u-red'] = true;
        }
      } else {
        classes['u-gray-background'] = true;
      }
      return Object.keys(classes).sort().join(' ');
    }

    _computeLabelValue(change, labelName) {
      const label = change.labels[labelName];
      if (!label) { return ''; }
      if (label.approved) {
        return '✓';
      }
      if (label.rejected) {
        return '✕';
      }
      if (label.value > 0) {
        return '+' + label.value;
      }
      if (label.value < 0) {
        return label.value;
      }
      return '';
    }

    _computeRepoUrl(change) {
      return Gerrit.Nav.getUrlForProjectChanges(change.project, true,
          change.internalHost);
    }

    _computeRepoBranchURL(change) {
      return Gerrit.Nav.getUrlForBranch(change.branch, change.project, null,
          change.internalHost);
    }

    _computeTopicURL(change) {
      if (!change.topic) { return ''; }
      return Gerrit.Nav.getUrlForTopic(change.topic, change.internalHost);
    }

    /**
     * Computes the display string for the project column. If there is a host
     * specified in the change detail, the string will be prefixed with it.
     *
     * @param {!Object} change
     * @param {string=} truncate whether or not the project name should be
     *     truncated. If this value is truthy, the name will be truncated.
     * @return {string}
     */
    _computeRepoDisplay(change, truncate) {
      if (!change || !change.project) { return ''; }
      let str = '';
      if (change.internalHost) { str += change.internalHost + '/'; }
      str += truncate ? this.truncatePath(change.project, 2) : change.project;
      return str;
    }

    _computeSizeTooltip(change) {
      if (change.insertions + change.deletions === 0 ||
          isNaN(change.insertions + change.deletions)) {
        return 'Size unknown';
      } else {
        return `+${change.insertions}, -${change.deletions}`;
      }
    }

    /**
     * TShirt sizing is based on the following paper:
     * http://dirkriehle.com/wp-content/uploads/2008/09/hicss-42-csdistr-final-web.pdf
     */
    _computeChangeSize(change) {
      const delta = change.insertions + change.deletions;
      if (isNaN(delta) || delta === 0) {
        return null; // Unknown
      }
      if (delta < CHANGE_SIZE.XS) {
        return 'XS';
      } else if (delta < CHANGE_SIZE.SMALL) {
        return 'S';
      } else if (delta < CHANGE_SIZE.MEDIUM) {
        return 'M';
      } else if (delta < CHANGE_SIZE.LARGE) {
        return 'L';
      } else {
        return 'XL';
      }
    }

    toggleReviewed() {
      const newVal = !this.change.reviewed;
      this.set('change.reviewed', newVal);
      this.dispatchEvent(new CustomEvent('toggle-reviewed', {
        bubbles: true,
        composed: true,
        detail: {change: this.change, reviewed: newVal},
      }));
    }
  }

  customElements.define(GrChangeListItem.is, GrChangeListItem);
})();
