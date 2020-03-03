import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      :host {
        display: block;
      }
      #graphic,
      #help {
        display: inline-block;
        margin: var(--spacing-m);
      }
      #graphic #circle {
        align-items: center;
        background-color: var(--chip-background-color);
        border-radius: 50%;
        display: flex;
        height: 10em;
        justify-content: center;
        width: 10em;
      }
      #graphic iron-icon {
        color: #9e9e9e;
        height: 5em;
        width: 5em;
      }
      #graphic p {
        color: var(--deemphasized-text-color);
        text-align: center;
      }
      #help {
        padding-top: var(--spacing-xl);
        vertical-align: top;
      }
      #help h1 {
        font-family: var(--header-font-family);
        font-size: var(--font-size-h3);
        font-weight: var(--font-weight-h3);
        line-height: var(--line-height-h3);
      }
      #help p {
        margin-bottom: var(--spacing-m);
        max-width: 35em;
      }
      @media only screen and (max-width: 50em) {
        #graphic {
          display: none;
        }
      }
    </style>
    <div id="graphic">
      <div id="circle">
        <iron-icon id="icon" icon="gr-icons:zeroState"></iron-icon>
      </div>
      <p>
        No outgoing changes yet
      </p>
    </div>
    <div id="help">
      <h1>Push your first change for code review</h1>
      <p>
        Pushing a change for review is easy, but a little different from
        other git code review tools. Click on the \`Create Change' button
        and follow the step by step instructions.
      </p>
      <gr-button on-click="_handleCreateTap">Create Change</gr-button>
    </div>
`;
