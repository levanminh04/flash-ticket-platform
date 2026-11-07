import { useEffect, useState, useCallback, useMemo, useRef } from "react";
import { useParams, useNavigate, Link } from "react-router-dom";
import { useKeycloak } from "@react-keycloak/web";
import { toast } from "react-toastify";
import { useTranslation } from "react-i18next";
import { FaCalendarCheck, FaMapLocationDot } from "react-icons/fa6";
import { eventService } from "../../services/eventService";
import { bookingService } from "../../services/bookingService";
import { hasRealmRole } from "../../lib/auth";
import { Availability, OrderSelection } from "../../types/bookingContract";
import {
  EventSummary,
  PublicSeat,
  PublicSeatMap,
  PublicSeatSector,
  TicketInventoryMode,
  TicketType,
} from "../../types/api";
import BuyerSeatMapCanvas from "../../components/seat-map/runtime/BuyerSeatMapCanvas";
import {
  ArrowLeft,
  ChevronRight,
  LoaderCircle,
  ZoomIn,
  ZoomOut,
  RotateCcw,
  X,
  Info,
} from "lucide-react";


interface TicketSelection {
  [ticketTypeId: string]: number;
}

interface SelectedSeat {
  seatId: string;
  ticketTypeId: string;
  sectorId: string;
  seatLabel: string;
}

function getInventoryMode(ticketType: TicketType): TicketInventoryMode {
  if (ticketType.inventoryMode === "ASSIGNED_SEAT" || ticketType.inventoryMode === "QUANTITY") {
    return ticketType.inventoryMode;
  }

  return ticketType.seatSelectionEnabled ? "ASSIGNED_SEAT" : "QUANTITY";
}

function getTicketTypeSectorId(ticketType: TicketType) {
  return ticketType.eventSectorId || ticketType.sectorId || "";
}

function getSectorTicketTypeId(sector: PublicSeatSector) {
  const mapData = sector.mapData ?? {};
  return typeof mapData.ticketTypeId === "string" ? mapData.ticketTypeId : "";
}

function getSeatTicketTypeId(sector: PublicSeatSector, seat: PublicSeat) {
  return seat.ticketTypeId || getSectorTicketTypeId(sector);
}

function getSectorType(sector?: PublicSeatSector | null) {
  return String(sector?.sectorType || sector?.mapData?.sectorType || "").toUpperCase();
}

