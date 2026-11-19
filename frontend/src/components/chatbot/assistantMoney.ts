export function normalizeAssistantVnd(value: string): bigint | null {
  if (typeof value !== "string" || value.length > 80 || !/^\d+(?:\.0+)?$/.test(value)) return null;
  return BigInt(value.split(".", 1)[0]);
}

export function formatAssistantMoney(value: string | null | undefined, currency: string | null | undefined, locale: string): string {
  if (value == null || !currency || !/^[A-Z]{3}$/.test(currency)) return "—";
  const amount = currency === "VND" ? normalizeAssistantVnd(value) : null;
  if (amount !== null) return new Intl.NumberFormat(locale, { style: "currency", currency: "VND" }).format(amount);
  return currency !== "VND" && typeof value === "string" && value.length <= 80 && /^\d+(?:\.\d+)?$/.test(value)
    ? `${value} ${currency}` : "—";
}
