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
import {html} from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <gr-diff id="diff" change-num="[[changeNum]]" no-auto-render="[[noAutoRender]]" patch-range="[[patchRange]]" path="[[path]]" prefs="[[prefs]]" project-name="[[projectName]]" display-line="[[displayLine]]" is-image-diff="[[isImageDiff]]" commit-range="[[commitRange]]" hidden\$="[[hidden]]" no-render-on-prefs-change="[[noRenderOnPrefsChange]]" line-wrapping="[[lineWrapping]]" view-mode="[[viewMode]]" line-of-interest="[[lineOfInterest]]" logged-in="[[_loggedIn]]" loading="[[_loading]]" error-message="[[_errorMessage]]" base-image="[[_baseImage]]" revision-image="[[_revisionImage]]" coverage-ranges="[[_coverageRanges]]" blame="[[_blame]]" layers="[[_layers]]" diff="[[diff]]" show-newline-warning-left="[[_showNewlineWarningLeft(diff)]]" show-newline-warning-right="[[_showNewlineWarningRight(diff)]]">
    </gr-diff>
    <gr-syntax-layer id="syntaxLayer" enabled="[[_syntaxHighlightingEnabled]]" diff="[[diff]]"></gr-syntax-layer>
    <gr-js-api-interface id="jsAPI"></gr-js-api-interface>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`;
