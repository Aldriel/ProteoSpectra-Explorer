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

import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { Footer } from './Footer';

afterEach(() => {
  cleanup();
});

describe('Footer', () => {
  it('signs in French with the website, the contact, the licence and Ko-fi', () => {
    render(<Footer />);

    const website = screen.getByRole('link', { name: /Maxime Ethier/ });
    expect(website.getAttribute('href')).toBe('https://www.maximeethier.com');
    expect(
      screen.getByRole('link', { name: 'contact@maximeethier.com' }).getAttribute('href'),
    ).toBe('mailto:contact@maximeethier.com');
    expect(screen.getByRole('link', { name: 'Licence Apache 2.0' }).getAttribute('href')).toBe(
      '/LICENSE.txt',
    );
    expect(screen.getByRole('link', { name: 'Soutenez-moi sur Ko-fi' })).toBeTruthy();
  });

  it('signs in English with the English website and licence name', () => {
    render(<Footer language="en" />);

    const website = screen.getByRole('link', { name: /Biocomputing Consultant/ });
    expect(website.getAttribute('href')).toBe('https://www.maximeethier.com/en');
    expect(screen.getByRole('link', { name: 'Apache License 2.0' })).toBeTruthy();
  });
});
