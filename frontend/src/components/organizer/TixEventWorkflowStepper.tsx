import React from "react";
import { useTranslation } from "react-i18next";
import { Check } from "lucide-react";
import "./tix-workflow-stepper.css";

export type WorkflowStepId = "info" | "media" | "sectors" | "tickets" | "review";

export interface TixEventWorkflowStepperProps {
  currentStep: WorkflowStepId;
  mode?: "QUANTITY" | "SEAT_MAP";
  eventId?: string;
  hasImages?: boolean;
  hasSectors?: boolean;
  hasTickets?: boolean;
  allCompleted?: boolean;
  onStepClick?: (step: WorkflowStepId) => void;
  className?: string;
}

interface StepDefinition {
  id: WorkflowStepId;
  stepNumber: number;
  tagKey: string;
  defaultTag: string;
  titleKey: string;
  defaultTitle: string;
}

export default function TixEventWorkflowStepper({
  currentStep,
  mode = "QUANTITY",
  eventId,
  hasImages = false,
  hasSectors = false,
  hasTickets = false,
  allCompleted = false,
  onStepClick,
  className = "",
}: TixEventWorkflowStepperProps) {
  const { t } = useTranslation();

  const steps: StepDefinition[] =
    mode === "SEAT_MAP"
      ? [
          {
            id: "info",
            stepNumber: 1,
            tagKey: "organizerStepper.step1Tag",
            defaultTag: "Bước 1",
            titleKey: "organizerStepper.step1Title",
            defaultTitle: "Thông tin sự kiện",
          },
          {
            id: "media",
            stepNumber: 2,
            tagKey: "organizerStepper.step2Tag",
            defaultTag: "Bước 2",
            titleKey: "organizerStepper.step2Title",
            defaultTitle: "Hình ảnh & Banner",
          },
          {
            id: "sectors",
            stepNumber: 3,
            tagKey: "organizerStepper.step3Tag",
            defaultTag: "Bước 3",
            titleKey: "organizerStepper.step3TitleSectors",
            defaultTitle: "Sơ đồ khu vực",
          },
          {
            id: "tickets",
            stepNumber: 4,
            tagKey: "organizerStepper.step4Tag",
            defaultTag: "Bước 4",
            titleKey: "organizerStepper.step4TitleSeatMapTickets",
            defaultTitle: "Loại vé & Gán ghế",
          },
          {
            id: "review",
            stepNumber: 5,
            tagKey: "organizerStepper.step5Tag",
            defaultTag: "Bước 5",
            titleKey: "organizerStepper.step5Title",
            defaultTitle: "Xem lại & Gửi duyệt",
          },
        ]
      : [
          {
            id: "info",
            stepNumber: 1,
            tagKey: "organizerStepper.step1Tag",
            defaultTag: "Bước 1",
            titleKey: "organizerStepper.step1Title",
            defaultTitle: "Thông tin sự kiện",
          },
          {
            id: "media",
            stepNumber: 2,
            tagKey: "organizerStepper.step2Tag",
            defaultTag: "Bước 2",
            titleKey: "organizerStepper.step2Title",
            defaultTitle: "Hình ảnh & Banner",
          },
          {
            id: "tickets",
            stepNumber: 3,
            tagKey: "organizerStepper.step3Tag",
            defaultTag: "Bước 3",
            titleKey: "organizerStepper.step3TitleQuantity",
            defaultTitle: "Hạng vé & Giá bán",
          },
          {
            id: "review",
            stepNumber: 4,
            tagKey: "organizerStepper.step4Tag",
            defaultTag: "Bước 4",
            titleKey: "organizerStepper.step4Title",
            defaultTitle: "Xem lại & Gửi duyệt",
          },
        ];

  const currentStepIndex = steps.findIndex((s) => s.id === currentStep);

  const isStepCompleted = (step: StepDefinition, index: number) => {
    if (!eventId) return false;
    if (step.id === "info") return true;
    if (step.id === "media") return currentStepIndex > index || hasImages;
    if (step.id === "sectors") return currentStepIndex > index || hasSectors;
    if (step.id === "tickets") return currentStepIndex > index || hasTickets;
    return index < currentStepIndex;
  };

  const isStepDisabled = (index: number) => {
    if (!eventId) {
      return index > 0;
    }
    return false;
  };

  return (
    <nav
      className={`tix-workflow-stepper-wrap ${mode === "SEAT_MAP" ? "is-five-steps" : ""} ${className}`}
      aria-label={t("organizerStepper.navAriaLabel", "Quy trình thiết lập sự kiện")}
    >
      <div className="tix-workflow-stepper-track">
        {steps.map((step, index) => {
          const isActive = !allCompleted && step.id === currentStep;
          const completed = allCompleted || (isStepCompleted(step, index) && !isActive);
          const disabled = isStepDisabled(index);
          const isPending = !isActive && !completed;

          return (
            <React.Fragment key={step.id}>
              {index > 0 && (
                <div
                  className={`tix-workflow-connector-line ${
                    allCompleted || index <= currentStepIndex || (index === 1 && Boolean(eventId))
                      ? "is-active"
                      : ""
                  }`}
                  aria-hidden="true"
                />
              )}

              <div
                className={`tix-workflow-step-item ${
                  isActive
                    ? "is-active"
                    : completed
                    ? "is-completed"
                    : isPending
                    ? "is-pending"
                    : ""
                }`}
              >
                <button
                  type="button"
                  className="tix-workflow-node-button"
                  disabled={disabled}
                  onClick={() => {
                    if (!disabled && onStepClick) {
                      onStepClick(step.id);
                    }
                  }}
                  aria-current={isActive ? "step" : undefined}
                >
                  <div className="tix-workflow-node-circle">
                    {completed ? (
                      <Check size={16} strokeWidth={2.6} />
                    ) : (
                      <span>{step.stepNumber}</span>
                    )}
                  </div>

                  <div className="tix-workflow-node-text">
                    <span className="tix-workflow-node-step-tag">
                      {t(step.tagKey, step.defaultTag)}
                    </span>
                    <span className="tix-workflow-node-title">
                      {t(step.titleKey, step.defaultTitle)}
                    </span>
                  </div>
                </button>
              </div>
            </React.Fragment>
          );
        })}
      </div>
    </nav>
  );
}
