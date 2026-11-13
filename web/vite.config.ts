import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Served at mehdiacho.tech/projects/recur/ (see docs/HANDOFF.md, "Website").
export default defineConfig({
  base: '/projects/recur/',
  plugins: [react()],
  test: { include: ['test/**/*.test.ts'] },
} as never);
