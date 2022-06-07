/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css} from 'lit';

export const submitRequirementsStyles = css`
  iron-icon.check-circle-filled,
  iron-icon.overridden {
    color: var(--success-foreground);
  }
  iron-icon.block,
  iron-icon.error {
    color: var(--deemphasized-text-color);
  }
`;
