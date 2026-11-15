import { useEffect, useRef, useState } from "react";
import { Check, ChevronDown } from "lucide-react";

interface TixSelectOption {
  value: string;
  label: string;
}

interface TixSelectProps {
  name?: string;
  ariaLabel?: string;
  value: string;
  onChange: (value: string) => void;
  options: TixSelectOption[];
  placeholder?: string;
  disabled?: boolean;
  required?: boolean;
}

export default function TixSelect({
  name,
  ariaLabel,
  value,
  onChange,
  options,
  placeholder,
  disabled,
  required,
}: TixSelectProps) {
  const [isOpen, setIsOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  const selectedOption = options.find((opt) => opt.value === value);
  const displayLabel = selectedOption ? selectedOption.label : placeholder || "";

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setIsOpen(false);
      }
    };
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        setIsOpen(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, []);

  return (
    <div
      className={`tix-custom-select-wrap ${isOpen ? "is-open" : ""}`}
      ref={dropdownRef}
    >
      {name && <input type="hidden" name={name} value={value} required={required} />}

      <button
        type="button"
        className={`tix-custom-select-trigger ${disabled ? "is-disabled" : ""}`}
        onClick={() => {
          if (!disabled) setIsOpen((prev) => !prev);
        }}
        disabled={disabled}
        aria-label={ariaLabel ? `${ariaLabel}: ${displayLabel}` : undefined}
        aria-haspopup="listbox"
        aria-expanded={isOpen}
      >
        <span
          className={`tix-custom-select-label ${
            !selectedOption ? "tix-custom-select-placeholder" : ""
          }`}
        >
          {displayLabel}
        </span>
        <ChevronDown size={15} className="tix-custom-select-chevron" />
      </button>

      {isOpen && (
        <div className="tix-custom-select-menu" role="listbox">
          {!required && placeholder && (
            <button
              type="button"
              role="option"
              aria-selected={!value}
              className={`tix-custom-select-item ${!value ? "is-active" : ""}`}
              onClick={() => {
                onChange("");
                setIsOpen(false);
              }}
            >
              <span>{placeholder}</span>
              {!value && <Check size={14} className="tix-select-check-icon" />}
            </button>
          )}
          {options.map((opt) => (
            <button
              key={opt.value}
              type="button"
              role="option"
              aria-selected={value === opt.value}
              className={`tix-custom-select-item ${
                value === opt.value ? "is-active" : ""
              }`}
              onClick={() => {
                onChange(opt.value);
                setIsOpen(false);
              }}
            >
              <span>{opt.label}</span>
              {value === opt.value && (
                <Check size={14} className="tix-select-check-icon" />
              )}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
