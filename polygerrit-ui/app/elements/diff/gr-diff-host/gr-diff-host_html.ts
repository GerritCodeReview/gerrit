/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {html} from '@polymer/polymer/lib/utils/html-tag';

export const htmlTemplate = html`
  <gr-diff
    id="diff"
    no-auto-render="[[noAutoRender]]"
    path="[[path]]"
    prefs="[[diffPrefs]]"
    display-line="[[displayLine]]"
    is-image-diff="[[isImageDiff]]"
    hidden$="[[hidden]]"
    no-render-on-prefs-change="[[noRenderOnPrefsChange]]"
    render-prefs="[[_renderPrefs]]"
    line-wrapping="[[lineWrapping]]"
    view-mode="[[viewMode]]"
    line-of-interest="[[lineOfInterest]]"
    logged-in="[[_loggedIn]]"
    error-message="[[_errorMessage]]"
    base-image="[[_baseImage]]"
    revision-image="[[_revisionImage]]"
    coverage-ranges="[[_coverageRanges]]"
    blame="[[_blame]]"
    layers="[[_layers]]"
    diff="[[diff]]"
    show-newline-warning-left="[[_showNewlineWarningLeft(diff)]]"
    show-newline-warning-right="[[_showNewlineWarningRight(diff)]]"
    use-new-image-diff-ui="[[_useNewImageDiffUi()]]"
  >
  </gr-diff>
`;
