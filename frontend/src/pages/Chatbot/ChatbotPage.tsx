import AccountCategoryNav from "../../components/common/AccountCategoryNav";
import AccountSidebar from "../../components/account/AccountSidebar";
import FloatingChatbot from "../../components/chatbot/FloatingChatbot";

export default function ChatbotPage() {
  return <div className="chatbot-page">
    <AccountCategoryNav />
    <div className="container account-layout-container">
      <AccountSidebar />
      <section className="account-main-content">
        <h1 className="page-title">Trợ lý FlashTicket</h1>
        <FloatingChatbot fullPage />
      </section>
    </div>
  </div>;
}
