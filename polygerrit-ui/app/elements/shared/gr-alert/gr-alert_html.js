import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      /**
       * ALERT: DO NOT ADD TRANSITION PROPERTIES WITHOUT PROPERLY UNDERSTANDING
       * HOW THEY ARE USED IN THE CODE.
       */
      :host([toast]) {
        background-color: var(--tooltip-background-color);
        bottom: 1.25rem;
        border-radius: var(--border-radius);
        box-shadow: var(--elevation-level-2);
        color: var(--view-background-color);
        left: 1.25rem;
        position: fixed;
        transform: translateY(5rem);
        transition: transform var(--gr-alert-transition-duration, 80ms) ease-out;
        z-index: 1000;
      }
      :host([shown]) {
        transform: translateY(0);
      }
      /**
       * NOTE: To avoid style being overwritten by outside of the shadow DOM
       * (as outside styles always win), .content-wrapper is introduced as a
       * wrapper around main content to have better encapsulation, styles that
       * may be affected by outside should be defined on it.
       * In this case, \`padding:0px\` is defined in main.css for all elements
       * with the universal selector: *.
       */
      .content-wrapper {
        padding: var(--spacing-l) var(--spacing-xl);
      }
      .text {
        color: var(--tooltip-text-color);
        display: inline-block;
        max-height: 10rem;
        max-width: 80vw;
        vertical-align: bottom;
        word-break: break-all;
      }
      .action {
        color: var(--link-color);
        font-weight: var(--font-weight-bold);
        margin-left: var(--spacing-l);
        text-decoration: none;
        --gr-button: {
          padding: 0;
        }
      }
    </style>
    <div class="content-wrapper">
      <span class="text">[[text]]</span>
      <gr-button link="" class="action" hidden\$="[[_hideActionButton]]" on-click="_handleActionTap">[[actionText]]</gr-button>
    </div>
`;
