import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    watch: {
      // Fallback for Linux inotify watcher exhaustion (ENOSPC). Slightly more
      // CPU, but works without raising fs.inotify.max_user_watches via sudo.
      usePolling: true,
      interval: 300,
    },
  },
})
