export const UIBus = (() => {
  const subs = new Set();
  return {
    on(fn) { subs.add(fn); return () => subs.delete(fn); },
    emit(e) { subs.forEach((f) => f(e)); },
  };
})();

export const toast = (msg, kind = "info") => UIBus.emit({ type: "toast", msg, kind });

export const confirmDialog = (opts) =>
  new Promise((res) => UIBus.emit({ type: "confirm", opts, res }));

export const openModal = (render) => UIBus.emit({ type: "modal", render });

export const closeModal = () => UIBus.emit({ type: "modal", render: null });
