/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css} from 'lit';

export const votingStyles = css`
  .voteChip {
    border: 1px solid var(--border-color);
    /* max rounded */
    border-radius: 1em;
    box-shadow: none;
    box-sizing: border-box;
    min-width: 3em;
    color: var(--vote-text-color);
  }
`;
