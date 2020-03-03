import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      #nav {
        background-color: var(--table-header-background-color);
        border: 1px solid var(--border-color);
        border-top: none;
        height: 100%;
        position: absolute;
        top: 0;
        width: 14em;
      }
      #nav.pinned {
        position: fixed;
      }
      @media only screen and (max-width: 53em) {
        #nav {
          display: none;
        }
      }
    </style>
    <nav id="nav">
      <slot></slot>
    </nav>
`;
