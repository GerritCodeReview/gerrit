/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {directive, AsyncDirective} from 'lit/async-directive.js';
import {DirectiveParameters, ChildPart} from 'lit/directive.js';
import {
  insertPart,
  setChildPartValue,
  removePart,
} from 'lit/directive-helpers.js';

interface RepeatOptions<T> {
  values: T[];
  mapFn?: (val: T, idx: number) => unknown;
  initialCount: number;
  targetFrameRate?: number;
  startAt?: number;
  endAt?: number;
}

interface RepeatState<T> {
  values: T[];
  mapFn?: (val: T, idx: number) => unknown;
  startAt: number;
  endAt: number;
  incrementAmount: number;
  lastRenderedAt: number;
  targetFrameRate: number;
}

// This directive supports incrementally rendering a list of elements.
// It only responds to updates to values (which forces a complete re-render) and
// an update to endAt (which expands the list).
// It currently does not support changes to mapFn, initialCount or startAt
// unless values are also changed.
class IncrementalRepeat<T> extends AsyncDirective {
  private children: {part: ChildPart; options: RepeatOptions<T>}[] = [];

  private part!: ChildPart;

  private state!: RepeatState<T>;

  // Will render from `options.startAt` to `options.endAt`, up to
  // `options.initialCount` elements.
  render(options: RepeatOptions<T>) {
    const start = options.startAt ?? 0;
    const offset = start + options.initialCount;
    const end =
      options.endAt === undefined ? offset : Math.min(options.endAt, offset);
    const values = options.values.slice(start, end);
    if (options.mapFn) {
      const mapFn = options.mapFn;
      return values.map((val, idx) => mapFn(val, idx + start));
    }
    return values;
  }

  override update(part: ChildPart, [options]: DirectiveParameters<this>) {
    if (options.values !== this.state?.values) {
      if (this.nextScheduledFrameWork !== undefined)
        cancelAnimationFrame(this.nextScheduledFrameWork);
      this.part = part;
      this.clearParts();
      this.state = {
        values: options.values,
        mapFn: options.mapFn,
        startAt: options.initialCount,
        endAt: options.endAt ?? options.values.length,
        incrementAmount: options.initialCount,
        lastRenderedAt: performance.now(),
        targetFrameRate: options.targetFrameRate ?? 30,
      };
      this.nextScheduledFrameWork = requestAnimationFrame(
        this.animationFrameHandler
      );
    } else {
      this.updateParts();
      // TODO: Deal with updates to startAt by removing children and then
      // trimming the child where the new startAt falls into.
      if ((options.endAt ?? options.values.length) >= this.state.endAt) {
        this.state.endAt = options.endAt ?? options.values.length;
        if (this.nextScheduledFrameWork) {
          cancelAnimationFrame(this.nextScheduledFrameWork);
        }
        this.nextScheduledFrameWork = requestAnimationFrame(
          this.animationFrameHandler
        );
      }
    }
    // Render the first initial count.
    return this.render(options);
  }

  private appendPart(options: RepeatOptions<T>) {
    const part = insertPart(this.part);
    this.children.push({part, options});
    setChildPartValue(part, this.render(options));
  }

  private clearParts() {
    for (const child of this.children) {
      removePart(child.part);
    }
    this.children = [];
  }

  private updateParts() {
    for (const child of this.children) {
      setChildPartValue(child.part, this.render(child.options));
    }
  }

  private nextScheduledFrameWork: number | undefined;

  private animationFrameHandler = () => {
    if (this.state.startAt >= this.state.endAt) {
      this.nextScheduledFrameWork = undefined;
      return;
    }
    const now = performance.now();
    const frameRate = 1000 / (now - this.state.lastRenderedAt);
    if (frameRate < this.state.targetFrameRate) {
      // https://en.wikipedia.org/wiki/Additive_increase/multiplicative_decrease
      this.state.incrementAmount = Math.max(
        1,
        Math.round(this.state.incrementAmount / 2)
      );
    } else {
      this.state.incrementAmount++;
    }
    this.state.lastRenderedAt = now;
    this.appendPart({
      mapFn: this.state.mapFn,
      values: this.state.values,
      initialCount: this.state.incrementAmount,
      startAt: this.state.startAt,
      endAt: this.state.endAt,
    });

    this.state.startAt += this.state.incrementAmount;
    if (this.state.startAt < this.state.endAt) {
      this.nextScheduledFrameWork = requestAnimationFrame(
        this.animationFrameHandler
      );
    } else {
      this.nextScheduledFrameWork = undefined;
    }
  };
}

export const incrementalRepeat = directive(IncrementalRepeat);
