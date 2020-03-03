import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      :host {
        display: block;
        max-height: 100vh;
        overflow-y: auto;
      }
      header{
        padding: var(--spacing-l);
      }
      main {
        display: flex;
        padding: 0 var(--spacing-xxl) var(--spacing-xxl);
      }
      header {
        align-items: center;
        border-bottom: 1px solid var(--border-color);
        display: flex;
        justify-content: space-between;
      }
      table:last-of-type {
        margin-left: var(--spacing-xxl);
      }
      td {
        padding: var(--spacing-xs) 0;
      }
      td:first-child {
        padding-right: var(--spacing-m);
        text-align: right;
      }
      .header {
        font-weight: var(--font-weight-bold);
        padding-top: var(--spacing-l);
      }
      .modifier {
        font-weight: var(--font-weight-normal);
      }
    </style>
    <header>
      <h3>Keyboard shortcuts</h3>
      <gr-button link="" on-click="_handleCloseTap">Close</gr-button>
    </header>
    <main>
      <table>
        <tbody>
          <template is="dom-repeat" items="[[_left]]">
            <tr>
              <td></td><td class="header">[[item.section]]</td>
            </tr>
            <template is="dom-repeat" items="[[item.shortcuts]]" as="shortcut">
              <tr>
                <td>
                  <gr-key-binding-display binding="[[shortcut.binding]]">
                  </gr-key-binding-display>
                </td>
                <td>[[shortcut.text]]</td>
              </tr>
            </template>
          </template>
        </tbody>
      </table>
      <template is="dom-if" if="[[_right]]">
        <table>
          <tbody>
            <template is="dom-repeat" items="[[_right]]">
              <tr>
                <td></td><td class="header">[[item.section]]</td>
              </tr>
              <template is="dom-repeat" items="[[item.shortcuts]]" as="shortcut">
                <tr>
                  <td>
                    <gr-key-binding-display binding="[[shortcut.binding]]">
                    </gr-key-binding-display>
                  </td>
                  <td>[[shortcut.text]]</td>
                </tr>
              </template>
            </template>
          </tbody>
        </table>
      </template>
    </main>
    <footer></footer>
`;
