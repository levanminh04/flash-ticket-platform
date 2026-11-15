import { FormEvent, useEffect, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { toast } from "react-toastify";
import {
  AlertTriangle,
  CheckCircle2,
  ChevronDown,
  Copy,
  Check,
  LoaderCircle,
  QrCode,
  RotateCcw,
  ShieldAlert,
  Trash2,
  XCircle,
  Calendar,
} from "lucide-react";
import OrganizerLayout from "../../components/organizer/OrganizerLayout";
import TixOrganizerTopNav from "../../components/organizer/TixOrganizerTopNav";
import { useOrganizerGate } from "./useOrganizerGate";
import {
  organizerDashboardService,
  OrganizerSummary,
} from "../../services/organizerDashboardService";
import { ticketService, CheckInResult } from "../../services/ticketService";
import "./organizer-dashboard.css";
import "./organizer-check-in.css";

function playBeep(success: boolean) {
  try {
    const AudioContextClass = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
    if (!AudioContextClass) return;
    const ctx = new AudioContextClass();
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.connect(gain);
    gain.connect(ctx.destination);
    if (success) {
      osc.frequency.setValueAtTime(880, ctx.currentTime);
      osc.frequency.setValueAtTime(1320, ctx.currentTime + 0.1);
      gain.gain.setValueAtTime(0.2, ctx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + 0.25);
      osc.start();
      osc.stop(ctx.currentTime + 0.25);
    } else {
      osc.type = "sawtooth";
      osc.frequency.setValueAtTime(220, ctx.currentTime);
      osc.frequency.setValueAtTime(165, ctx.currentTime + 0.15);
      gain.gain.setValueAtTime(0.3, ctx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + 0.35);
      osc.start();
      osc.stop(ctx.currentTime + 0.35);
    }
  } catch {
    // Ignore audio policy/context restrictions
  }
}

function formatDateTime(value: string | null | undefined, language: string) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString(language === "en" ? "en-US" : "vi-VN", {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
}

type PendingCheckInAttempt = {
  eventId: string;
  qrToken: string;
  idempotencyKey: string;
};

function isUnconfirmedNetworkError(error: any): boolean {
  if (!error) return false;
  if (error?.name === "CanceledError" || error?.code === "ERR_CANCELED") {
    return false;
  }
  if (!error?.response) {
    return true;
  }
  if (
    error?.code === "ECONNABORTED" ||
    /timeout/i.test(String(error?.message || ""))
  ) {
    return true;
  }
  const status = error?.response?.status;
  return status === 502 || status === 503 || status === 504;
}

export default function OrganizerCheckInPage() {
  const { i18n, t } = useTranslation();
  const language = i18n.resolvedLanguage || i18n.language;
  const gate = useOrganizerGate();
  const [params, setParams] = useSearchParams();
  const urlEventId = params.get("eventId") || "";

  const [events, setEvents] = useState<OrganizerSummary[]>([]);
  const [loadingEvents, setLoadingEvents] = useState(false);
  const [eventsError, setEventsError] = useState<string | null>(null);
  const [eventsReloadTick, setEventsReloadTick] = useState(0);
  const [selectedEventId, setSelectedEventId] = useState(urlEventId);
  const [isEventDropdownOpen, setIsEventDropdownOpen] = useState(false);

  const [qrToken, setQrToken] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<CheckInResult | null>(null);
  const [unconfirmedAttempt, setUnconfirmedAttempt] =
    useState<PendingCheckInAttempt | null>(null);
  const [history, setHistory] = useState<CheckInResult[]>([]);
  const [copiedId, setCopiedId] = useState<string | null>(null);

  const inputRef = useRef<HTMLInputElement>(null);
  const eventDropdownRef = useRef<HTMLDivElement>(null);
  const pendingAttemptRef = useRef<PendingCheckInAttempt | null>(null);

  useEffect(() => {
    if (!isEventDropdownOpen) return;
    const handleClickOutside = (event: MouseEvent) => {
      if (
        eventDropdownRef.current &&
        !eventDropdownRef.current.contains(event.target as Node)
      ) {
        setIsEventDropdownOpen(false);
      }
    };
    const handleEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setIsEventDropdownOpen(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    document.addEventListener("keydown", handleEscape);
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
      document.removeEventListener("keydown", handleEscape);
    };
  }, [isEventDropdownOpen]);

  // Load all published organizer events across cursor pages
  useEffect(() => {
    if (!gate.ready) return;
    const controller = new AbortController();
    setLoadingEvents(true);
    setEventsError(null);

    const loadAllPublishedEvents = async () => {
      const merged = new Map<string, OrganizerSummary>();
      const seenCursors = new Set<string>();
      let cursor: string | null = null;

      do {
        const res = await organizerDashboardService.events(
          cursor,
          controller.signal,
          "PUBLISHED",
          50,
        );
        (res.items || [])
          .filter((evt) => evt.status === "PUBLISHED")
          .forEach((evt) => merged.set(evt.eventId, evt));

        const nextCursor = res.nextCursor ?? null;
        if (nextCursor) {
          if (seenCursors.has(nextCursor)) {
            throw new Error("Repeated organizer event cursor");
          }
          seenCursors.add(nextCursor);
        }
        cursor = nextCursor;
      } while (cursor);

      return [...merged.values()];
    };

    loadAllPublishedEvents()
      .then((publishedItems) => {
        if (!controller.signal.aborted) {
          setEvents(publishedItems);
        }
      })
      .catch((err) => {
        if (controller.signal.aborted) return;
        console.error("Failed to load organizer events", err);
        setEventsError(
          err?.response?.data?.message || t("checkIn.loadEventsFailed"),
        );
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoadingEvents(false);
      });

    return () => controller.abort();
  }, [gate.ready, eventsReloadTick]);

  const handleRetryEvents = () => {
    setEventsReloadTick((tick) => tick + 1);
  };

  // Sync url param when event changes
  const handleSelectEvent = (eventId: string) => {
    setSelectedEventId(eventId);
    if (pendingAttemptRef.current && pendingAttemptRef.current.eventId !== eventId) {
      pendingAttemptRef.current = null;
      setUnconfirmedAttempt(null);
    }
    if (eventId) {
      setParams({ eventId });
    } else {
      setParams({});
    }
    inputRef.current?.focus();
  };

  const handleQrTokenChange = (value: string) => {
    setQrToken(value);
    const trimmed = value.trim();
    if (
      unconfirmedAttempt &&
      (unconfirmedAttempt.qrToken !== trimmed ||
        unconfirmedAttempt.eventId !== selectedEventId)
    ) {
      setUnconfirmedAttempt(null);
    }
  };

  const handleClearPendingRetry = () => {
    pendingAttemptRef.current = null;
    setUnconfirmedAttempt(null);
    setQrToken("");
    inputRef.current?.focus();
  };

  const handleCopy = (text: string) => {
    navigator.clipboard.writeText(text);
    setCopiedId(text);
    setTimeout(() => setCopiedId(null), 2000);
  };

  const submitCheckIn = async (eventId: string, trimmedToken: string) => {
    const existingPending = pendingAttemptRef.current;
    const idempotencyKey =
      existingPending &&
      existingPending.eventId === eventId &&
      existingPending.qrToken === trimmedToken
        ? existingPending.idempotencyKey
        : crypto.randomUUID();

    pendingAttemptRef.current = {
      eventId,
      qrToken: trimmedToken,
      idempotencyKey,
    };

    setSubmitting(true);
    try {
      const response = await ticketService.checkIn(
        eventId,
        trimmedToken,
        idempotencyKey,
      );

      pendingAttemptRef.current = null;
      setUnconfirmedAttempt(null);
      setResult(response);
      setHistory((current) => [response, ...current].slice(0, 20));
      setQrToken("");
      playBeep(response.succeeded);

      if (response.succeeded) {
        toast.success(t("checkIn.results.SUCCESS"));
      } else {
        const resultMsg =
          t(`checkIn.results.${response.result}`, {
            defaultValue: response.result,
          });
        toast.error(resultMsg);
      }
    } catch (error: any) {
      playBeep(false);
      if (isUnconfirmedNetworkError(error)) {
        pendingAttemptRef.current = {
          eventId,
          qrToken: trimmedToken,
          idempotencyKey,
        };
        setResult(null);
        setUnconfirmedAttempt({
          eventId,
          qrToken: trimmedToken,
          idempotencyKey,
        });
        toast.warning(t("checkIn.unconfirmedToast"));
        return;
      }

      pendingAttemptRef.current = null;
      setUnconfirmedAttempt(null);
      const code = error?.response?.data?.code;
      const fallbackMessage =
        error?.response?.data?.message || t("checkIn.requestFailed");
      const errorMsg = code
        ? t(`checkIn.results.${code}`, { defaultValue: fallbackMessage })
        : fallbackMessage;
      toast.error(String(errorMsg));
    } finally {
      setSubmitting(false);
      setTimeout(() => {
        inputRef.current?.focus();
      }, 50);
    }
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (!selectedEventId) {
      toast.warning(t("checkIn.eventRequired"));
      return;
    }

    const trimmedToken = qrToken.trim();
    if (!trimmedToken) {
      toast.warning(t("checkIn.qrRequired"));
      inputRef.current?.focus();
      return;
    }

    if (trimmedToken.length > 4096) {
      toast.error(t("checkIn.qrTooLong"));
      return;
    }

    await submitCheckIn(selectedEventId, trimmedToken);
  };

  const selectedEvent = events.find((e) => e.eventId === selectedEventId);

  const validCount = history.filter((item) => item.succeeded).length;
  const deniedCount = history.length - validCount;

  const getEventStatusClass = (status: string) => {
    if (status === "PUBLISHED") return "is-published";
    if (status === "CANCELLED") return "is-cancelled";
    return "is-other";
  };

  return (
    <OrganizerLayout
      hideSidebar
      hideHeader
      requireOrganizer
      className="organizer-hub-page"
    >
      <div className="tix-dashboard-page tix-check-in-page">
        <div className="tix-dashboard-container">
          <TixOrganizerTopNav activeTab="checkin" />

          <header className="tix-overview-header">
            <div className="tix-overview-header-left">
              <div className="tix-overview-title-block">
                <h1 className="tix-overview-main-title">{t("checkIn.title")}</h1>
                <p className="tix-overview-subtitle">{t("checkIn.description")}</p>
              </div>
              {(history.length > 0 || selectedEvent) && (
                <div className="tix-overview-badges-row">
                  {history.length > 0 && (
                    <>
                      <span className="tix-overview-badge">
                        <CheckCircle2 size={14} color="#059669" />
                        <span>{validCount}</span>
                      </span>
                      <span className="tix-overview-badge">
                        <XCircle size={14} color="#dc2626" />
                        <span>{deniedCount}</span>
                      </span>
                    </>
                  )}
                  {selectedEvent && (
                    <span className="tix-overview-badge">
                      <Calendar size={14} />
                      <span>{selectedEvent.title}</span>
                    </span>
                  )}
                </div>
              )}
            </div>
          </header>

          <section className="tix-checkin-grid">
            {/* Form scanning card */}
            <section className="tix-chart-card tix-checkin-scanner-card">
              {/* Event selector */}
              <div className="tix-checkin-field">
                <span className="tix-checkin-label" id="checkin-event-label">
                  {t("checkIn.selectEvent")}
                  <span className="tix-checkin-required" aria-hidden="true">*</span>
                </span>
                <div
                  className={`tix-dropdown-wrap tix-checkin-dropdown-wrap ${isEventDropdownOpen ? "is-open" : ""}`}
                  ref={eventDropdownRef}
                >
                  <button
                    type="button"
                    className={`tix-checkin-dropdown-btn ${!selectedEvent ? "is-placeholder" : ""}`}
                    disabled={loadingEvents}
                    onClick={() => setIsEventDropdownOpen((prev) => !prev)}
                    aria-labelledby="checkin-event-label"
                    aria-expanded={isEventDropdownOpen}
                    aria-haspopup="listbox"
                  >
                    <span className="tix-checkin-dropdown-value">
                      {selectedEvent ? selectedEvent.title : t("checkIn.selectEventPrompt")}
                    </span>
                    <ChevronDown
                      size={16}
                      className={`tix-chevron-icon ${isEventDropdownOpen ? "rotated" : ""}`}
                    />
                  </button>

                  {isEventDropdownOpen && (
                    <div
                      className="tix-dropdown-menu tix-checkin-dropdown-menu"
                      role="listbox"
                      aria-labelledby="checkin-event-label"
                    >
                      <button
                        type="button"
                        role="option"
                        aria-selected={selectedEventId === ""}
                        className={`tix-dropdown-item ${selectedEventId === "" ? "is-active" : ""}`}
                        onClick={() => {
                          handleSelectEvent("");
                          setIsEventDropdownOpen(false);
                        }}
                      >
                        <span className="tix-checkin-dropdown-item-label">
                          {t("checkIn.selectEventPrompt")}
                        </span>
                        {selectedEventId === "" && <Check size={14} className="tix-check-icon" />}
                      </button>
                      {events.map((evt) => (
                        <button
                          key={evt.eventId}
                          type="button"
                          role="option"
                          aria-selected={selectedEventId === evt.eventId}
                          className={`tix-dropdown-item ${selectedEventId === evt.eventId ? "is-active" : ""}`}
                          onClick={() => {
                            handleSelectEvent(evt.eventId);
                            setIsEventDropdownOpen(false);
                          }}
                        >
                          <span className="tix-checkin-dropdown-item-label">{evt.title}</span>
                          {selectedEventId === evt.eventId && (
                            <Check size={14} className="tix-check-icon" />
                          )}
                        </button>
                      ))}
                    </div>
                  )}
                </div>
                {!loadingEvents && !eventsError && events.length === 0 && (
                  <span className="tix-checkin-hint">{t("checkIn.noEvents")}</span>
                )}
              </div>

              {eventsError && (
                <div className="tix-checkin-alert is-error" role="alert">
                  <span className="tix-checkin-alert-msg">
                    <AlertTriangle size={15} style={{ flexShrink: 0 }} />
                    <span>{eventsError}</span>
                  </span>
                  <button
                    type="button"
                    onClick={handleRetryEvents}
                    disabled={loadingEvents}
                    className="tix-checkin-retry-inline-btn"
                  >
                    <RotateCcw size={12} />
                    <span>{t("checkIn.retryLoadEvents")}</span>
                  </button>
                </div>
              )}

              {selectedEvent && (
                <div className="tix-checkin-event-card">
                  <Calendar size={16} className="tix-checkin-event-icon" />
                  <div className="tix-checkin-event-body">
                    <div className="tix-checkin-event-top">
                      <span className="tix-checkin-event-name">{selectedEvent.title}</span>
                      <span
                        className={`tix-checkin-event-status ${getEventStatusClass(selectedEvent.status)}`}
                      >
                        {t(`dashboardV1.status.${selectedEvent.status}`, {
                          defaultValue: selectedEvent.status,
                        })}
                      </span>
                    </div>
                    <div className="tix-checkin-event-time">
                      {formatDateTime(selectedEvent.startsAt, language)}
                    </div>
                  </div>
                </div>
              )}

              {/* QR code token input */}
              <form onSubmit={handleSubmit} className="tix-checkin-form">
                <label className="tix-checkin-field">
                  <span className="tix-checkin-label">
                    {t("checkIn.qrData")}
                    <span className="tix-checkin-required" aria-hidden="true">*</span>
                  </span>
                  <div className="tix-checkin-input-wrap">
                    <QrCode size={16} className="tix-checkin-input-icon" />
                    <input
                      ref={inputRef}
                      type="text"
                      value={qrToken}
                      onChange={(e) => handleQrTokenChange(e.target.value)}
                      className="tix-checkin-input"
                      placeholder={t("checkIn.qrPlaceholder")}
                      disabled={submitting || !selectedEventId}
                      autoFocus
                      autoComplete="off"
                    />
                  </div>
                  <span className="tix-checkin-hint">
                    Hỗ trợ súng bắn mã vạch/QR tự động hoặc dán trực tiếp mã token.
                  </span>
                </label>

                <button
                  type="submit"
                  className="tix-primary-cta-btn tix-checkin-submit-btn"
                  disabled={submitting || !selectedEventId || !qrToken.trim()}
                >
                  {submitting ? (
                    <LoaderCircle size={18} className="spin" />
                  ) : (
                    <QrCode size={18} />
                  )}
                  <span>{t("checkIn.confirm")}</span>
                </button>
              </form>
            </section>

            {/* Results & History Stack */}
            <div className="tix-checkin-results-stack">
              {/* Latest Scan Result */}
              <section className="tix-chart-card">
                <div className="tix-card-header">
                  <h2 className="tix-card-title">{t("checkIn.latestResult")}</h2>
                </div>

                {unconfirmedAttempt ? (
                  <div className="tix-checkin-result-box is-unconfirmed" role="status">
                    <div className="tix-checkin-result-banner">
                      <AlertTriangle size={34} color="#d97706" style={{ flexShrink: 0 }} />
                      <div>
                        <h3 className="tix-checkin-result-title is-unconfirmed">
                          {t("checkIn.unconfirmedTitle")}
                        </h3>
                        <p className="tix-checkin-result-desc">
                          {t("checkIn.unconfirmedDescription")}
                        </p>
                      </div>
                    </div>

                    <div className="tix-checkin-meta-grid">
                      <div>
                        <span className="tix-checkin-meta-label">
                          {t("checkIn.idempotencyKey")}
                        </span>
                        <div className="tix-checkin-meta-val-row">
                          <code className="tix-checkin-code">
                            {unconfirmedAttempt.idempotencyKey}
                          </code>
                          <button
                            type="button"
                            onClick={() => handleCopy(unconfirmedAttempt.idempotencyKey)}
                            className="tix-checkin-copy-btn"
                            title={t("checkIn.copy")}
                            aria-label={t("checkIn.copy")}
                          >
                            {copiedId === unconfirmedAttempt.idempotencyKey ? (
                              <Check size={14} color="#059669" />
                            ) : (
                              <Copy size={14} />
                            )}
                          </button>
                        </div>
                      </div>
                    </div>

                    <div className="tix-checkin-unconfirmed-actions">
                      <button
                        type="button"
                        disabled={submitting}
                        onClick={() =>
                          submitCheckIn(
                            unconfirmedAttempt.eventId,
                            unconfirmedAttempt.qrToken,
                          )
                        }
                        className="tix-checkin-retry-btn"
                      >
                        {submitting ? (
                          <LoaderCircle size={15} className="spin" />
                        ) : (
                          <RotateCcw size={15} />
                        )}
                        <span>{t("checkIn.retrySameKey")}</span>
                      </button>

                      <button
                        type="button"
                        disabled={submitting}
                        onClick={handleClearPendingRetry}
                        className="tix-checkin-secondary-btn"
                      >
                        <span>{t("checkIn.scanAnother")}</span>
                      </button>
                    </div>
                  </div>
                ) : result ? (
                  <div
                    className={`tix-checkin-result-box ${
                      result.succeeded ? "is-valid" : "is-denied"
                    }`}
                  >
                    <div className="tix-checkin-result-banner">
                      {result.succeeded ? (
                        <CheckCircle2 size={34} color="#059669" style={{ flexShrink: 0 }} />
                      ) : result.result === "EVENT_CANCELLED" || result.result === "VOID" ? (
                        <XCircle size={34} color="#dc2626" style={{ flexShrink: 0 }} />
                      ) : (
                        <AlertTriangle size={34} color="#d97706" style={{ flexShrink: 0 }} />
                      )}
                      <div>
                        <h3
                          className={`tix-checkin-result-title ${
                            result.succeeded ? "is-valid" : "is-denied"
                          }`}
                        >
                          {result.succeeded
                            ? t("checkIn.validAdmission")
                            : t("checkIn.deniedAdmission")}
                        </h3>
                        <p className="tix-checkin-result-desc">
                          <strong>{t("checkIn.status")}: </strong>
                          {t(`checkIn.results.${result.result}`, { defaultValue: result.result })}
                        </p>
                      </div>
                    </div>

                    <div className="tix-checkin-meta-grid">
                      {result.ticketId && (
                        <div>
                          <span className="tix-checkin-meta-label">{t("checkIn.ticketId")}</span>
                          <div className="tix-checkin-meta-val-row">
                            <code className="tix-checkin-code">{result.ticketId}</code>
                            <button
                              type="button"
                              onClick={() => handleCopy(result.ticketId!)}
                              className="tix-checkin-copy-btn"
                              title={t("checkIn.copy")}
                              aria-label={t("checkIn.copy")}
                            >
                              {copiedId === result.ticketId ? (
                                <Check size={14} color="#059669" />
                              ) : (
                                <Copy size={14} />
                              )}
                            </button>
                          </div>
                        </div>
                      )}

                      <div>
                        <span className="tix-checkin-meta-label">{t("checkIn.attemptId")}</span>
                        <div className="tix-checkin-meta-val-row">
                          <code className="tix-checkin-code">{result.attemptId}</code>
                          <button
                            type="button"
                            onClick={() => handleCopy(result.attemptId)}
                            className="tix-checkin-copy-btn"
                            title={t("checkIn.copy")}
                            aria-label={t("checkIn.copy")}
                          >
                            {copiedId === result.attemptId ? (
                              <Check size={14} color="#059669" />
                            ) : (
                              <Copy size={14} />
                            )}
                          </button>
                        </div>
                      </div>

                      <div>
                        <span className="tix-checkin-meta-label">{t("checkIn.processedAt")}</span>
                        <div className="tix-checkin-meta-time">
                          {formatDateTime(result.processedAt, language)}
                        </div>
                      </div>
                    </div>
                  </div>
                ) : (
                  <div className="tix-checkin-empty">
                    <ShieldAlert size={30} />
                    <p>{t("checkIn.emptySession")}</p>
                  </div>
                )}
              </section>

              {/* Session history panel */}
              <section className="tix-chart-card">
                <div className="tix-card-header">
                  <h2 className="tix-card-title">
                    {t("checkIn.currentHistory")}
                    {history.length > 0 && (
                      <span className="tix-checkin-count-pill">{history.length}</span>
                    )}
                  </h2>
                  {history.length > 0 && (
                    <button
                      type="button"
                      onClick={() => setHistory([])}
                      className="tix-checkin-clear-btn"
                    >
                      <Trash2 size={13} />
                      <span>{t("checkIn.clearHistory")}</span>
                    </button>
                  )}
                </div>

                {history.length === 0 ? (
                  <div className="tix-checkin-empty is-compact">
                    <QrCode size={24} />
                    <p>{t("checkIn.emptyHistory")}</p>
                  </div>
                ) : (
                  <div className="tix-checkin-history-list">
                    {history.map((item) => (
                      <article
                        key={item.attemptId}
                        className={`tix-checkin-history-item ${
                          item.succeeded ? "is-valid" : "is-denied"
                        }`}
                      >
                        <div>
                          <div className="tix-checkin-history-top">
                            <span
                              className={`tix-checkin-history-badge ${
                                item.succeeded ? "is-valid" : "is-denied"
                              }`}
                            >
                              {item.result}
                            </span>
                            {item.ticketId && (
                              <span className="tix-checkin-history-ticket">
                                Vé: {item.ticketId.slice(0, 8)}...
                              </span>
                            )}
                          </div>
                          <div className="tix-checkin-history-msg">
                            {t(`checkIn.results.${item.result}`, { defaultValue: item.result })}
                          </div>
                        </div>

                        <div className="tix-checkin-history-time">
                          {formatDateTime(item.processedAt, language)}
                        </div>
                      </article>
                    ))}
                  </div>
                )}
              </section>
            </div>
          </section>
        </div>
      </div>
    </OrganizerLayout>
  );
}
