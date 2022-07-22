/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css} from 'lit';

export const submitRequirementsStyles = css`
  gr-icon.check_circle,
  gr-icon.published_with_changes {
    --icon-color: var(--success-foreground);
  }
  gr-icon.block,
  gr-icon.error {
    --icon-color: var(--deemphasized-text-color);
  }
  gr-icon.cancel {
    --icon-color: var(--error-foreground);
  }
`;
