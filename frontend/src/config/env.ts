// Centralized environment config for reusable paths.
// Adjust Vite env vars (e.g. VITE_LOGISIM_PREVIEW_PREFIX) in one place and reuse across components.

export const LOGISIM_PREVIEW_PREFIX =
  (import.meta.env.VITE_LOGISIM_PREVIEW_PREFIX as string | undefined)?.trim() ||
  '/api/files';
