import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <gr-diff id="diff" change-num="[[changeNum]]" no-auto-render="[[noAutoRender]]" patch-range="[[patchRange]]" path="[[path]]" prefs="[[prefs]]" project-name="[[projectName]]" display-line="[[displayLine]]" is-image-diff="[[isImageDiff]]" commit-range="[[commitRange]]" hidden\$="[[hidden]]" no-render-on-prefs-change="[[noRenderOnPrefsChange]]" line-wrapping="[[lineWrapping]]" view-mode="[[viewMode]]" line-of-interest="[[lineOfInterest]]" logged-in="[[_loggedIn]]" loading="[[_loading]]" error-message="[[_errorMessage]]" base-image="[[_baseImage]]" revision-image="[[_revisionImage]]" coverage-ranges="[[_coverageRanges]]" blame="[[_blame]]" layers="[[_layers]]" diff="[[diff]]" show-newline-warning-left="[[_showNewlineWarningLeft(diff)]]" show-newline-warning-right="[[_showNewlineWarningRight(diff)]]">
    </gr-diff>
    <gr-syntax-layer id="syntaxLayer" enabled="[[_syntaxHighlightingEnabled]]" diff="[[diff]]"></gr-syntax-layer>
    <gr-js-api-interface id="jsAPI"></gr-js-api-interface>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
    <gr-reporting id="reporting" category="diff"></gr-reporting>
`;
