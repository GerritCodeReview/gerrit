import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      :host {
        box-sizing: border-box;
        display: block;
        min-height: var(--header-height);
        position: relative;
      }
      header {
        background: inherit;
        border: inherit;
        display: inline;
        height: inherit;
      }
      .floating {
        left: 0;
        position: fixed;
        width: 100%;
        will-change: top;
      }
      .fixedAtTop {
        border-bottom: 1px solid #a4a4a4;
        box-shadow: var(--elevation-level-2);
      }
    </style>
    <header id="header" class\$="[[_computeHeaderClass(_headerFloating, _topLast)]]">
      <slot></slot>
    </header>
`;
