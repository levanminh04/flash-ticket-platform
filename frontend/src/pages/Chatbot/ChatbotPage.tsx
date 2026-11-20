import { useKeycloak } from "@react-keycloak/web";
import { useTranslation } from "react-i18next";
import AccountCategoryNav from "../../components/common/AccountCategoryNav";
import AccountSidebar from "../../components/account/AccountSidebar";
import TixAdminTopNav from "../../components/admin/TixAdminTopNav";
import FloatingChatbot from "../../components/chatbot/FloatingChatbot";
import { AssistantConversationProvider } from "../../components/chatbot/AssistantConversationProvider";
import OrganizerLayout from "../../components/organizer/OrganizerLayout";
import { hasRealmRole } from "../../lib/auth";
import type { AssistantWorkspace } from "../../services/chatService";

export default function ChatbotPage({ workspace = "buyer" }: { workspace?: AssistantWorkspace }) {
  const { t } = useTranslation();
  const { keycloak, initialized } = useKeycloak();

  if (workspace === "admin") {
    const isAdmin = Boolean(keycloak.authenticated && hasRealmRole(keycloak.tokenParsed, "ADMIN"));
    return <main className="tix-dashboard-page tix-admin-page">
      <div className="tix-dashboard-container">
        {initialized && isAdmin ? <>
          <TixAdminTopNav activeTab="chatbot" />
          <AssistantConversationProvider workspace="admin">
            <FloatingChatbot fullPage />
          </AssistantConversationProvider>
        </> : <div className="tix-orders-card tix-admin-state-box" role={initialized ? "alert" : "status"}>
          <p>{t(!initialized ? "chatbot.loadingAccount" : keycloak.authenticated
            ? "chatbot.adminOnly" : "chatbot.loginRequired")}</p>
          {initialized && !keycloak.authenticated &&
            <button type="button" className="tix-admin-btn tix-admin-btn-primary"
              onClick={() => void keycloak.login()}>{t("auth.signIn")}</button>}
        </div>}
      </div>
    </main>;
  }

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
