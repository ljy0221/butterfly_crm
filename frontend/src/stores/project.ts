import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { Role } from "@/api/types";

export interface CurrentProject {
  projectId: number;
  name: string;
  role: Role;
}

interface ProjectState {
  current: CurrentProject | null;
  setCurrent: (p: CurrentProject | null) => void;
  clear: () => void;
}

export const useProjectStore = create<ProjectState>()(
  persist(
    (set) => ({
      current: null,
      setCurrent: (p) => set({ current: p }),
      clear: () => set({ current: null }),
    }),
    {
      name: "butterfly-project",
      storage: {
        getItem: (key) => {
          const raw = sessionStorage.getItem(key);
          try { return raw ? JSON.parse(raw) : null; } catch { return null; }
        },
        setItem: (key, value) => sessionStorage.setItem(key, JSON.stringify(value)),
        removeItem: (key) => sessionStorage.removeItem(key),
      },
    },
  ),
);
