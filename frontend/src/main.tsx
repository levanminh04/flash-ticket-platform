import ReactDOM from "react-dom/client";
import App from "./App.tsx";
import "./i18n";
import "./index.css";
import "./assets/css/global.css";
import "./assets/css/home.css";
import "./assets/css/venues.css";
import "./assets/css/select-ticket.css";
import "./assets/css/checkout.css";
import "./assets/css/payment-result.css";
import "./assets/css/profile-pages.css";
import "./assets/css/event-detail.css";
import "./assets/css/public-organizer.css";
import "./assets/css/organizer.css";
import "./assets/css/chatbot-widget.css";

const root = ReactDOM.createRoot(document.getElementById("root")!);

root.render(<App />);
