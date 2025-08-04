/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import './shared/gr-a11y-announcer/gr-a11y-announcer';
import {GrA11yAnnouncer} from './shared/gr-a11y-announcer/gr-a11y-announcer';

export interface FixGrA11yAnnouncer extends GrA11yAnnouncer {
  requestAvailability(): void;
}

export function grAnnouncerRequestAvailability() {
  (GrA11yAnnouncer as unknown as FixGrA11yAnnouncer).requestAvailability();
}
