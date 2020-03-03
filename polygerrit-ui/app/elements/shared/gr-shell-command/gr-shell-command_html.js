import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      .commandContainer {
        margin-bottom: var(--spacing-m);
      }
      .commandContainer {
        background-color: var(--shell-command-background-color);
        /* Should be spacing-m larger than the :before width. */
        padding: var(--spacing-m) var(--spacing-m) var(--spacing-m) calc(3*var(--spacing-m) + 0.5em);
        position: relative;
        width: 100%;
      }
      .commandContainer:before {
        content: '\$';
        position: absolute;
        display: block;
        box-sizing: border-box;
        background: var(--shell-command-decoration-background-color);
        top: 0;
        bottom: 0;
        left: 0;
        /* Should be spacing-m smaller than the .commandContainer padding-left. */
        width: calc(2*var(--spacing-m) + 0.5em);
        /* Should vertically match the padding of .commandContainer. */
        padding: var(--spacing-m);
        /* Should roughly match the height of .commandContainer without padding. */
        line-height: 26px;
      }
      .commandContainer gr-copy-clipboard {
        --text-container-style: {
          border: none;
        }
      }
    </style>
    <label>[[label]]</label>
    <div class="commandContainer">
      <gr-copy-clipboard text="[[command]]"></gr-copy-clipboard>
    </div>
`;
