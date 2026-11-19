import AccountCategoryNav from "../../components/common/AccountCategoryNav";
import AccountSidebar from "../../components/account/AccountSidebar";
import FloatingChatbot from "../../components/chatbot/FloatingChatbot";
import { AssistantConversationProvider } from "../../components/chatbot/AssistantConversationProvider";
import OrganizerLayout from "../../components/organizer/OrganizerLayout";
import type { AssistantWorkspace } from "../../services/chatService";

export default function ChatbotPage({ workspace = "buyer" }: { workspace?: AssistantWorkspace }) {
  if (workspace === "organizer") {
    return <OrganizerLayout hideSidebar hideHeader className="organizer-chat-page">
      <AssistantConversationProvider workspace="organizer">
        <FloatingChatbot fullPage />
      </AssistantConversationProvider>
    </OrganizerLayout>;
  }
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
