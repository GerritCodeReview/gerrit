import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      :host {
        display: flex;
        position: relative;
      }
      :host(.monospace) {
        font-family: var(--monospace-font-family);
        font-size: var(--font-size-mono);
        line-height: var(--line-height-mono);
        font-weight: var(--font-weight-normal);
      }
      :host(.code) {
        font-family: var(--monospace-font-family);
        font-size: var(--font-size-code);
        line-height: var(--line-height-code);
        font-weight: var(--font-weight-normal);
      }
      #emojiSuggestions {
        font-family: var(--font-family);
      }
      gr-autocomplete {
        display: inline-block
      }
      #textarea {
        background-color: var(--view-background-color);
        width: 100%;
      }
      #hiddenText #emojiSuggestions {
        visibility: visible;
        white-space: normal;
      }
      iron-autogrow-textarea {
        position: relative;

        /** This is needed for firefox */
        --iron-autogrow-textarea_-_white-space: pre-wrap;
      }
      #textarea.noBorder {
        border: none;
      }
      #hiddenText {
        display: block;
        float: left;
        position: absolute;
        visibility: hidden;
        width: 100%;
        white-space: pre-wrap;
      }
    </style>
    <div id="hiddenText"></div>
    <!-- When the autocomplete is open, the span is moved at the end of
      hiddenText in order to correctly position the dropdown. After being moved,
      it is set as the positionTarget for the emojiSuggestions dropdown. -->
    <span id="caratSpan"></span>
    <gr-autocomplete-dropdown vertical-align="top" horizontal-align="left" dynamic-align="" id="emojiSuggestions" suggestions="[[_suggestions]]" index="[[_index]]" vertical-offset="[[_verticalOffset]]" on-dropdown-closed="_resetEmojiDropdown" on-item-selected="_handleEmojiSelect">
    </gr-autocomplete-dropdown>
    <iron-autogrow-textarea id="textarea" autocomplete="[[autocomplete]]" placeholder="[[placeholder]]" disabled="[[disabled]]" rows="[[rows]]" max-rows="[[maxRows]]" value="{{text}}" on-bind-value-changed="_onValueChanged"></iron-autogrow-textarea>
    <gr-reporting id="reporting"></gr-reporting>
`;
