import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <gr-cursor-manager id="cursorManager" scroll-behavior="[[_scrollBehavior]]" cursor-target-class="target-row" focus-on-move="[[_focusOnMove]]" target="{{diffRow}}" scroll-top-margin="[[scrollTopMargin]]"></gr-cursor-manager>
`;
