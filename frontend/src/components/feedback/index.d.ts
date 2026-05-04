import type { ComponentType, ReactNode, CSSProperties } from "react";

type AnyProps = {
  [key: string]: unknown;
  children?: ReactNode;
  style?: CSSProperties;
};

export const ToastHost: ComponentType<AnyProps>;
export const ModalHost: ComponentType<AnyProps>;
