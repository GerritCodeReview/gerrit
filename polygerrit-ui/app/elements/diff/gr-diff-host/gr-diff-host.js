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

  /**
   * Wrapper around gr-diff.
   *
   * Webcomponent fetching diffs and related data from restAPI and passing them
   * to the presentational gr-diff for rendering.
   */
  // TODO(oler): Move all calls to restAPI from gr-diff here.
  Polymer({
    is: 'gr-diff-host',

    /**
     * Fired when the user selects a line.
     * @event line-selected
     */

    /**
     * Fired if being logged in is required.
     *
     * @event show-auth-required
     */

    /**
     * Fired when a comment is saved or discarded
     *
     * @event diff-comments-modified
     */

    properties: {
      changeNum: String,
      noAutoRender: {
        type: Boolean,
        value: false,
      },
      /** @type {?} */
      patchRange: Object,
      path: String,
      prefs: {
        type: Object,
      },
      projectConfig: {
        type: Object,
      },
      projectName: String,
      displayLine: {
        type: Boolean,
        value: false,
      },
      isImageDiff: {
        type: Boolean,
        notify: true,
      },
      commitRange: Object,
      filesWeblinks: {
        type: Object,
        notify: true,
      },
      hidden: {
        type: Boolean,
        reflectToAttribute: true,
      },
      noRenderOnPrefsChange: Boolean,
      comments: Object,
      lineWrapping: {
        type: Boolean,
        value: false,
      },
      viewMode: {
        type: String,
        value: DiffViewMode.SIDE_BY_SIDE,
      },

      /**
       * Special line number which should not be collapsed into a shared region.
       * @type {{
       *  number: number,
       *  leftSide: {boolean}
       * }|null}
       */
      lineOfInterest: Object,

      /**
       * If the diff fails to load, show the failure message in the diff rather
       * than bubbling the error up to the whole page. This is useful for when
       * loading inline diffs because one diff failing need not mark the whole
       * page with a failure.
       */
      showLoadFailure: Boolean,

      isBlameLoaded: {
        type: Boolean,
        notify: true,
      },
    },

    /** @return {!Promise} */
    reload() {
      return this.$.diff.reload();
    },

    /** Cancel any remaining diff builder rendering work. */
    cancel() {
      this.$.diff.cancel();
    },

    /** @return {!Array<!HTMLElement>} */
    getCursorStops() {
      return this.$.diff.getCursorStops();
    },

    /** @return {boolean} */
    isRangeSelected() {
      return this.$.diff.isRangeSelected();
    },

    toggleLeftDiff() {
      this.$.diff.toggleLeftDiff();
    },

    /**
     * Load and display blame information for the base of the diff.
     * @return {Promise} A promise that resolves when blame finishes rendering.
     */
    loadBlame() {
      return this.$.diff.loadBlame();
    },

    /** Unload blame information for the diff. */
    clearBlame() {
      this.$.diff.clearBlame();
    },

    /** @return {!Array<!HTMLElement>} */
    getThreadEls() {
      return this.$.diff.getThreadEls();
    },

    /** @param {HTMLElement} el */
    addDraftAtLine(el) {
      this.$.diff.addDraftAtLine(el);
    },

    clearDiffContent() {
      this.$.diff.clearDiffContent();
    },

    expandAllContext() {
      this.$.diff.expandAllContext();
    },
  });
})();
