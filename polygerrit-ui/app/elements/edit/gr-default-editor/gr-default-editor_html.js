import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      textarea {
        border: none;
        box-sizing: border-box;
        font-family: var(--monospace-font-family);
        font-size: var(--font-size-code);
        line-height: var(--line-height-code);
        min-height: 60vh;
        resize: none;
        white-space: pre;
        width: 100%;
      }
      textarea:focus {
        outline: none;
      }
    </style>
    <textarea id="textarea" value="[[fileContent]]" on-input="_handleTextareaInput"></textarea>
`;
