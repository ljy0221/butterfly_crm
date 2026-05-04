import type { ComponentType, ReactNode, CSSProperties } from "react";

type AnyProps = {
  [key: string]: unknown;
  children?: ReactNode;
  style?: CSSProperties;
};

export const Sidebar: ComponentType<AnyProps>;
export const Topbar: ComponentType<AnyProps>;
