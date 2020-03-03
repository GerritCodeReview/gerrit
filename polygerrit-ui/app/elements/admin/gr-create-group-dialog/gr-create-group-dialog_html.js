import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
    </style>
    <style include="gr-form-styles">
      :host {
        display: inline-block;
      }
      input {
        width: 20em;
      }
    </style>
    <div class="gr-form-styles">
      <div id="form">
        <section>
          <span class="title">Group name</span>
          <iron-input bind-value="{{_name}}">
            <input is="iron-input" bind-value="{{_name}}">
          </iron-input>
        </section>
      </div>
    </div>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`;
