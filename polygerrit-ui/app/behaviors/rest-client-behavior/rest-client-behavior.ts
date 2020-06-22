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
import {
  BaseUrlBehavior,
  BaseUrlBehaviorInterface,
} from '../base-url-behavior/base-url-behavior';
import {ChangeStatus} from '../../constants/constants';

// WARNING: The types below can be completely wrong!
// The types was added to avoid eslinter and typescript errors.
// Correct typing requires more analysis and (probably) code changes.
// This will be done later.
type ChangeNum = string; // This can be wrong! See WARNING above
type PatchNum = string; // This can be wrong! See WARNING above

// This can be wrong! See WARNING above
interface Change {
  status: string; // This can be wrong! See WARNING above
  mergeable: boolean; // This can be wrong! See WARNING above
  work_in_progress: boolean; // This can be wrong! See WARNING above
  is_private: boolean; // This can be wrong! See WARNING above
  submittable: boolean; // This can be wrong! See WARNING above
}

// This can be wrong! See WARNING above
interface ChangeStatusesOptions {
  mergeable: boolean; // This can be wrong! See WARNING above
  submitEnabled: boolean; // This can be wrong! See WARNING above
}

/** @polymerBehavior Gerrit.RESTClientBehavior */
export const RESTClientBehavior = [
  {
    ChangeDiffType: {
      ADDED: 'ADDED',
      COPIED: 'COPIED',
      DELETED: 'DELETED',
      MODIFIED: 'MODIFIED',
      RENAMED: 'RENAMED',
      REWRITE: 'REWRITE',
    },

    // Must be kept in sync with the ListChangesOption enum and protobuf.
    ListChangesOption: {
      LABELS: 0,
      DETAILED_LABELS: 8,

      // Return information on the current patch set of the change.
      CURRENT_REVISION: 1,
      ALL_REVISIONS: 2,

      // If revisions are included, parse the commit object.
      CURRENT_COMMIT: 3,
      ALL_COMMITS: 4,

      // If a patch set is included, include the files of the patch set.
      CURRENT_FILES: 5,
      ALL_FILES: 6,

      // If accounts are included, include detailed account info.
      DETAILED_ACCOUNTS: 7,

      // Include messages associated with the change.
      MESSAGES: 9,

      // Include allowed actions client could perform.
      CURRENT_ACTIONS: 10,

      // Set the reviewed boolean for the caller.
      REVIEWED: 11,

      // Include download commands for the caller.
      DOWNLOAD_COMMANDS: 13,

      // Include patch set weblinks.
      WEB_LINKS: 14,

      // Include consistency check results.
      CHECK: 15,

      // Include allowed change actions client could perform.
      CHANGE_ACTIONS: 16,

      // Include a copy of commit messages including review footers.
      COMMIT_FOOTERS: 17,

      // Include push certificate information along with any patch sets.
      PUSH_CERTIFICATES: 18,

      // Include change's reviewer updates.
      REVIEWER_UPDATES: 19,

      // Set the submittable boolean.
      SUBMITTABLE: 20,

      // If tracking ids are included, include detailed tracking ids info.
      TRACKING_IDS: 21,

      // Skip mergeability data.
      SKIP_MERGEABLE: 22,

      /**
       * Skip diffstat computation that compute the insertions field (number of lines inserted) and
       * deletions field (number of lines deleted)
       */
      SKIP_DIFFSTAT: 23,
    },

    listChangesOptionsToHex(...args: number[]) {
      let v = 0;
      for (let i = 0; i < args.length; i++) {
        v |= 1 << args[i];
      }
      return v.toString(16);
    },

    /**
     *  @return {string}
     */
    changeBaseURL(
      this: BaseUrlBehaviorInterface,
      project: string,
      changeNum: ChangeNum,
      patchNum: PatchNum
    ) {
      let v =
        this.getBaseUrl() +
        '/changes/' +
        encodeURIComponent(project) +
        '~' +
        changeNum;
      if (patchNum) {
        v += '/revisions/' + patchNum;
      }
      return v;
    },

    changePath(this: BaseUrlBehaviorInterface, changeNum: ChangeNum) {
      return this.getBaseUrl() + '/c/' + changeNum;
    },

    changeIsOpen(change?: Change) {
      return change && change.status === ChangeStatus.NEW;
    },

    /**
     * @param {!Object} change
     * @param {!Object=} opt_options
     *
     * @return {!Array}
     */
    changeStatuses(change: Change, opt_options?: ChangeStatusesOptions) {
      const states = [];
      if (change.status === ChangeStatus.MERGED) {
        states.push('Merged');
      } else if (change.status === ChangeStatus.ABANDONED) {
        states.push('Abandoned');
      } else if (
        change.mergeable === false ||
        (opt_options && opt_options.mergeable === false)
      ) {
        // 'mergeable' prop may not always exist (@see Issue 6819)
        states.push('Merge Conflict');
      }
      if (change.work_in_progress) {
        states.push('WIP');
      }
      if (change.is_private) {
        states.push('Private');
      }

      // If there are any pre-defined statuses, only return those. Otherwise,
      // will determine the derived status.
      if (states.length || !opt_options) {
        return states;
      }

      // If no missing requirements, either active or ready to submit.
      if (change.submittable && opt_options.submitEnabled) {
        states.push('Ready to submit');
      } else {
        // Otherwise it is active.
        states.push('Active');
      }
      return states;
    },

    /**
     * @param {!Object} change
     * @return {string}
     */
    changeStatusString(change: Change) {
      return this.changeStatuses(change).join(', ');
    },
  },
  BaseUrlBehavior,
];

// eslint-disable-next-line @typescript-eslint/ban-ts-ignore
// @ts-ignore
function defineEmptyMixin() {
  // This is a temporary function.
  // Polymer linter doesn't process correctly the following code:
  // class MyElement extends Polymer.mixinBehaviors([legacyBehaviors], ...) {...}
  // To workaround this issue, the mock mixin is declared in this method.
  // In the following changes, legacy behaviors will be converted to mixins.

  /**
   * @polymer
   * @mixinFunction
   */
  const RESTClientMixin = (
    // eslint-disable-next-line @typescript-eslint/ban-ts-ignore
    // @ts-ignore
    base
  ) =>
    class extends base {
      // eslint-disable-next-line @typescript-eslint/ban-ts-ignore
      // @ts-ignore
      changeStatusString(change) {}

      // eslint-disable-next-line @typescript-eslint/ban-ts-ignore
      // @ts-ignore
      changeStatuses(change, opt_options) {}
    };
  // We can't apply @ts-ignore directly to RESTClientMixin - it breaks polylint
  // tests (polylinter expects that @polymer and @mixinFunction appear right
  // before the mixin definition). To workaround it and suppress error about
  // unused variable use a temporary variable.
  // eslint-disable-next-line @typescript-eslint/ban-ts-ignore
  // @ts-ignore
  const tmp = RESTClientMixin;
}

// TODO(dmfilippov) Remove the following lines with assignments
// Plugins can use the behavior because it was accessible with
// the global Gerrit... variable. To avoid breaking changes in plugins
// temporary assign global variables.
window.Gerrit = window.Gerrit || {};
window.Gerrit.RESTClientBehavior = RESTClientBehavior;
