/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css} from 'lit';

export const changeMetadataStyles = css`
  section {
    display: table-row;
  }

  section:not(:first-of-type) .title,
  section:not(:first-of-type) .value {
    padding-top: var(--spacing-s);
  }

  .title,
  .value {
    display: table-cell;
    vertical-align: top;
  }

  .title {
    color: var(--deemphasized-text-color);
    max-width: 20em;
    padding-left: var(--metadata-horizontal-padding);
    padding-right: var(--metadata-horizontal-padding);
    overflow-wrap: anywhere;
  }
`;
