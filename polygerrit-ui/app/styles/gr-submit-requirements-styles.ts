/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css} from 'lit';

export const submitRequirementsStyles = css`
  iron-icon.check_circle,
  iron-icon.settings_backup_restore {
    color: var(--success-foreground);
  }
  iron-icon.block,
  iron-icon.error {
    color: var(--deemphasized-text-color);
  }
  iron-icon.cancel {
    color: var(--error-foreground);
  }
`;
