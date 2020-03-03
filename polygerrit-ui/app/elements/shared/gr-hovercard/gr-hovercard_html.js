import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      :host {
        box-sizing: border-box;
        opacity: 0;
        position: absolute;
        transition: opacity 200ms;
        visibility: hidden;
        z-index: 100;
      }
      :host(.hovered) {
        visibility: visible;
        opacity: 1;
      }
      #hovercard {
        background: var(--dialog-background-color);
        box-shadow: var(--elevation-level-2);
        padding: var(--spacing-l);
      }
    </style>
    <div id="hovercard" role="tooltip" tabindex="-1">
      <slot></slot>
    </div>
`;
