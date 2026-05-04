import { Input } from "@/components/common";

interface Props {
  value: string;
  onChange: (next: string) => void;
  error?: string | null;
}

export default function CompanyAuthStep({ value, onChange, error }: Props) {
  return (
    <div>
      <Input
        label="회사 인증번호"
        value={value}
        onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
          onChange(e.target.value)
        }
        placeholder="발급받은 회사 인증번호를 입력하세요"
        autoFocus
        error={!!error}
        help={error ?? "신청서 승인 메일에 포함된 회사 인증번호를 입력해주세요"}
      />
    </div>
  );
}
