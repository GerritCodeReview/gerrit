/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css} from 'lit';

export const submitRequirementsStyles = css`
  gr-icon.check_circle,
  gr-icon.published_with_changes {
    color: var(--success-foreground);
  }
  gr-icon.block,
  gr-icon.error {
    color: var(--deemphasized-text-color);
  }
  gr-icon.cancel {
    color: var(--error-foreground);
  }
`;
