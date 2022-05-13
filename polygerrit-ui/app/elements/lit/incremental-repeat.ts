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
  // TODO: targetFramerate
}

interface RepeatState<T> {
  values: T[];
  mapFn?: (val: T, idx: number) => unknown;
  startAt: number;
  incrementAmount: number;
  lastRenderedAt: number;
  targetFrameRate: number;
}

class IncrementalRepeat<T> extends AsyncDirective {
  private parts: ChildPart[] = [];
  private part!: ChildPart;
  private state!: RepeatState<T>;
  render(options: RepeatOptions<T>) {
    const values = options.values.slice(
      options.startAt ?? 0,
      (options.startAt ?? 0) + options.initialCount
    );
    if (options.mapFn) {
      return values.map(options.mapFn);
    }
    return values;
  }

  override update(part: ChildPart, [options]: DirectiveParameters<this>) {
    if (
      options.values !== this.state?.values ||
      options.mapFn !== this.state?.mapFn
    ) {
      if (this.nextScheduledFrameWork !== undefined)
        cancelAnimationFrame(this.nextScheduledFrameWork);
      this.nextScheduledFrameWork = requestAnimationFrame(
        this.animationFrameHandler
      );
      this.part = part;
      for (let i = 0; i < this.parts.length; i++) {
        removePart(this.parts[i]);
      }
      this.parts = [];
      this.state = {
        values: options.values,
        mapFn: options.mapFn,
        startAt: options.initialCount,
        incrementAmount: options.initialCount,
        lastRenderedAt: performance.now(),
        targetFrameRate: options.targetFrameRate ?? 30,
      };
    }
    return this.render(options);
  }

  private nextScheduledFrameWork: number | undefined;
  private animationFrameHandler = () => {
    const now = performance.now();
    const frameRate = 1000 / (now - this.state.lastRenderedAt);
    if (frameRate < this.state.targetFrameRate) { 
      // https://en.wikipedia.org/wiki/Additive_increase/multiplicative_decrease
      this.state.incrementAmount = Math.max(1, this.state.incrementAmount / 2);
    } else {
      this.state.incrementAmount++;
    }
    this.state.lastRenderedAt = now;
    const part = insertPart(this.part);
    this.parts.push(part);
    setChildPartValue(
      part,
      this.render({
        mapFn: this.state.mapFn,
        values: this.state.values,
        initialCount: this.state.incrementAmount,
        startAt: this.state.startAt,
      })
    );
    this.state.startAt += this.state.incrementAmount;
    if (this.state.startAt < this.state.values.length) {
      this.nextScheduledFrameWork = requestAnimationFrame(
        this.animationFrameHandler
      );
    }
  };
}
export const incrementalRepeat = directive(IncrementalRepeat);
