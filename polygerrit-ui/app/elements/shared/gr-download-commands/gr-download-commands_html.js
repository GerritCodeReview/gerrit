import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      paper-tabs {
        height: 3rem;
        margin-bottom: var(--spacing-m);
        --paper-tabs-selection-bar-color: var(--link-color);
      }
      paper-tab {
        max-width: 15rem;
        text-transform: uppercase;
        --paper-tab-ink: var(--link-color);
      }
      label,
      input {
        display: block;
      }
      label {
        font-weight: var(--font-weight-bold);
      }
      .schemes {
        display: flex;
        justify-content: space-between;
      }
      .commands {
        display: flex;
        flex-direction: column;
      }
      gr-shell-command {
        width: 60em;
        margin-bottom: var(--spacing-m);
      }
      .hidden {
        display: none;
      }
    </style>
    <div class="schemes">
      <paper-tabs id="downloadTabs" class\$="[[_computeShowTabs(schemes)]]" selected="[[_computeSelected(schemes, selectedScheme)]]" on-selected-changed="_handleTabChange">
        <template is="dom-repeat" items="[[schemes]]" as="scheme">
          <paper-tab data-scheme\$="[[scheme]]">[[scheme]]</paper-tab>
        </template>
      </paper-tabs>
    </div>
    <div class="commands" hidden\$="[[!schemes.length]]" hidden="">
      <template is="dom-repeat" items="[[commands]]" as="command">
        <gr-shell-command label="[[command.title]]" command="[[command.command]]"></gr-shell-command>
      </template>
    </div>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`;
