import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      :host {
        color: inherit;
        display: inline;
      }
    </style>
    <span>
      [[_computeDateStr(dateStr, _timeFormat, _dateFormat, _relative, showDateAndTime)]]
    </span>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`;
