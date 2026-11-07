import { useState } from "react";
import { useTranslation } from "react-i18next";

export const orderIdPattern = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/;

export default function OrderLookupForm({ orderId, onLookup }: { orderId: string; onLookup: (orderId: string) => void }) {
  const { t } = useTranslation();
  const [value, setValue] = useState(orderId);
  return <form className="order-lookup-form" onSubmit={event => { event.preventDefault(); onLookup(value.trim().toLowerCase()); }}>
    <label htmlFor="lookup-order-id">{t("paymentV1.orderId")}</label>
    <div><input id="lookup-order-id" value={value} onChange={event => setValue(event.target.value)} required pattern={orderIdPattern.source.slice(1, -1)} maxLength={36} autoComplete="off" spellCheck={false} placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx" />
    <button className="btn btn-primary" type="submit">{t("paymentV1.lookup")}</button></div>
  </form>;
}
