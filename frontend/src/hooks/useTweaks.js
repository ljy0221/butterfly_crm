import React from "react";

// Single source of truth for tweak values. setTweak persists via the host
// (__edit_mode_set_keys — used by the design-preview harness).
export function useTweaks(defaults) {
  const [values, setValues] = React.useState(defaults);
  const setTweak = React.useCallback((key, val) => {
    setValues((prev) => ({ ...prev, [key]: val }));
    try {
      window.parent.postMessage({ type: "__edit_mode_set_keys", edits: { [key]: val } }, "*");
    } catch (_) {
      // host not present — safe to ignore in standalone dev
    }
  }, []);
  return [values, setTweak];
}
