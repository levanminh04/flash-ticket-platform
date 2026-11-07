import { ChevronDown } from "lucide-react";
import { useEffect, useRef } from "react";
import { useTranslation } from "react-i18next";
import {
  LANGUAGE_STORAGE_KEY,
  SUPPORTED_LANGUAGES,
  SupportedLanguage,
} from "../../i18n";

const languageOptions: Record<
  SupportedLanguage,
  { label: string; shortLabel: string; flagClass: string }
> = {
  vi: {
    label: "Vietnamese",
    shortLabel: "VI",
    flagClass: "language-flag-vietnam",
  },
  en: {
    label: "English",
    shortLabel: "EN",
    flagClass: "language-flag-english",
  },
};

type LanguageSwitcherProps = {
  className?: string;
};

export default function LanguageSwitcher({ className = "" }: LanguageSwitcherProps) {
  const { i18n, t } = useTranslation();
  const currentLanguage = i18n.resolvedLanguage === "en" ? "en" : "vi";
  const currentLanguageOption = languageOptions[currentLanguage];
  const menuRef = useRef<HTMLDetailsElement>(null);

  useEffect(() => {
    const closeOnOutsideClick = (event: PointerEvent) => {
      if (!menuRef.current?.contains(event.target as Node)) {
        menuRef.current?.removeAttribute("open");
      }
    };
    document.addEventListener("pointerdown", closeOnOutsideClick);
    return () => document.removeEventListener("pointerdown", closeOnOutsideClick);
  }, []);

  const handleLanguageChange = (language: SupportedLanguage) => {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, language);
    void i18n.changeLanguage(language);
    menuRef.current?.removeAttribute("open");
    menuRef.current?.querySelector("summary")?.focus();
  };

  return (
    <details
      className={`language-switcher ${className}`.trim()}
      ref={menuRef}
      onKeyDown={(event) => {
        const options = Array.from(event.currentTarget.querySelectorAll<HTMLButtonElement>("[role=menuitemradio]"));
        const currentIndex = options.indexOf(document.activeElement as HTMLButtonElement);
        if (["ArrowDown", "ArrowUp", "Home", "End"].includes(event.key)) {
          event.preventDefault();
          event.currentTarget.open = true;
          let nextIndex = 0;
          if (event.key === "End") nextIndex = options.length - 1;
          if (event.key === "ArrowDown") nextIndex = (currentIndex + 1) % options.length;
          if (event.key === "ArrowUp") nextIndex = (currentIndex <= 0 ? options.length : currentIndex) - 1;
          options[nextIndex]?.focus();
        }
        if (event.key === "Escape") {
          event.currentTarget.open = false;
          event.currentTarget.querySelector("summary")?.focus();
        }
      }}
    >
      <summary
        className="language-switcher-trigger"
        aria-label={t("common.language")}
        title={t("common.language")}
      >
        <span
          className={`language-switcher-flag ${currentLanguageOption.flagClass}`}
          aria-hidden="true"
        />
        <span className="language-switcher-current-code" aria-hidden="true">
          {currentLanguageOption.shortLabel}
        </span>
        <ChevronDown size={14} aria-hidden="true" />
      </summary>
      <div className="language-switcher-menu" role="menu" aria-label={t("common.language")}>
        {SUPPORTED_LANGUAGES.map((language) => (
          <button
            key={language}
            type="button"
            className={`language-switcher-option ${
              language === currentLanguage ? "is-active" : ""
            }`.trim()}
            onClick={() => handleLanguageChange(language)}
            role="menuitemradio"
            aria-checked={language === currentLanguage}
            aria-label={languageOptions[language].label}
          >
            <span
              className={`language-switcher-flag ${languageOptions[language].flagClass}`}
              aria-hidden="true"
            />
            <span className="language-switcher-code">{languageOptions[language].shortLabel}</span>
          </button>
        ))}
      </div>
    </details>
  );
}
