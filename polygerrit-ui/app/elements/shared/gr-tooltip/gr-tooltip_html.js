import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      :host {
        --gr-tooltip-arrow-size: .5em;
        --gr-tooltip-arrow-center-offset: 0;

        background-color: var(--tooltip-background-color);
        box-shadow: var(--elevation-level-2);
        color: var(--tooltip-text-color);
        font-size: var(--font-size-small);
        position: absolute;
        z-index: 1000;
        max-width: var(--tooltip-max-width);
      }
      :host .tooltip {
        padding: var(--spacing-m) var(--spacing-l);
      }
      :host .arrowPositionBelow,
      :host([position-below]) .arrowPositionAbove  {
        display: none;
      }
      :host([position-below]) .arrowPositionBelow {
        display: initial;
      }
      .arrow {
        border-left: var(--gr-tooltip-arrow-size) solid transparent;
        border-right: var(--gr-tooltip-arrow-size) solid transparent;
        height: 0;
        position: absolute;
        left: calc(50% - var(--gr-tooltip-arrow-size));
        margin-left: var(--gr-tooltip-arrow-center-offset);
        width: 0;
      }
      .arrowPositionAbove {
        border-top: var(--gr-tooltip-arrow-size) solid var(--tooltip-background-color);
        bottom: calc(-1 * var(--gr-tooltip-arrow-size));
      }
      .arrowPositionBelow {
        border-bottom: var(--gr-tooltip-arrow-size) solid var(--tooltip-background-color);
        top: calc(-1 * var(--gr-tooltip-arrow-size));
      }
    </style>
    <div class="tooltip">
      <i class="arrowPositionBelow arrow"></i>
      [[text]]
      <i class="arrowPositionAbove arrow"></i>
    </div>
`;
