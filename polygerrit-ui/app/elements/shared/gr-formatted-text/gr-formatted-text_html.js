import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      :host {
        display: block;
        font-family: var(--font-family);
      }
      p,
      ul,
      blockquote,
      gr-linked-text.pre {
        margin: 0 0 var(--spacing-m) 0;
      }
      p,
      ul,
      blockquote {
        max-width: var(--gr-formatted-text-prose-max-width, none);
      }
      :host(.noTrailingMargin) p:last-child,
      :host(.noTrailingMargin) ul:last-child,
      :host(.noTrailingMargin) blockquote:last-child,
      :host(.noTrailingMargin) gr-linked-text.pre:last-child {
        margin: 0;
      }
      blockquote {
        border-left: 1px solid #aaa;
        padding: 0 var(--spacing-m);
      }
      li {
        list-style-type: disc;
        margin-left: var(--spacing-xl);
      }
      gr-linked-text.pre {
        font-family: var(--monospace-font-family);
        font-size: var(--font-size-code);
        line-height: var(--line-height-code);
      }

    </style>
    <div id="container"></div>
`;
