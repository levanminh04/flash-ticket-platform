import ReactDOM from "react-dom/client";
import App from "./App.tsx";
import "./i18n";
import "./index.css";
import "./assets/css/tokens.css";
import "./assets/css/global.css";
import "./assets/css/home.css";
import "./pages/Venues/venues.css";
import "./pages/SelectTicket/select-ticket.css";
import "./pages/Checkout/checkout.css";
import "./pages/Payment/payment-result.css";
import "./assets/css/profile-pages.css";
import "./pages/EventDetail/event-detail.css";
import "./pages/Organizer/public-organizer.css";
import "./assets/css/organizer.css";
import "./assets/css/chatbot-widget.css";
import "./components/layout/Navbar.css";

const root = ReactDOM.createRoot(document.getElementById("root")!);

root.render(<App />);
