import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      .scoresTable {
        display: table;
        width: 100%;
      }
      .mergedMessage {
        font-style: italic;
        text-align: center;
        width: 100%;
      }
      gr-label-score-row:hover {
        background-color: var(--hover-background-color);
      }
      gr-label-score-row {
        display: table-row;
      }
      gr-label-score-row.no-access {
        display: var(--label-no-access-display, table-row);
      }
    </style>
    <div class="scoresTable">
      <template is="dom-repeat" items="[[_labels]]" as="label">
        <gr-label-score-row class\$="[[_computeLabelAccessClass(label.name, permittedLabels)]]" label="[[label]]" name="[[label.name]]" labels="[[change.labels]]" permitted-labels="[[permittedLabels]]" label-values="[[_labelValues]]"></gr-label-score-row>
      </template>
    </div>
    <div class="mergedMessage" hidden\$="[[!_changeIsMerged(change.status)]]">
      Because this change has been merged, votes may not be decreased.
    </div>
`;
