/**
 * 기존 .jsx 공용 컴포넌트의 ambient 타입 선언.
 *
 * .jsx에서 destructured props는 tsc가 strict하게 required로 추론해버려
 * .tsx 호출부에서 빈번한 TS2741 (Property 'style' is missing) 에러를 내뿜는다.
 * 도메인 작업 중에 각 컴포넌트를 점진적으로 .tsx 변환할 때까지의
 * 임시 호환 레이어로 모든 prop을 optional any로 선언한다.
 *
 * 추후 컴포넌트별로 정확한 props 타입을 작성하면 이 파일은 줄여 나간다.
 */

import type { ComponentType, ReactNode, CSSProperties } from "react";

type AnyProps = {
  [key: string]: unknown;
  children?: ReactNode;
  style?: CSSProperties;
  className?: string;
};

/**
 * Icon 사전 — Icon[name](...args). 대부분 (size?: number) 시그니처지만 일부(예: star)는
 * (filled?: boolean, size?: number) 등 제각각이라 ambient 단계에서는 가변 args 로 둔다.
 */
export const Icon: {
  [key: string]: (...args: unknown[]) => ReactNode;
};
export const Button: ComponentType<AnyProps>;
export const Tag: ComponentType<AnyProps>;
export const ChannelBadge: ComponentType<AnyProps>;
export const CampaignStatus: ComponentType<AnyProps>;
export const Card: ComponentType<AnyProps>;
export const StatCard: ComponentType<AnyProps>;
export const Input: ComponentType<AnyProps>;
export const Select: ComponentType<AnyProps>;
export const Avatar: ComponentType<AnyProps>;
export const Checkbox: ComponentType<AnyProps>;
export const Toggle: ComponentType<AnyProps>;
export const Menu: ComponentType<AnyProps>;
export const SearchInput: ComponentType<AnyProps>;
export const PageHeader: ComponentType<AnyProps>;
export const BirthDatePicker: ComponentType<{
  value: string;
  onChange: (v: string) => void;
  error?: string;
}>;
