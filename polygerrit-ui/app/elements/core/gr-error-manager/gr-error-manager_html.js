import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <gr-overlay with-backdrop="" id="errorOverlay">
      <gr-error-dialog id="errorDialog" on-dismiss="_handleDismissErrorDialog" confirm-label="Dismiss" confirm-on-enter="" login-url="[[loginUrl]]"></gr-error-dialog>
    </gr-overlay>
    <gr-overlay id="noInteractionOverlay" with-backdrop="" always-on-top="" no-cancel-on-esc-key="" no-cancel-on-outside-click="">
    </gr-overlay>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
    <gr-reporting id="reporting"></gr-reporting>
`;
