import { Input, BirthDatePicker } from "@/components/common";

export interface PersonalInfoValue {
  name: string;
  phone: string;
  birthDate: string;
}

export interface PersonalInfoErrors {
  name?: string;
  phone?: string;
  birthDate?: string;
}

interface Props {
  value: PersonalInfoValue;
  onChange: (next: PersonalInfoValue) => void;
  errors?: PersonalInfoErrors;
}

export default function PersonalInfoStep({ value, onChange, errors }: Props) {
  const set = <K extends keyof PersonalInfoValue>(
    key: K,
    v: PersonalInfoValue[K],
  ) => onChange({ ...value, [key]: v });

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-3)" }}>
      <Input
        label="이름"
        value={value.name}
        onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
          set("name", e.target.value)
        }
        placeholder="홍길동"
        autoFocus
        maxLength={20}
        error={!!errors?.name}
        help={errors?.name}
      />
      <Input
        label="전화번호 (-제외)"
        value={value.phone}
        onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
          set("phone", e.target.value.replace(/[^0-9]/g, ""))
        }
        placeholder="01012345678"
        maxLength={11}
        error={!!errors?.phone}
        help={errors?.phone}
      />
      <BirthDatePicker
        value={value.birthDate}
        onChange={(v) => set("birthDate", v)}
        error={errors?.birthDate}
      />
    </div>
  );
}

