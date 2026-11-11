import { FormEvent, useEffect, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { toast } from "react-toastify";
import {
  AlertTriangle,
  CheckCircle2,
  Copy,
  Check,
  LoaderCircle,
  QrCode,
  ShieldAlert,
  Trash2,
  XCircle,
  Calendar,
} from "lucide-react";
import OrganizerLayout from "../../components/organizer/OrganizerLayout";
import { useOrganizerGate } from "./useOrganizerGate";
import {
  organizerDashboardService,
  OrganizerSummary,
} from "../../services/organizerDashboardService";
import { ticketService, CheckInResult } from "../../services/ticketService";

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

export default function OrganizerCheckInPage() {
  const { i18n, t } = useTranslation();
  const language = i18n.resolvedLanguage || i18n.language;
  const gate = useOrganizerGate();
  const [params, setParams] = useSearchParams();
  const urlEventId = params.get("eventId") || "";

  const [events, setEvents] = useState<OrganizerSummary[]>([]);
  const [loadingEvents, setLoadingEvents] = useState(false);
  const [selectedEventId, setSelectedEventId] = useState(urlEventId);

  const [qrToken, setQrToken] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<CheckInResult | null>(null);
  const [history, setHistory] = useState<CheckInResult[]>([]);
  const [copiedId, setCopiedId] = useState<string | null>(null);

  const inputRef = useRef<HTMLInputElement>(null);

  // Load organizer events
  useEffect(() => {
    if (!gate.ready) return;
    const controller = new AbortController();
    setLoadingEvents(true);
    organizerDashboardService
      .events(null, controller.signal)
      .then((res) => {
        if (!controller.signal.aborted) {
          setEvents(res.items || []);
          if (!selectedEventId && res.items && res.items.length > 0) {
            // Default select first published event or first event
            const published = res.items.find((e) => e.status === "PUBLISHED");
            const firstId = published ? published.eventId : res.items[0].eventId;
            setSelectedEventId(firstId);
            setParams({ eventId: firstId });
          }
        }
      })
      .catch((err) => {
        if (!controller.signal.aborted) {
          console.error("Failed to load organizer events", err);
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoadingEvents(false);
      });

    return () => controller.abort();
  }, [gate.ready]);

  // Sync url param when event changes
  const handleSelectEvent = (eventId: string) => {
    setSelectedEventId(eventId);
    if (eventId) {
      setParams({ eventId });
    } else {
      setParams({});
    }
    inputRef.current?.focus();
  };

  const handleCopy = (text: string) => {
    navigator.clipboard.writeText(text);
    setCopiedId(text);
    setTimeout(() => setCopiedId(null), 2000);
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

    setSubmitting(true);
    try {
      const response = await ticketService.checkIn(
        selectedEventId,
        trimmedToken,
      );

      setResult(response);
      setHistory((current) => [response, ...current].slice(0, 20));
      setQrToken("");
      playBeep(response.succeeded);

      if (response.succeeded) {
        toast.success(t("checkIn.results.SUCCESS"));
      } else {
        const resultMsg =
          t(`checkIn.results.${response.result}`) || response.result;
        toast.error(resultMsg);
      }
    } catch (error: any) {
      playBeep(false);
      const code =
        error?.response?.data?.code ||
        error?.response?.data?.message ||
        "CHECK_IN_FAILED";
      const errorMsg =
        t(`checkIn.results.${code}`) || error?.response?.data?.message || t("checkIn.results.UNKNOWN_QR");
      toast.error(String(errorMsg));
    } finally {
      setSubmitting(false);
      setTimeout(() => {
        inputRef.current?.focus();
      }, 50);
    }
  };

  const selectedEvent = events.find((e) => e.eventId === selectedEventId);

  return (
    <OrganizerLayout
      title={t("checkIn.title")}
      description={t("checkIn.description")}
      className="organizer-check-in-page"
    >
      <section className="organizer-grid organizer-grid-checkin" style={{ gap: "24px" }}>
        {/* Form scanning panel */}
        <div className="organizer-panel organizer-checkin-form">
          <div className="organizer-panel-heading">
            <h2 style={{ display: "flex", alignItems: "center", gap: "8px" }}>
              <QrCode size={20} />
              {t("checkIn.title")}
            </h2>
          </div>

          {/* Event selector */}
          <label className="organizer-field">
            <span style={{ fontWeight: 600 }}>{t("checkIn.selectEvent")} *</span>
            <select
              value={selectedEventId}
              onChange={(e) => handleSelectEvent(e.target.value)}
              className="organizer-input"
              disabled={loadingEvents}
              style={{ cursor: "pointer", background: "#1f2937", color: "#fff" }}
            >
              <option value="">{t("checkIn.selectEventPrompt")}</option>
              {events.map((evt) => (
                <option key={evt.eventId} value={evt.eventId}>
                  {evt.title} ({evt.status})
                </option>
              ))}
            </select>
          </label>

          {selectedEvent && (
            <div
              style={{
                display: "flex",
                alignItems: "center",
                gap: "12px",
                padding: "10px 14px",
                background: "rgba(255, 255, 255, 0.04)",
                borderRadius: "10px",
                fontSize: "13px",
              }}
            >
              <Calendar size={16} className="text-gray-400" />
              <span>
                <strong>{selectedEvent.title}</strong> ·{" "}
                <span
                  style={{
                    color:
                      selectedEvent.status === "PUBLISHED"
                        ? "#10b981"
                        : selectedEvent.status === "CANCELLED"
                        ? "#ef4444"
                        : "#f59e0b",
                  }}
                >
                  {selectedEvent.status}
                </span>{" "}
                · {formatDateTime(selectedEvent.startsAt, language)}
              </span>
            </div>
          )}

          {/* QR code token input */}
          <form onSubmit={handleSubmit} style={{ display: "flex", flexDirection: "column", gap: "16px" }}>
            <label className="organizer-field">
              <span style={{ fontWeight: 600 }}>{t("checkIn.qrData")} *</span>
              <input
                ref={inputRef}
                type="text"
                value={qrToken}
                onChange={(e) => setQrToken(e.target.value)}
                className="organizer-input"
                placeholder={t("checkIn.qrPlaceholder")}
                disabled={submitting || !selectedEventId}
                autoFocus
                autoComplete="off"
                style={{ fontFamily: "monospace", fontSize: "14px" }}
              />
              <span style={{ fontSize: "12px", color: "#9ca3af" }}>
                Hỗ trợ súng bắn mã vạch/QR tự động hoặc dán trực tiếp mã token.
              </span>
            </label>

            <button
              type="submit"
              className="btn btn-primary"
              disabled={submitting || !selectedEventId || !qrToken.trim()}
              style={{
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                gap: "8px",
                padding: "12px",
                fontWeight: 600,
              }}
            >
              {submitting ? (
                <LoaderCircle size={18} className="spin" />
              ) : (
                <QrCode size={18} />
              )}
              {t("checkIn.confirm")}
            </button>
          </form>
        </div>

        {/* Results & History Stack */}
        <div className="organizer-stack" style={{ display: "flex", flexDirection: "column", gap: "24px" }}>
          {/* Latest Scan Result */}
          <section className="organizer-panel organizer-result-panel">
            <div className="organizer-panel-heading">
              <h2>{t("checkIn.latestResult")}</h2>
            </div>

            {result ? (
              <div
                style={{
                  display: "flex",
                  flexDirection: "column",
                  gap: "16px",
                  padding: "20px",
                  borderRadius: "16px",
                  background: result.succeeded
                    ? "rgba(16, 185, 129, 0.12)"
                    : "rgba(239, 68, 68, 0.12)",
                  border: `1px solid ${
                    result.succeeded ? "rgba(16, 185, 129, 0.4)" : "rgba(239, 68, 68, 0.4)"
                  }`,
                }}
              >
                {/* Result header banner */}
                <div style={{ display: "flex", alignItems: "center", gap: "12px" }}>
                  {result.succeeded ? (
                    <CheckCircle2 size={36} color="#10b981" />
                  ) : result.result === "EVENT_CANCELLED" || result.result === "VOID" ? (
                    <XCircle size={36} color="#ef4444" />
                  ) : (
                    <AlertTriangle size={36} color="#f59e0b" />
                  )}
                  <div>
                    <h3
                      style={{
                        margin: 0,
                        fontSize: "18px",
                        fontWeight: 700,
                        color: result.succeeded ? "#10b981" : "#ef4444",
                      }}
                    >
                      {result.succeeded
                        ? t("checkIn.validAdmission")
                        : t("checkIn.deniedAdmission")}
                    </h3>
                    <p style={{ margin: "4px 0 0 0", fontSize: "14px", color: "#e2e8f0" }}>
                      <strong>{t("checkIn.status")}: </strong>
                      {t(`checkIn.results.${result.result}`) || result.result}
                    </p>
                  </div>
                </div>

                {/* Result metadata grid */}
                <div
                  style={{
                    display: "grid",
                    gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))",
                    gap: "12px",
                    background: "rgba(0, 0, 0, 0.25)",
                    padding: "14px",
                    borderRadius: "10px",
                    fontSize: "13px",
                  }}
                >
                  {result.ticketId && (
                    <div>
                      <span style={{ color: "#94a3b8" }}>{t("checkIn.ticketId")}:</span>
                      <div style={{ display: "flex", alignItems: "center", gap: "6px", marginTop: "2px" }}>
                        <code style={{ fontSize: "12px", color: "#38bdf8" }}>{result.ticketId}</code>
                        <button
                          type="button"
                          onClick={() => handleCopy(result.ticketId!)}
                          style={{ background: "none", border: "none", cursor: "pointer", color: "#94a3b8" }}
                          title={t("checkIn.copy")}
                        >
                          {copiedId === result.ticketId ? <Check size={14} color="#10b981" /> : <Copy size={14} />}
                        </button>
                      </div>
                    </div>
                  )}

                  <div>
                    <span style={{ color: "#94a3b8" }}>{t("checkIn.attemptId")}:</span>
                    <div style={{ display: "flex", alignItems: "center", gap: "6px", marginTop: "2px" }}>
                      <code style={{ fontSize: "12px", color: "#cbd5e1" }}>{result.attemptId}</code>
                      <button
                        type="button"
                        onClick={() => handleCopy(result.attemptId)}
                        style={{ background: "none", border: "none", cursor: "pointer", color: "#94a3b8" }}
                        title={t("checkIn.copy")}
                      >
                        {copiedId === result.attemptId ? <Check size={14} color="#10b981" /> : <Copy size={14} />}
                      </button>
                    </div>
                  </div>

                  <div>
                    <span style={{ color: "#94a3b8" }}>{t("checkIn.processedAt")}:</span>
                    <div style={{ marginTop: "2px", fontWeight: 500 }}>
                      {formatDateTime(result.processedAt, language)}
                    </div>
                  </div>
                </div>
              </div>
            ) : (
              <div className="organizer-empty-state">
                <ShieldAlert size={32} className="text-gray-400" />
                <p>{t("checkIn.emptySession")}</p>
              </div>
            )}
          </section>

          {/* Session history panel */}
          <section className="organizer-panel">
            <div
              className="organizer-panel-heading"
              style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}
            >
              <h2>
                {t("checkIn.currentHistory")}{" "}
                {history.length > 0 && (
                  <span style={{ fontSize: "14px", color: "#94a3b8", fontWeight: 400 }}>
                    ({history.length})
                  </span>
                )}
              </h2>
              {history.length > 0 && (
                <button
                  type="button"
                  onClick={() => setHistory([])}
                  className="btn btn-outline"
                  style={{
                    display: "flex",
                    alignItems: "center",
                    gap: "6px",
                    padding: "4px 10px",
                    fontSize: "12px",
                  }}
                >
                  <Trash2 size={13} />
                  {t("checkIn.clearHistory")}
                </button>
              )}
            </div>

            {history.length === 0 ? (
              <div className="organizer-empty-state compact">
                <QrCode size={24} />
                <p>{t("checkIn.emptyHistory")}</p>
              </div>
            ) : (
              <div
                className="organizer-history-list"
                style={{ display: "flex", flexDirection: "column", gap: "10px", maxHeight: "360px", overflowY: "auto" }}
              >
                {history.map((item) => (
                  <article
                    key={item.attemptId}
                    className="organizer-history-card"
                    style={{
                      display: "flex",
                      justifyContent: "space-between",
                      alignItems: "center",
                      padding: "12px 16px",
                      borderRadius: "10px",
                      background: "rgba(255, 255, 255, 0.03)",
                      borderLeft: `4px solid ${item.succeeded ? "#10b981" : "#ef4444"}`,
                    }}
                  >
                    <div>
                      <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                        <span
                          style={{
                            padding: "2px 8px",
                            borderRadius: "4px",
                            fontSize: "11px",
                            fontWeight: 700,
                            background: item.succeeded ? "rgba(16, 185, 129, 0.2)" : "rgba(239, 68, 68, 0.2)",
                            color: item.succeeded ? "#10b981" : "#ef4444",
                          }}
                        >
                          {item.result}
                        </span>
                        {item.ticketId && (
                          <span style={{ fontSize: "12px", color: "#94a3b8", fontFamily: "monospace" }}>
                            Vé: {item.ticketId.slice(0, 8)}...
                          </span>
                        )}
                      </div>
                      <div style={{ fontSize: "12px", color: "#cbd5e1", marginTop: "4px" }}>
                        {t(`checkIn.results.${item.result}`) || item.result}
                      </div>
                    </div>

                    <div style={{ fontSize: "12px", color: "#94a3b8", textAlign: "right" }}>
                      {formatDateTime(item.processedAt, language)}
                    </div>
                  </article>
                ))}
              </div>
            )}
          </section>
        </div>
      </section>
    </OrganizerLayout>
  );
}