export default function SelectTicketPage() {
  const { i18n, t } = useTranslation();
  const { slug } = useParams<{ slug: string }>();
  const navigate = useNavigate();
  const { keycloak } = useKeycloak();
  const language = i18n.resolvedLanguage || "vi";

  const [event, setEvent] = useState<EventSummary | null>(null);
  const [ticketTypes, setTicketTypes] = useState<TicketType[]>([]);
  const [seatMap, setSeatMap] = useState<PublicSeatMap | null>(null);
  const [availability, setAvailability] = useState<Availability | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [quantities, setQuantities] = useState<TicketSelection>({});
  const [selectedSeats, setSelectedSeats] = useState<SelectedSeat[]>([]);
  const [selectedStandingSectorId, setSelectedStandingSectorId] = useState<string | null>(null);
  const [standingPopupSectorId, setStandingPopupSectorId] = useState<string | null>(null);

  // Modal states
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [modalEmail, setModalEmail] = useState("");
  const [submitError, setSubmitError] = useState("");
  const [modalErrors, setModalErrors] = useState<Record<string, string>>({});
  const [isBookingInProgress, setIsBookingInProgress] = useState(false);
  const submittingRef = useRef(false);
  const recipientDialogRef = useRef<HTMLDialogElement>(null);
  const standingDialogRef = useRef<HTMLDialogElement>(null);
  const continueRef = useRef<HTMLButtonElement>(null);

  const [zoom, setZoom] = useState(0);
  const [fitZoom, setFitZoom] = useState(1);

  useEffect(() => {
    if (keycloak?.authenticated && keycloak?.tokenParsed) {
      const parsed = keycloak.tokenParsed as Record<string, any>;
      const email = parsed.email || "";
      if (email) setModalEmail(email);
    }
  }, [keycloak?.authenticated, keycloak?.tokenParsed]);

  const loadSelection = useCallback(async () => {
    if (!slug) return;
    setIsLoading(true);
    setError(null);
    try {
        const [data, currentAvailability] = await Promise.all([eventService.getEventDetails(slug), bookingService.getAvailability(slug)]);
        if (currentAvailability.eventId !== data.id) throw new Error("AVAILABILITY_SCOPE_MISMATCH");
        setAvailability(currentAvailability);
        setEvent(data);
        const types: TicketType[] = (data.ticketTypes || []).map(type => ({
          ...type,
          quantityAvailable: Math.min(Number(currentAvailability.ticketTypes.find(item => item.ticketTypeId === type.id)?.available ?? 0), Number.MAX_SAFE_INTEGER),
        }));
        const statesBySeat = new Map(currentAvailability.seats.map(seat => [seat.seatId, seat.state]));
        const nextSeatMap = data.seatMap ? {
          ...data.seatMap,
          sectors: data.seatMap.sectors?.map(sector => ({ ...sector, seatsData: sector.seatsData?.map(seat => ({ ...seat, inventoryStatus: statesBySeat.get(seat.id) ?? "UNAVAILABLE" })) })),
        } : null;
        setTicketTypes(types);
        setSeatMap(nextSeatMap);

        let nextQuantities: TicketSelection = Object.fromEntries(types.map(type => [type.id, 0]));
        let nextSeats: SelectedSeat[] = [];
        let nextStanding: string | null = null;
        if (sessionStorage.getItem("transient_slug") === slug) {
          try {
            const previous: TicketSelection = JSON.parse(sessionStorage.getItem("transient_quantities") || "{}");
            const previousSeats: SelectedSeat[] = JSON.parse(sessionStorage.getItem("transient_selectedSeats") || "[]");
            if (data.mode === "QUANTITY") {
              nextQuantities = Object.fromEntries(types.map(type => [type.id, Number.isSafeInteger(previous[type.id]) && previous[type.id] > 0 ? Math.min(previous[type.id], type.quantityAvailable) : 0]));
            } else if (previousSeats.length) {
              for (const sector of nextSeatMap?.sectors || []) {
                if (sector.sectorType !== "SEATED") continue;
                for (const seat of sector.seatsData || []) {
                  const type = types.find(type => type.id === seat.ticketTypeId);
                  if (!type || !previousSeats.some(previous => previous.seatId === seat.id) || seat.inventoryStatus !== "AVAILABLE" || (nextSeats.length && nextSeats[0].sectorId !== sector.id) || nextQuantities[type.id] >= type.quantityAvailable) continue;
                  nextSeats.push({ seatId: seat.id, ticketTypeId: type.id, sectorId: sector.id, seatLabel: seat.seatLabel || `${seat.rowName || ""}${seat.seatNumber || ""}` });
                  nextQuantities[type.id] += 1;
                }
              }
            } else {
              const savedStanding = sessionStorage.getItem("transient_selectedStandingSectorId");
              if (nextSeatMap?.sectors?.some(sector => sector.id === savedStanding && sector.sectorType === "STANDING")) {
                nextStanding = savedStanding;
                let available = Number(currentAvailability.sectors.find(sector => sector.sectorId === savedStanding)?.available || 0);
                for (const type of types.filter(type => getTicketTypeSectorId(type) === savedStanding)) {
                  const quantity = Number.isSafeInteger(previous[type.id]) && previous[type.id] > 0 ? Math.min(previous[type.id], type.quantityAvailable, available) : 0;
                  nextQuantities[type.id] = quantity;
                  available -= quantity;
                }
              }
            }
          } catch {
            // Ignore an incomplete browser draft; never submit it as a new order.
            nextQuantities = Object.fromEntries(types.map(type => [type.id, 0]));
            nextSeats = []; nextStanding = null;
          } finally {
            for (const key of ["transient_slug", "transient_quantities", "transient_selectedSeats", "transient_selectedStandingSectorId"]) sessionStorage.removeItem(key);
          }
        }
        setQuantities(nextQuantities);
        setSelectedSeats(nextSeats);
        setSelectedStandingSectorId(nextStanding);
        setError(null);
    } catch {
        setError("selectTicket.ticketLoadFailed");
    } finally {
      setIsLoading(false);
    }
  }, [slug]);

  useEffect(() => { void loadSelection(); }, [loadSelection]);

  useEffect(() => {
    if (!isModalOpen) return;
    const dialog = recipientDialogRef.current;
    dialog?.showModal();
    return () => { dialog?.close(); continueRef.current?.focus(); };
  }, [isModalOpen]);

  useEffect(() => {
    if (!standingPopupSectorId) return;
    const dialog = standingDialogRef.current;
    dialog?.showModal();
    return () => dialog?.close();
  }, [standingPopupSectorId]);

  const totalSelected = Object.values(quantities).reduce((a, b) => a + b, 0);
  const minPerOrder = 1;
  const maxPerOrder = Number.MAX_SAFE_INTEGER;

  const ticketTypeBySectorId = useMemo(() => {
    const mapping = new Map<string, TicketType>();
    ticketTypes.forEach((ticketType) => {
      const sectorId = getTicketTypeSectorId(ticketType);
      if (sectorId) {
        mapping.set(sectorId, ticketType);
      }
    });
    return mapping;
  }, [ticketTypes]);

  const ticketTypeById = useMemo(() => {
    const mapping = new Map<string, TicketType>();
    ticketTypes.forEach((ticketType) => {
      mapping.set(ticketType.id, ticketType);
    });
    return mapping;
  }, [ticketTypes]);

  const sectorById = useMemo(() => {
    const mapping = new Map<string, PublicSeatSector>();
    (seatMap?.sectors ?? []).forEach((sector) => {
      mapping.set(sector.id, sector);
    });
    return mapping;
  }, [seatMap]);

  const quantityTicketTypes = useMemo(
    () =>
      ticketTypes
        .filter((ticketType) => ticketType.isVisible !== false && getInventoryMode(ticketType) === "QUANTITY")
        .sort((a, b) => a.displayOrder - b.displayOrder),
    [ticketTypes],
  );

  const assignedSeatTicketTypes = useMemo(
    () =>
      ticketTypes
        .filter((ticketType) => ticketType.isVisible !== false && getInventoryMode(ticketType) === "ASSIGNED_SEAT")
        .sort((a, b) => a.displayOrder - b.displayOrder),
    [ticketTypes],
  );

  const hasAssignedSeatTickets = assignedSeatTicketTypes.length > 0;

  const standingSectorTicketTypes = useMemo(
    () =>
      ticketTypes
        .filter((ticketType) => ticketType.isVisible !== false)
        .filter((ticketType) => {
        const sectorId = getTicketTypeSectorId(ticketType);
        return sectorId && getSectorType(sectorById.get(sectorId)) === "STANDING";
      })
        .sort((a, b) => a.displayOrder - b.displayOrder),
    [ticketTypes, sectorById],
  );

  const orderQuantityTicketTypes = useMemo(() => {
    const mapping = new Map<string, TicketType>();
    [...quantityTicketTypes, ...standingSectorTicketTypes].forEach((ticketType) => {
      mapping.set(ticketType.id, ticketType);
    });
    return [...mapping.values()];
  }, [quantityTicketTypes, standingSectorTicketTypes]);

  const hasStandingSectorTickets = standingSectorTicketTypes.length > 0;
  const hasInteractiveSeatMap = Boolean(seatMap && (hasAssignedSeatTickets || hasStandingSectorTickets));

  const visibleTicketTypes = useMemo(
    () =>
      ticketTypes
        .filter((ticketType) => ticketType.isVisible !== false)
        .sort((a, b) => a.displayOrder - b.displayOrder),
    [ticketTypes],
  );

  const standingPopupSector = standingPopupSectorId
    ? sectorById.get(standingPopupSectorId)
    : null;
  const standingPopupTicketTypes = standingPopupSector
    ? standingSectorTicketTypes.filter(
        (ticketType) => getTicketTypeSectorId(ticketType) === standingPopupSector.id,
      )
    : [];
  const standingPopupTotal = standingPopupTicketTypes.reduce(
    (sum, ticketType) => sum + BigInt(quantities[ticketType.id] || 0) * BigInt(ticketType.unitPrice || String(ticketType.price)),
    0n,
  );

  const availableSeatCount = useMemo(
    () =>
      (seatMap?.sectors ?? []).reduce(
        (total, sector) =>
          total +
          (sector.seatsData ?? []).filter(
            (seat) => seat.isActive !== false && seat.inventoryStatus === "AVAILABLE",
          ).length,
        0,
      ),
    [seatMap],
  );

  const updateQuantity = useCallback(
    (ticketType: TicketType, delta: number) => {
      const sectorId = getTicketTypeSectorId(ticketType);
      if (
        sectorId &&
        getSectorType(sectorById.get(sectorId)) === "STANDING" &&
        sectorId !== selectedStandingSectorId
      ) {
        toast.warning(t("selectTicket.selectStandingSectorFirst"), {
          toastId: "select-standing-sector",
        });
        return;
      }

      const currentQuantity = quantities[ticketType.id] || 0;
      const nextQuantity = Math.max(0, currentQuantity + delta);
      const nextTotal = totalSelected - currentQuantity + nextQuantity;

      if (nextTotal > maxPerOrder) {
        toast.warning(t("selectTicket.maxPerOrder", { count: maxPerOrder }), {
          toastId: "max-ticket-limit",
        });
        return;
      }

      if (nextQuantity > ticketType.maxPerOrder) {
        toast.warning(t("selectTicket.ticketTypeMax", { count: ticketType.maxPerOrder }), {
          toastId: `ticket-max-${ticketType.id}`,
        });
        return;
      }

      if (nextQuantity > (ticketType.quantityAvailable || 0)) {
        toast.warning(t("selectTicket.ticketTypeAvailable"), {
          toastId: `ticket-available-${ticketType.id}`,
        });
        return;
      }

      if (delta > 0 && sectorId && getSectorType(sectorById.get(sectorId)) === "STANDING") {
        const selectedInSector = ticketTypes.filter(type => getTicketTypeSectorId(type) === sectorId).reduce((sum, type) => sum + (quantities[type.id] || 0), 0);
        const remaining = Number(availability?.sectors.find(sector => sector.sectorId === sectorId && sector.kind === "STANDING")?.available ?? 0);
        if (selectedInSector + delta > remaining) {
          toast.warning(t("selectTicket.ticketTypeAvailable"));
          return;
        }
      }

      setQuantities((prev) => ({
        ...prev,
        [ticketType.id]: nextQuantity,
      }));
    },
    [maxPerOrder, quantities, selectedStandingSectorId, sectorById, t, totalSelected, ticketTypes, availability],
  );

  const handleSectorClick = useCallback(
    (sector: PublicSeatSector) => {
      if (getSectorType(sector) !== "STANDING") {
        return;
      }

      const activeSectorId = selectedSeats[0]?.sectorId || selectedStandingSectorId;
      if (activeSectorId && activeSectorId !== sector.id) {
        toast.warning(t("selectTicket.oneSectorOnly"), {
          toastId: "single-sector-limit",
        });
        return;
      }

      const hasTicketType = standingSectorTicketTypes.some(
        (ticketType) => getTicketTypeSectorId(ticketType) === sector.id,
      );
      if (!hasTicketType) {
        toast.warning(t("selectTicket.noTicketInStandingSector"), {
          toastId: `standing-sector-${sector.id}`,
        });
        return;
      }

      setSelectedStandingSectorId(sector.id);
      setStandingPopupSectorId(sector.id);
    },
    [selectedSeats, selectedStandingSectorId, standingSectorTicketTypes, t],
  );

  const clearStandingSectorSelection = useCallback(() => {
    if (selectedStandingSectorId) {
      const ticketTypeIds = standingSectorTicketTypes
        .filter((ticketType) => getTicketTypeSectorId(ticketType) === selectedStandingSectorId)
        .map((ticketType) => ticketType.id);

      setQuantities((prev) => {
        const next = { ...prev };
        ticketTypeIds.forEach((ticketTypeId) => {
          delete next[ticketTypeId];
        });
        return next;
      });
    }

    setSelectedStandingSectorId(null);
    setStandingPopupSectorId(null);
  }, [selectedStandingSectorId, standingSectorTicketTypes]);

  const handleSeatClick = useCallback(
    (sector: PublicSeatSector, seat: PublicSeat) => {
      if (seat.isActive === false || seat.inventoryStatus !== "AVAILABLE") {
        return;
      }

      const ticketType =
        ticketTypeById.get(getSeatTicketTypeId(sector, seat)) ||
        ticketTypeBySectorId.get(sector.id) ||
        ticketTypeById.get(getSectorTicketTypeId(sector));
      if (!ticketType) {
        toast.warning(t("selectTicket.unmappedSector"));
        return;
      }

      if (getInventoryMode(ticketType) !== "ASSIGNED_SEAT") {
        return;
      }

      const activeSectorId = selectedSeats[0]?.sectorId || selectedStandingSectorId;
      if (activeSectorId && activeSectorId !== sector.id) {
        toast.warning(t("selectTicket.oneSectorOnly"), {
          toastId: "single-sector-limit",
        });
        return;
      }

      const already = selectedSeats.find((selectedSeat) => selectedSeat.seatId === seat.id);
      if (already) {
        setSelectedSeats((prev) => prev.filter((selectedSeat) => selectedSeat.seatId !== seat.id));
        setQuantities((prev) => ({
          ...prev,
          [ticketType.id]: Math.max(0, (prev[ticketType.id] || 0) - 1),
        }));
        return;
      }

      if (totalSelected >= maxPerOrder) {
        toast.warning(t("selectTicket.maxPerOrder", { count: maxPerOrder }), {
          toastId: "max-ticket-limit",
        });
        return;
      }

      if ((quantities[ticketType.id] || 0) >= (ticketType.quantityAvailable || 0)) {
        toast.warning(t("selectTicket.ticketTypeAvailable"), {
          toastId: `ticket-available-${ticketType.id}`,
        });
        return;
      }

      const displayLabel = sector.mapData?.code && seat.seatLabel
        ? `${String(sector.mapData.code)}-${seat.seatLabel}`
        : seat.seatLabel || `${seat.rowName || ""}${seat.seatNumber || ""}`;

      setSelectedSeats((prev) => [
        ...prev,
        {
          seatId: seat.id,
          ticketTypeId: ticketType.id,
          sectorId: sector.id,
          seatLabel: displayLabel,
        },
      ]);
      setQuantities((prev) => ({
        ...prev,
        [ticketType.id]: (prev[ticketType.id] || 0) + 1,
      }));
    },
    [maxPerOrder, quantities, selectedSeats, selectedStandingSectorId, t, ticketTypeById, ticketTypeBySectorId, totalSelected],
  );

  const handleRemoveSeat = useCallback(
    (seatId: string) => {
      const seat = selectedSeats.find((selectedSeat) => selectedSeat.seatId === seatId);
      if (!seat) {
        return;
      }

      setSelectedSeats((prev) => prev.filter((selectedSeat) => selectedSeat.seatId !== seatId));
      setQuantities((prev) => ({
        ...prev,
        [seat.ticketTypeId]: Math.max(0, (prev[seat.ticketTypeId] || 0) - 1),
      }));
    },
    [selectedSeats],
  );

  const handleModalSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (submittingRef.current || !event) return;
    if (!modalEmail.trim() || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(modalEmail.trim())) {
      setModalErrors({ email: t("selectTicket.formEmailInvalid") });
      return;
    }
    submittingRef.current = true;
    setIsBookingInProgress(true);
    setSubmitError("");
    setModalErrors({});
    try {
      const items = orderQuantityTicketTypes.map(type => ({ ticketTypeId: type.id, quantity: quantities[type.id] || 0 })).filter(item => item.quantity > 0);
      let selection: OrderSelection;
      if (event.mode === "QUANTITY") selection = { mode: "QUANTITY", items };
      else if (selectedSeats.length) selection = { mode: "SEAT_MAP", kind: "SEATED", sectorId: selectedSeats[0].sectorId, seatIds: selectedSeats.map(seat => seat.seatId) };
      else if (selectedStandingSectorId) selection = { mode: "SEAT_MAP", kind: "STANDING", sectorId: selectedStandingSectorId, items };
      else throw new Error(t("selectTicket.emptySelection"));
      const order = await bookingService.createOrder({ eventId: event.id, customerEmail: modalEmail, selection });
      sessionStorage.setItem("lastOrderId", order.orderId);
      toast.success(t("selectTicket.bookingSuccess"));
      setIsModalOpen(false);
      navigate(`/checkout?orderId=${order.orderId}`);
    } catch (error: unknown) {
      const problem = error as { response?: { data?: { code?: string; message?: string } } };
      setSubmitError(problem.response?.data?.message || t("selectTicket.retrySameOrder"));
      if (["SEAT_UNAVAILABLE", "INSUFFICIENT_QUANTITY"].includes(problem.response?.data?.code || "")) {
        setIsModalOpen(false);
        void loadSelection();
      }
    } finally {
      submittingRef.current = false;
      setIsBookingInProgress(false);
    }
  };

  const resetView = () => {
    setZoom(0);
  };

  const handleContinue = () => {
    if (totalSelected < minPerOrder) {
      toast.warning(t("selectTicket.minRequired", { count: minPerOrder }));
      return;
    }

    if (!keycloak?.authenticated) {
      // Save transient selection so we can restore it after redirect
      sessionStorage.setItem("transient_slug", slug || "");
      sessionStorage.setItem("transient_quantities", JSON.stringify(quantities));
      sessionStorage.setItem("transient_selectedSeats", JSON.stringify(selectedSeats));
      sessionStorage.setItem("transient_selectedStandingSectorId", selectedStandingSectorId || "");

      keycloak?.login({
        redirectUri: window.location.href,
      });
      return;
    }

    if (!hasRealmRole(keycloak.tokenParsed, "BUYER")) {
      toast.error(t("selectTicket.buyerRequired"));
      return;
    }
    setIsModalOpen(true);
  };

  const subtotal = ticketTypes.reduce(
    (sum, ticketType) => sum + BigInt(quantities[ticketType.id] || 0) * BigInt(ticketType.unitPrice || String(ticketType.price)),
    0n,
  );

  const startStr =
    (event as any)?.schedule?.startDatetime ||
    (event as any)?.startDatetime ||
    (event as any)?.startTime;
  const startDate = startStr ? new Date(startStr) : null;
  const venueName = (event as any)?.venue?.name || (event as any)?.venueName || "";
  const venueAddress = (event as any)?.venue?.address || (event as any)?.venueAddress || "";

  if (isLoading) {
    return (
      <div className="select-ticket-page">
        <div className="select-ticket-loading" role="status" aria-live="polite">
          <LoaderCircle className="select-ticket-loading-icon" size={24} />
          <span>{t("selectTicket.loading")}</span>
        </div>
      </div>
    );
  }

  if (error || !event) {
    return (
      <div className="select-ticket-page">
        <div className="booking-load-error">
          <p role="alert">{t(error || "selectTicket.eventNotFound")}</p>
          <button className="btn btn-primary" onClick={() => void loadSelection()}>{t("home.events.retry")}</button>
          <Link
            to="/"
            style={{ textDecoration: "underline", color: "var(--primary)" }}
          >
            {t("selectTicket.returnHome")}
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="select-ticket-page">
      <div className="select-ticket-layout">
        <div className="select-ticket-map-panel">
          <button
            type="button"
            className="select-ticket-back-button"
            onClick={() => navigate(-1)}
          >
            <ArrowLeft size={22} />
            <span>{t("selectTicket.back")}</span>
          </button>

          {hasInteractiveSeatMap ? (
            <div className="select-ticket-floating-zoom" aria-label={t("selectTicket.interactiveMapControls")}>
              <button type="button" className="zoom-btn" aria-label={t("selectTicket.zoomIn")} onClick={() => setZoom((z) => Math.min(3, (z || fitZoom) * 1.25))}>
                <ZoomIn size={18} />
              </button>
              <button type="button" className="zoom-btn" aria-label={t("selectTicket.resetMap")} onClick={resetView}>
                <RotateCcw size={18} />
              </button>
              <button type="button" className="zoom-btn" aria-label={t("selectTicket.zoomOut")} onClick={() => setZoom((z) => Math.max(0.1, (z || fitZoom) / 1.25))}>
                <ZoomOut size={18} />
              </button>
            </div>
          ) : null}

          {hasInteractiveSeatMap ? (
            <div className="select-ticket-map-heading">
              <h1>{t("selectTicket.chooseSector")}</h1>
              <p>{t("selectTicket.clickSector")}</p>
            </div>
          ) : null}

          <div className="seat-map-section">
            <div className={`seat-map-container ${hasInteractiveSeatMap ? "has-interactive-map" : ""}`}>
              {hasInteractiveSeatMap && seatMap ? (
                <>
                  <BuyerSeatMapCanvas
                    seatMap={seatMap}
                    zoom={zoom}
                    selectedSeatIds={selectedSeats.map((seat) => seat.seatId)}
                    selectedSectorId={selectedStandingSectorId}
                    getSeatColor={(sector, seat) => {
                      const ticketType =
                        ticketTypeById.get(getSeatTicketTypeId(sector, seat)) ||
                        ticketTypeBySectorId.get(sector.id);
                      return seat.colorCode || ticketType?.colorCode || sector.colorCode || "#16a34a";
                    }}
                    onZoomChange={setZoom}
                    onFitZoom={setFitZoom}
                    onSeatClick={handleSeatClick}
                    onSectorClick={handleSectorClick}
                  />
                  <div className="booking-sector-list">
                    {(seatMap.sectors || []).filter(sector => sector.sectorType === "STANDING").map(sector => (
                      <button key={sector.id} type="button" className="btn btn-outline" onClick={() => handleSectorClick(sector)}>{sector.name}</button>
                    ))}
                  </div>
                  {hasAssignedSeatTickets && <details className="booking-seat-list">
                    <summary>{t("selectTicket.seatList")}</summary>
                    {(seatMap.sectors || []).filter(sector => sector.sectorType === "SEATED").map(sector => <fieldset key={sector.id}>
                      <legend>{sector.name}</legend>
                      {sector.seatsData?.map(seat => <button key={seat.id} type="button" aria-label={`${sector.name} — ${seat.seatLabel}`} aria-pressed={selectedSeats.some(selected => selected.seatId === seat.id)} disabled={seat.isActive === false || seat.inventoryStatus !== "AVAILABLE"} onClick={() => handleSeatClick(sector, seat)}>{seat.seatLabel}</button>)}
                    </fieldset>)}
                  </details>}
                  <div className="seat-map-status-legend" aria-label={t("selectTicket.seatLegend")}>
                    <strong>{t("selectTicket.seatLegend")}</strong>
                    <div className="seat-map-status-legend-grid">
                      <span><i className="legend-seat-dot is-available" />{t("selectTicket.seatAvailable")}</span>
                      <span><i className="legend-seat-dot is-selected" />{t("selectTicket.seatSelected")}</span>
                      <span><i className="legend-seat-dot is-sold" />{t("selectTicket.seatSold")}</span>
                      <span><i className="legend-seat-dot is-locked" />{t("selectTicket.seatLocked")}</span>
                      <span><i className="legend-seat-dot is-unavailable" />{t("selectTicket.seatUnavailable")}</span>
                    </div>
                  </div>
                </>
              ) : !hasAssignedSeatTickets && !hasStandingSectorTickets ? (
                <div className="quantity-booking-panel">
                  <h2>{t("selectTicket.quantityBookingTitle")}</h2>
                  <p>{t("selectTicket.quantityBookingDescription")}</p>

                  <div className="quantity-ticket-list">
                    {quantityTicketTypes.length === 0 ? (
                      <div className="selected-empty">{t("selectTicket.noMatchingTicketType")}</div>
                    ) : (
                      quantityTicketTypes.map((ticketType) => {
                        const isSoldOut =
                          ticketType.status === "SOLD_OUT" ||
                          ticketType.quantityAvailable === 0;

                        return (
                          <div
                            className={`quantity-ticket-row ${isSoldOut ? "sold-out" : ""}`}
                            key={ticketType.id}
                          >
                            <div className="quantity-ticket-copy">
                              <strong>{ticketType.name}</strong>
                              <span>
                                {isSoldOut
                                  ? t("selectTicket.soldOut")
                                  : `${BigInt(ticketType.unitPrice || String(ticketType.price)).toLocaleString(language === "en" ? "en-US" : "vi-VN")} ₫`}
                              </span>
                            </div>

                            <div
                              className="quantity-selector quantity-selector-main"
                              aria-label={t("selectTicket.chooseQuantity", { name: ticketType.name })}
                            >
                              <button
                                type="button"
                                className="quantity-btn"
                                onClick={() => updateQuantity(ticketType, -1)}
                                disabled={(quantities[ticketType.id] || 0) <= 0}
                              >
                                -
                              </button>
                              <span className="quantity-value">{quantities[ticketType.id] || 0}</span>
                              <button
                                type="button"
                                className="quantity-btn"
                                onClick={() => updateQuantity(ticketType, 1)}
                                disabled={isSoldOut}
                              >
                                +
                              </button>
                            </div>
                          </div>
                        );
                      })
                    )}
                  </div>
                </div>
              ) : (
                <div className="buyer-seat-map-empty">
                  <div className="quantity-booking-notice">
                    <div className="notice-icon-wrapper">
                      <Info size={40} className="notice-icon" />
                    </div>
                    <h3>{t("selectTicket.noSeatMapTitle")}</h3>
                    <p>{t("selectTicket.noPublishedSeatMap")}</p>
                  </div>
                </div>
              )}
            </div>

          </div>
        </div>
          <div className="booking-sidebar">
            <div className="sidebar-event-card">
              <div className="sidebar-event-title-bar">
                <div className="event-title">{event.title}</div>
              </div>
              <div className="event-details">
                {startDate && (
                  <div className="event-meta-row">
                    <FaCalendarCheck size={14} />
                    <span>
                      {startDate.toLocaleDateString(language === "en" ? "en-US" : "vi-VN", {
                        weekday: "long",
                        day: "numeric",
                        month: "long",
                        year: "numeric",
                      })}
                      {" · "}
                      {startDate.toLocaleTimeString(language === "en" ? "en-US" : "vi-VN", {
                        hour: "2-digit",
                        minute: "2-digit",
                      })}
                    </span>
                  </div>
                )}

                {(venueName || venueAddress) && (
                  <div className="event-meta-row">
                    <FaMapLocationDot size={14} />
                    <span>
                      {venueName}
                      {venueAddress ? `, ${venueAddress}` : ""}
                    </span>
                  </div>
                )}
              </div>
            </div>

            <div className="sidebar-tiers">
              <h3>{t("selectTicket.ticketPrice")}</h3>
              {visibleTicketTypes.length === 0 ? (
                <div className="selected-empty">{t("selectTicket.noMatchingTicketType")}</div>
              ) : (
                visibleTicketTypes.map((ticketType) => {
                  const isSoldOut = ticketType.status === "SOLD_OUT" || ticketType.quantityAvailable === 0;

                  return (
                    <div
                      key={ticketType.id}
                      className={`tier-item ${isSoldOut ? "sold-out" : ""}`}
                    >
                      <div className="tier-left">
                        <span
                          className="tier-dot"
                          style={{ backgroundColor: ticketType.colorCode || "var(--text-muted)" }}
                        />
                        <span className="tier-copy">
                          <span className="tier-name">{ticketType.name}</span>
                        </span>
                      </div>
                      <span className="tier-price">
                        {isSoldOut ? t("selectTicket.soldOut") : `${BigInt(ticketType.unitPrice || String(ticketType.price)).toLocaleString(language === "en" ? "en-US" : "vi-VN")} ₫`}
                      </span>
                    </div>
                  );
                })
              )}

              <div className="ticket-notice" style={{ marginTop: 12 }}>
                <Info size={14} />
                <span>
                  {t("selectTicket.minimumNotice", { min: minPerOrder })}
                  {hasAssignedSeatTickets ? ` · ${t("selectTicket.availableSeats", { count: availableSeatCount })}` : ""}
                </span>
              </div>
            </div>

            <div className="sidebar-selected">
              <div className="booking-availability-note">
                <span>{t("selectTicket.availabilityNotice")}</span>
                <button type="button" onClick={() => void loadSelection()}>{t("selectTicket.refreshAvailability")}</button>
              </div>
              {submitError && !isModalOpen && <p role="alert" className="booking-submit-error">{submitError}</p>}
              <div className="selected-header">
                <span className="label">{t("selectTicket.selected")}</span>
                {totalSelected > 0 && (
                  <span style={{ fontSize: 13, color: "var(--text-muted)" }}>
                    {totalSelected} {t("nav.tickets").toLowerCase()}
                  </span>
                )}
              </div>

              {totalSelected === 0 ? (
                <div className="selected-empty">{t("selectTicket.emptySelection")}</div>
              ) : (
                <div className="selected-seats-list">
                  {quantityTicketTypes
                    .filter((ticketType) => (quantities[ticketType.id] || 0) > 0)
                    .map((ticketType) => {
                      const colorCode = ticketType.colorCode || "var(--primary, #16a34a)";
                      const selectedQuantity = quantities[ticketType.id] || 0;
                      return (
                        <span
                          key={ticketType.id}
                          className="selected-seat-tag"
                          style={{
                            backgroundColor: colorCode,
                            color: "#ffffff",
                            borderColor: colorCode,
                          }}
                        >
                          {ticketType.name} x{selectedQuantity}
                          <span className="remove-seat" onClick={() => updateQuantity(ticketType, -selectedQuantity)}>
                            <X size={12} />
                          </span>
                        </span>
                      );
                    })}
                  {selectedSeats.map((seat) => {
                    const ticketType = ticketTypeById.get(seat.ticketTypeId);
                    const colorCode = ticketType?.colorCode || "var(--primary, #16a34a)";
                    return (
                      <span
                        key={seat.seatId}
                        className="selected-seat-tag"
                        style={{
                          backgroundColor: colorCode,
                          color: "#ffffff",
                          borderColor: colorCode,
                        }}
                      >
                        {seat.seatLabel}
                        <span className="remove-seat" onClick={() => handleRemoveSeat(seat.seatId)}>
                          <X size={12} />
                        </span>
                      </span>
                    );
                  })}
                </div>
              )}

              <div className="sidebar-action-embedded">
                {totalSelected === 0 ? (
                  <button className="action-btn disabled-state" disabled>
                    {t("selectTicket.selectAtLeastTicket")}
                  </button>
                ) : (
                  <button
                    className="action-btn active-state btn-continue"
                    ref={continueRef}
                    onClick={handleContinue}
                    disabled={totalSelected < minPerOrder}
                  >
                    <span className="btn-label">{t("selectTicket.continue")}</span>
                    <span className="btn-total">{subtotal.toLocaleString(language === "en" ? "en-US" : "vi-VN")} ₫</span>
                  </button>
                )}
              </div>
            </div>
          </div>
        </div>
      {standingPopupSector && standingPopupTicketTypes.length > 0 ? (
        <dialog className="standing-ticket-popup-overlay standing-sector-modal" ref={standingDialogRef} aria-labelledby="standing-sector-title" onCancel={() => setStandingPopupSectorId(null)}>
          <div className="standing-ticket-popup">
            <button
              type="button"
              className="standing-ticket-popup-close"
              onClick={() => setStandingPopupSectorId(null)}
              aria-label={t("common.close")}
            >
              <X size={22} />
            </button>
            <h3 id="standing-sector-title">{standingPopupSector.name}</h3>
            <p className="standing-ticket-popup-note">{t("selectTicket.oneSectorOnly")}</p>
            <div className="standing-ticket-popup-list">
              {standingPopupTicketTypes.map((ticketType) => (
                <div className="standing-ticket-popup-row quantity-ticket-row" key={ticketType.id}>
                  <span className="standing-ticket-popup-name">
                    <strong>{ticketType.name}</strong>
                    <small>{BigInt(ticketType.unitPrice || String(ticketType.price)).toLocaleString(language === "en" ? "en-US" : "vi-VN")} đ</small>
                  </span>
                  <div className="standing-ticket-popup-quantity">
                    <button
                      type="button"
                      onClick={() => updateQuantity(ticketType, -1)}
                      disabled={(quantities[ticketType.id] || 0) <= 0}
                    >
                      -
                    </button>
                    <span>{quantities[ticketType.id] || 0}</span>
                    <button
                      type="button"
                      onClick={() => updateQuantity(ticketType, 1)}
                      disabled={ticketType.status === "SOLD_OUT" || ticketType.quantityAvailable === 0}
                    >
                      +
                    </button>
                  </div>
                </div>
              ))}
            </div>
            <button
              type="button"
              className="standing-ticket-popup-switch"
              onClick={clearStandingSectorSelection}
            >
              {t("selectTicket.chooseAnotherSector")}
            </button>
            <button
              type="button"
              className="standing-ticket-popup-continue"
              onClick={() => setStandingPopupSectorId(null)}
            >
              <span>{t("selectTicket.continue")}</span>
              <span>- {standingPopupTotal.toLocaleString(language === "en" ? "en-US" : "vi-VN")} đ</span>
              <ChevronRight size={18} />
            </button>
          </div>
        </dialog>
      ) : null}
      {isModalOpen && (
        <dialog className="booking-info-modal-overlay" ref={recipientDialogRef} aria-labelledby="booking-recipient-title" onCancel={event => { if (submittingRef.current) event.preventDefault(); else setIsModalOpen(false); }}>
          <form className="booking-info-modal" onSubmit={handleModalSubmit}>
            <div className="booking-info-modal-header">
              <h3 id="booking-recipient-title">{t("selectTicket.recipientInfo")}</h3>
              <button
                type="button"
                className="booking-info-modal-close"
                onClick={() => setIsModalOpen(false)}
                aria-label={t("common.close")}
                disabled={isBookingInProgress}
              >
                <X size={18} />
              </button>
            </div>
            
            <div className="booking-info-modal-body">
              <div className="form-group">
                <label htmlFor="booking-customer-email">{t("profile.email")}</label>
                <input id="booking-customer-email" type="email" required maxLength={254} autoFocus className="form-input" value={modalEmail} onChange={e => setModalEmail(e.target.value)} disabled={isBookingInProgress} aria-invalid={Boolean(modalErrors.email)} />
                {modalErrors.email && <p role="alert" className="form-error">{modalErrors.email}</p>}
                <p className="booking-email-hint">{t("selectTicket.emailHint")}</p>
                {submitError && <p role="alert" className="booking-submit-error">{submitError}</p>}
              </div>
            </div>

            <div className="booking-info-modal-footer">
              <button
                type="button"
                className="btn-cancel"
                onClick={() => setIsModalOpen(false)}
                disabled={isBookingInProgress}
              >
                {t("selectTicket.cancel")}
              </button>
              <button
                type="submit"
                className="btn-confirm"
                disabled={isBookingInProgress}
              >
                {isBookingInProgress ? t("selectTicket.holdProcessing") : t("selectTicket.confirmHold")}
              </button>
            </div>
          </form>
        </dialog>
      )}
    </div>
  );
}
