import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <div class="contentWrapper">
      <slot></slot>
    </div>
    <gr-ranged-comment-layer id="rangeLayer" comment-ranges="[[commentRanges]]"></gr-ranged-comment-layer>
    <gr-coverage-layer id="coverageLayerLeft" coverage-ranges="[[_leftCoverageRanges]]" side="left"></gr-coverage-layer>
    <gr-coverage-layer id="coverageLayerRight" coverage-ranges="[[_rightCoverageRanges]]" side="right"></gr-coverage-layer>
    <gr-diff-processor id="processor" groups="{{_groups}}"></gr-diff-processor>
`;
