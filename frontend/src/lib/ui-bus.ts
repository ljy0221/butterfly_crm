import type { ReactNode } from "react";

export type ToastKind = "info" | "success" | "warning" | "error";

export interface ModalRenderProps {
  close: () => void;
}

export type ModalRender = (props: ModalRenderProps) => ReactNode;

export interface ModalOptions {
  /**
   * false 로 두면 Esc / 배경 클릭으로 닫히지 않는다.
   * API 키 1회 평문 노출처럼 사용자가 명시적 액션을 취해야 하는 모달에 사용.
   * 기본값 true.
   */
  dismissible?: boolean;
}

export type UIEvent =
  | { type: "toast"; msg: string; kind: ToastKind }
  | {
      type: "modal";
      render: ModalRender | null;
      options?: ModalOptions;
    }
  | {
      type: "confirm";
      opts: ConfirmOptions;
      res: (value: boolean) => void;
    };

export interface ConfirmOptions {
  title?: string;
  message?: string;
  confirmText?: string;
  cancelText?: string;
  tone?: "default" | "danger";
}

type Subscriber = (event: UIEvent) => void;

const subscribers = new Set<Subscriber>();

export const UIBus = {
  on(fn: Subscriber): () => void {
    subscribers.add(fn);
    return () => {
      subscribers.delete(fn);
    };
  },
  emit(event: UIEvent): void {
    subscribers.forEach((f) => f(event));
  },
};

export const toast = (msg: string, kind: ToastKind = "info"): void =>
  UIBus.emit({ type: "toast", msg, kind });

export const confirmDialog = (opts: ConfirmOptions): Promise<boolean> =>
  new Promise<boolean>((res) => UIBus.emit({ type: "confirm", opts, res }));

export const openModal = (render: ModalRender, options?: ModalOptions): void =>
  UIBus.emit({ type: "modal", render, options });

export const closeModal = (): void => UIBus.emit({ type: "modal", render: null });
