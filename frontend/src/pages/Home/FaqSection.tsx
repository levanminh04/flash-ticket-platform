import { useState } from "react";
import { ChevronRight, Plus, X } from "lucide-react";
import { Trans, useTranslation } from "react-i18next";

type FaqItem = {
  question: string;
  answer: string;
};

type FaqCategory = {
  id: string;
  label: string;
  items: FaqItem[];
};

const FAQ_CATEGORY_KEYS = [
  "booking",
  "tickets",
  "refunds",
  "account",
  "organizer",
] as const;

export default function FaqSection() {
  const { t } = useTranslation();
  const [activeCategoryId, setActiveCategoryId] = useState<string>(FAQ_CATEGORY_KEYS[0]);
  const [openQuestionIndex, setOpenQuestionIndex] = useState<number | null>(0);

  const categories: FaqCategory[] = FAQ_CATEGORY_KEYS.map((key) => ({
    id: key,
    label: t(`faq.categories.${key}.label`),
    items: t(`faq.categories.${key}.items`, { returnObjects: true }) as FaqItem[],
  }));

  const currentCategory =
    categories.find((cat) => cat.id === activeCategoryId) ?? categories[0];

  const handleSelectCategory = (categoryId: string) => {
    setActiveCategoryId(categoryId);
    setOpenQuestionIndex(0);
  };

  const handleToggleQuestion = (index: number) => {
    setOpenQuestionIndex((prev) => (prev === index ? null : index));
  };

  return (
    <section className="discovery-faq" aria-labelledby="discovery-faq-title">
      <header className="discovery-faq-header">
        <h2 id="discovery-faq-title">
          <Trans
            i18nKey="faq.title"
            components={{ accent: <span className="discovery-heading-accent" /> }}
          />
        </h2>
        <p>{t("faq.subtitle")}</p>
      </header>

      <div className="discovery-faq-grid">
        <div
          className="discovery-faq-categories"
          role="tablist"
          aria-label={t("faq.categoryListLabel")}
        >
          {categories.map((category) => {
            const isActive = category.id === currentCategory.id;
            return (
              <button
                key={category.id}
                type="button"
                role="tab"
                id={`faq-tab-${category.id}`}
                aria-selected={isActive}
                aria-controls={`faq-panel-${category.id}`}
                className={`discovery-faq-category${isActive ? " is-active" : ""}`}
                onClick={() => handleSelectCategory(category.id)}
              >
                <span>{category.label}</span>
                <ChevronRight size={17} strokeWidth={2} aria-hidden="true" />
              </button>
            );
          })}
        </div>

        <div
          className="discovery-faq-list"
          role="tabpanel"
          id={`faq-panel-${currentCategory.id}`}
          aria-labelledby={`faq-tab-${currentCategory.id}`}
        >
          {Array.isArray(currentCategory.items) &&
            currentCategory.items.map((item, index) => {
              const isOpen = openQuestionIndex === index;
              const panelId = `faq-answer-${currentCategory.id}-${index}`;
              return (
                <article
                  key={`${currentCategory.id}-${index}`}
                  className={`discovery-faq-item${isOpen ? " is-open" : ""}`}
                >
                  <h3 className="discovery-faq-question-heading">
                    <button
                      type="button"
                      className="discovery-faq-trigger"
                      aria-expanded={isOpen}
                      aria-controls={panelId}
                      onClick={() => handleToggleQuestion(index)}
                    >
                      <span>{item.question}</span>
                      <span className="discovery-faq-icon" aria-hidden="true">
                        {isOpen ? (
                          <X size={18} strokeWidth={2} />
                        ) : (
                          <Plus size={18} strokeWidth={2} />
                        )}
                      </span>
                    </button>
                  </h3>
                  {isOpen && (
                    <div id={panelId} className="discovery-faq-answer">
                      <p>{item.answer}</p>
                    </div>
                  )}
                </article>
              );
            })}
        </div>
      </div>
    </section>
  );
}
