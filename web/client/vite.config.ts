/// <reference types="vitest" />
import { readFileSync } from 'node:fs';
import react from '@vitejs/plugin-react';
import { defineConfig, searchForWorkspaceRoot, type Plugin } from 'vite';

const LICENSE_FILE = 'LICENSE.txt';
const BANNER =
  '/*! ProteoSpectra-Explorer | Copyright 2026 Maxime Ethier - Consultant en Bioinformatique/' +
  `Biocomputing Consultant | Apache-2.0, see ${LICENSE_FILE} */\n`;

/**
 * Tells whether a bundled file contains code of this application, and not only libraries.
 *
 * @param chunk bundled JavaScript file
 * @returns true if one of its modules comes from src/
 */
function hasApplicationCode(chunk: { moduleIds: readonly string[] }): boolean {
  return chunk.moduleIds.some((id) => {
    const path = id.replaceAll('\\', '/');
    return path.includes('/src/') && !path.includes('/node_modules/');
  });
}

/**
 * Publishes the repository's LICENSE as LICENSE.txt next to the application, in development
 * too, and puts a copyright banner at the top of the JavaScript files of the build holding
 * application code: minification removes the licence headers of the sources.
 */
function license(): Plugin {
  const text = readFileSync(new URL('../../LICENSE', import.meta.url), 'utf8');
  return {
    name: 'license',
    configureServer(server) {
      server.middlewares.use(`/${LICENSE_FILE}`, (_request, response) => {
        response.setHeader('Content-Type', 'text/plain; charset=utf-8');
        response.end(text);
      });
    },
    generateBundle: {
      // After Vite's own hooks, which may still add code at the top of the files.
      order: 'post',
      handler(_options, bundle) {
        this.emitFile({ type: 'asset', fileName: LICENSE_FILE, source: text });
        for (const output of Object.values(bundle)) {
          if (output.type === 'chunk' && hasApplicationCode(output)) {
            output.code = BANNER + output.code;
          }
        }
      },
    },
  };
}

const { version } = JSON.parse(readFileSync(new URL('package.json', import.meta.url), 'utf8')) as {
  version: string;
};

export default defineConfig({
  plugins: [react(), license()],
  define: {
    __APP_VERSION__: JSON.stringify(version),
  },
  server: {
    port: 5173,
    // The REST API (web/server) runs on port 7070; the browser only talks to Vite.
    proxy: {
      '/api': 'http://127.0.0.1:7070',
    },
    // The sample files are shared with the desktop application, in the repository's samples/.
    fs: {
      allow: [searchForWorkspaceRoot(process.cwd()), '../../samples'],
    },
  },
  test: {
    environment: 'jsdom',
  },
});
