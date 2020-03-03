import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      :host {
        color: var(--primary-text-color);
        display: block;
        max-height: 90vh;
        overflow: auto;
      }
      .container {
        display: flex;
        flex-direction: column;
        max-height: 90vh;
        padding: var(--spacing-xl);
      }
      header {
        flex-shrink: 0;
        padding-bottom: var(--spacing-xl);
      }
      main {
        display: flex;
        flex-shrink: 1;
        width: 100%;
        flex: 1;
        /* IMPORTANT: required for firefox */
        min-height: 0px;
      }
      main .overflow-container {
        flex: 1;
        overflow: auto;
      }
      footer {
        display: flex;
        flex-shrink: 0;
        justify-content: flex-end;
        padding-top: var(--spacing-xl);
      }
      gr-button {
        margin-left: var(--spacing-l);
      }
      .hidden {
        display: none;
      }
    </style>
    <div class="container" on-keydown="_handleKeydown">
      <header class="font-h3"><slot name="header"></slot></header>
      <main>
        <div class="overflow-container">
          <slot name="main"></slot>
        </div>
      </main>
      <footer>
        <slot name="footer"></slot>
        <gr-button id="cancel" class\$="[[_computeCancelClass(cancelLabel)]]" link="" on-click="_handleCancelTap">
          [[cancelLabel]]
        </gr-button>
        <gr-button id="confirm" link="" primary="" on-click="_handleConfirm" disabled="[[disabled]]">
          [[confirmLabel]]
        </gr-button>
      </footer>
    </div>
`;
