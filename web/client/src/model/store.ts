/*
 * Copyright 2026 Maxime Ethier - Consultant en Bioinformatique/Biocomputing Consultant
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Observable value of the Model, the counterpart of a JavaFX property: the views subscribe to it
 * (see controllers/useStore.ts) and are drawn again when it changes.
 */
export class Store<T> {
  private readonly listeners = new Set<() => void>();

  constructor(private value: T) {}

  /** Returns the current value. */
  readonly get = (): T => this.value;

  /** Replaces the value and notifies the listeners, unless it is the same value. */
  set(value: T): void {
    if (!Object.is(value, this.value)) {
      this.value = value;
      this.listeners.forEach((listener) => listener());
    }
  }

  /**
   * Calls `listener` after each change.
   *
   * @returns a function removing the listener
   */
  readonly subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  };
}
