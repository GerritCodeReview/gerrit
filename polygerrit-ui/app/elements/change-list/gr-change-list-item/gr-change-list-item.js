/**
 * @license
 * Copyright (C) 2015 The Android Open Source Project
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

import '../../../scripts/bundled-polymer.js';
import '../../../styles/gr-change-list-styles.js';
import '../../shared/gr-account-link/gr-account-link.js';
import '../../shared/gr-change-star/gr-change-star.js';
import '../../shared/gr-change-status/gr-change-status.js';
import '../../shared/gr-date-formatter/gr-date-formatter.js';
import '../../shared/gr-icons/gr-icons.js';
import '../../shared/gr-limited-text/gr-limited-text.js';
import '../../shared/gr-tooltip-content/gr-tooltip-content.js';
import '../../../styles/shared-styles.js';
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator.js';
import '../../plugins/gr-endpoint-param/gr-endpoint-param.js';
import {mixinBehaviors} from '@polymer/polymer/lib/legacy/class.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-change-list-item_html.js';
import {BaseUrlBehavior} from '../../../behaviors/base-url-behavior/base-url-behavior.js';
import {ChangeTableBehavior} from '../../../behaviors/gr-change-table-behavior/gr-change-table-behavior.js';
import {PathListBehavior} from '../../../behaviors/gr-path-list-behavior/gr-path-list-behavior.js';
import {URLEncodingBehavior} from '../../../behaviors/gr-url-encoding-behavior/gr-url-encoding-behavior.js';
import {RESTClientBehavior} from '../../../behaviors/rest-client-behavior/rest-client-behavior.js';
import {GerritNav} from '../../core/gr-navigation/gr-navigation.js';
import {pluginEndpoints} from '../../shared/gr-js-api-interface/gr-plugin-endpoints.js';
import {pluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader.js';

const CHANGE_SIZE = {
  XS: 10,
  SMALL: 50,
  MEDIUM: 250,
  LARGE: 1000,
};

/**
 * @appliesMixin RESTClientMixin
 * @extends Polymer.Element
 */
class GrChangeListItem extends mixinBehaviors( [
  BaseUrlBehavior,
  ChangeTableBehavior,
  PathListBehavior,
  RESTClientBehavior,
  URLEncodingBehavior,
], GestureEventListeners(
    LegacyElementMixin(
        PolymerElement))) {
  static get template() { return htmlTemplate; }

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
      _dashboardLabels: {
        type: Array,
      },
    };
  }

  _computeDashboardLabels(change, labelNames) {
    const labels = change.labels;
    this._dashboardLabels = [];
    // console.log(labels);
    for (const label in labels) {
      if (!labels.hasOwnProperty(label)) { continue; }
      const labelInfo = change.labels[label];
      const icon = this._computeLabelIcon(labelInfo);
      const style = this._computeLabelClass(change, labelInfo);
      const title = this._computeLabelTitle(change, labelInfo);

      this.push('_dashboardLabels', {label, icon, style, title, labelInfo});
    }
    return this._dashboardLabels;
  }

  /** @override */
  attached() {
    super.attached();
    pluginLoader.awaitPluginsLoaded().then(() => {
      this._dynamicCellEndpoints = pluginEndpoints.getDynamicEndpoints(
          'change-list-item-cell');
    });
  }

  _computeChangeURL(change) {
    return GerritNav.getUrlForChange(change);
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
    const label = labelName;
    console.log('start------------------');
    console.log('change: ', change);
    console.log('labelName: ', labelName);
    // Mimic a Set.
    const classes = {
      cell: true,
      label: true,
    };
    if (label) {
      if (label.approved) {
        classes['approved'] = true;
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
        classes['rejected'] = true;
        classes['u-red'] = true;
      }
    } else {
      console.log('label: ', label);
      console.log('end-------------');
      classes['u-gray-background'] = true;
    }
    return Object.keys(classes).sort()
        .join(' ');
  }

  /**
   * @param {Object} labelInfo
   * @return {string} The icon name, or undefined if no icon should
   *     be used.
   */
  _computeLabelIcon(labelInfo) {
    if (labelInfo.approved) { return 'gr-icons:check'; }
    if (labelInfo.rejected) { return 'gr-icons:close'; }
    return '';
  }

  _computeLabelValue(change, labelName) {
    const label = change.labels[labelName];
    if (!label) { return ''; }
    if (label.approved) {
      return 'gr-icons:check';
    }
    if (label.rejected) {
      return 'gr-icons:close';
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
    return GerritNav.getUrlForProjectChanges(change.project, true,
        change.internalHost);
  }

  _computeRepoBranchURL(change) {
    return GerritNav.getUrlForBranch(change.branch, change.project, null,
        change.internalHost);
  }

  _computeTopicURL(change) {
    if (!change.topic) { return ''; }
    return GerritNav.getUrlForTopic(change.topic, change.internalHost);
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

  _computeComments(unresolved_comment_count) {
    if (!unresolved_comment_count || unresolved_comment_count < 1) return '';
    return `${unresolved_comment_count} unresolved`;
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
