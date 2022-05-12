/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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
