import { useState } from "react";
import { Minus, Plus } from "lucide-react";
import { Trans, useTranslation } from "react-i18next";

type FaqItem = {
  question: string;
  answer: string;
};

const FEATURED_FAQ_PICKS: ReadonlyArray<{
  category: "booking" | "tickets" | "refunds" | "organizer";
  index: number;
}> = [
  { category: "booking", index: 0 },
  { category: "booking", index: 1 },
  { category: "booking", index: 2 },
  { category: "tickets", index: 0 },
  { category: "refunds", index: 1 },
  { category: "organizer", index: 0 },
];

export default function FaqSection() {
  const { t } = useTranslation();
  const [openQuestionIndex, setOpenQuestionIndex] = useState<number | null>(0);

  const featuredItems: FaqItem[] = FEATURED_FAQ_PICKS.map(({ category, index }) => {
    const items = t(`faq.categories.${category}.items`, { returnObjects: true }) as FaqItem[];
    return Array.isArray(items) ? items[index] : undefined;
  }).filter((item): item is FaqItem => Boolean(item?.question && item?.answer));

  const handleToggleQuestion = (index: number) => {
    setOpenQuestionIndex((prev) => (prev === index ? null : index));
  };

  return (
    <section className="discovery-faq" aria-labelledby="discovery-faq-title">
      <div className="discovery-faq-layout">
        <aside className="discovery-faq-aside">
          <h2 id="discovery-faq-title">
            <Trans
              i18nKey="faq.title"
              components={{ accent: <span className="discovery-heading-accent" /> }}
            />
          </h2>
          <p>{t("faq.subtitle")}</p>

          <div className="discovery-faq-illustration" aria-hidden="true">
            <img src="/promo/faq-illustration.webp" alt="" loading="lazy" />
          </div>
        </aside>

        <div className="discovery-faq-list">
          {featuredItems.map((item, index) => {
            const isOpen = openQuestionIndex === index;
            const panelId = `faq-answer-${index}`;
            return (
              <article
                key={item.question}
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
                        <Minus size={18} strokeWidth={2} />
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
