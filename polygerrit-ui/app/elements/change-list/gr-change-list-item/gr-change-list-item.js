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
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-change-list-item_html.js';
import {ChangeTableMixin} from '../../../mixins/gr-change-table-mixin/gr-change-table-mixin.js';
import {GerritNav} from '../../core/gr-navigation/gr-navigation.js';
import {getDisplayName} from '../../../utils/display-name-util.js';
import {getPluginEndpoints} from '../../shared/gr-js-api-interface/gr-plugin-endpoints.js';
import {pluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader.js';
import {appContext} from '../../../services/app-context.js';
import {truncatePath} from '../../../utils/path-list-util.js';
import {changeStatuses} from '../../../utils/change-util.js';

const CHANGE_SIZE = {
  XS: 10,
  SMALL: 50,
  MEDIUM: 250,
  LARGE: 1000,
};

// How many reviewers should be shown with an account-label?
const PRIMARY_REVIEWERS_COUNT = 2;

/**
 * @extends PolymerElement
 */
class GrChangeListItem extends ChangeTableMixin(GestureEventListeners(
    LegacyElementMixin(PolymerElement))) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-change-list-item'; }

  static get properties() {
    return {
      /** The logged-in user's account, or null if no user is logged in. */
      account: {
        type: Object,
        value: null,
      },
      visibleChangeTableColumns: Array,
      labelNames: {
        type: Array,
      },

      /** @type {?} */
      change: Object,
      config: Object,
      /** Name of the section in the change-list. Used for reporting. */
      sectionName: String,
      changeURL: {
        type: String,
        computed: '_computeChangeURL(change)',
      },
      statuses: {
        type: Array,
        computed: '_changeStatuses(change)',
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

  constructor() {
    super();
    this.reporting = appContext.reportingService;
  }

  /** @override */
  attached() {
    super.attached();
    pluginLoader.awaitPluginsLoaded().then(() => {
      this._dynamicCellEndpoints = getPluginEndpoints().getDynamicEndpoints(
          'change-list-item-cell');
    });
  }

  _changeStatuses(change) {
    return changeStatuses(change);
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
    return Object.keys(classes).sort()
        .join(' ');
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
    str += truncate ? truncatePath(change.project, 2) : change.project;
    return str;
  }

  _computeSizeTooltip(change) {
    if (change.insertions + change.deletions === 0 ||
        isNaN(change.insertions + change.deletions)) {
      return 'Size unknown';
    } else {
      return `added ${change.insertions}, removed ${change.deletions} lines`;
    }
  }

  _hasAttention(account) {
    if (!this.change || !this.change.attention_set) return false;
    return this.change.attention_set.hasOwnProperty(account._account_id);
  }

  /**
   * Computes the array of all reviewers with sorting the reviewers in the
   * attention set before others, and the current user first.
   */
  _computeReviewers(change) {
    if (!change || !change.reviewers || !change.reviewers.REVIEWER) return [];
    const reviewers = [...change.reviewers.REVIEWER].filter(r =>
      !change.owner || change.owner._account_id !== r._account_id
    );
    reviewers.sort((r1, r2) => {
      if (this.account) {
        if (r1._account_id === this.account._account_id) return -1;
        if (r2._account_id === this.account._account_id) return 1;
      }
      if (this._hasAttention(r1) && !this._hasAttention(r2)) return -1;
      if (this._hasAttention(r2) && !this._hasAttention(r1)) return 1;
      return (r1.name || '').localeCompare(r2.name || '');
    });
    return reviewers;
  }

  _computePrimaryReviewers(change) {
    return this._computeReviewers(change).slice(0, PRIMARY_REVIEWERS_COUNT);
  }

  _computeAdditionalReviewers(change) {
    return this._computeReviewers(change).slice(PRIMARY_REVIEWERS_COUNT);
  }

  _computeAdditionalReviewersCount(change) {
    return this._computeAdditionalReviewers(change).length;
  }

  _computeAdditionalReviewersTitle(change, config) {
    if (!change || !config) return '';
    return this._computeAdditionalReviewers(change)
        .map(user => getDisplayName(config, user))
        .join(', ');
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

  _handleChangeClick(e) {
    // Don't prevent the default and neither stop bubbling. We just want to
    // report the click, but then let the browser handle the click on the link.

    const selfId = (this.account && this.account._account_id) || -1;
    const ownerId = (this.change && this.change.owner
        && this.change.owner._account_id) || -1;

    this.reporting.reportInteraction('change-row-clicked', {
      section: this.sectionName,
      isOwner: selfId === ownerId,
    });
  }
}

customElements.define(GrChangeListItem.is, GrChangeListItem);
