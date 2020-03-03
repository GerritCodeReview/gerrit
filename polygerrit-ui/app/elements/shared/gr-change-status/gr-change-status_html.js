import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      .chip {
        border-radius: var(--border-radius);
        background-color: var(--chip-background-color);
        padding: 0 var(--spacing-m);
        white-space: nowrap;
      }
      :host(.merged) .chip {
        background-color: #5b9d52;
        color: #5b9d52;
      }
      :host(.abandoned) .chip {
        background-color: #afafaf;
        color: #afafaf;
      }
      :host(.wip) .chip {
        background-color: #8f756c;
        color: #8f756c;
      }
      :host(.private) .chip {
        background-color: #c17ccf;
        color: #c17ccf;
      }
      :host(.merge-conflict) .chip {
        background-color: #dc5c60;
        color: #dc5c60;
      }
      :host(.active) .chip {
        background-color: #29b6f6;
        color: #29b6f6;
      }
      :host(.ready-to-submit) .chip {
        background-color: #e10ca3;
        color: #e10ca3;
      }
      :host(.custom) .chip {
        background-color: #825cc2;
        color: #825cc2;
      }
      :host([flat]) .chip {
        background-color: transparent;
        padding: 0;
      }
      :host(:not([flat])) .chip {
        color: white;
      }
    </style>
    <gr-tooltip-content has-tooltip="" position-below="" title="[[tooltipText]]" max-width="40em">
      <div class="chip" aria-label\$="Label: [[status]]">
        [[_computeStatusString(status)]]
      </div>
    </gr-tooltip-content>
`;
