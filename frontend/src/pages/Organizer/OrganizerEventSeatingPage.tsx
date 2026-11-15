import { Navigate, useParams, useSearchParams } from "react-router-dom";

export default function OrganizerEventSeatingPage() {
  const { eventId } = useParams<{ eventId: string }>();
  const [searchParams] = useSearchParams();

  if (!eventId) {
    return <Navigate to="/organizer/events" replace />;
  }

  const rawStep = searchParams.get("step");
  const mappedStep =
    rawStep === "media"
      ? "media"
      : rawStep === "pricing" || rawStep === "tickets"
      ? "tickets"
      : rawStep === "review"
      ? "review"
      : "sectors";

  return <Navigate to={`/organizer/events/${eventId}/edit?step=${mappedStep}`} replace />;
}
