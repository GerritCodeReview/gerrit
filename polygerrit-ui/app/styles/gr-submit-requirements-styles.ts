/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css} from 'lit';

export const submitRequirementsStyles = css`
  .material-icon.check_circle,
  .material-icon.settings_backup_restore {
    color: var(--success-foreground);
  }
  .material-icon.block,
  .material-icon.error {
    color: var(--deemphasized-text-color);
  }
  .material-icon.cancel {
    color: var(--error-foreground);
  }
`;
