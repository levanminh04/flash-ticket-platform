import { useState } from "react";
import "./moving-logo-strip.css";

interface PaymentBrand {
  id: string;
  name: string;
  logoUrl: string;
  width: number;
  height: number;
}

const PAYMENT_BRANDS: PaymentBrand[] = [
  { id: "momo", name: "MoMo", logoUrl: "/payment-logos/momo.svg", width: 90, height: 24 },
  { id: "vnpay", name: "VNPay", logoUrl: "/payment-logos/vnpay.svg", width: 79, height: 23 },
  { id: "zalopay", name: "ZaloPay", logoUrl: "/payment-logos/zalopay.svg", width: 89, height: 20 },
  { id: "payos", name: "payOS", logoUrl: "/payment-logos/payos.svg", width: 79, height: 22 },
  { id: "paypal", name: "PayPal", logoUrl: "/payment-logos/paypal.svg", width: 75, height: 20 },
  { id: "visa", name: "Visa", logoUrl: "/payment-logos/visa.svg", width: 52, height: 17 },
  { id: "mastercard", name: "Mastercard", logoUrl: "/payment-logos/mastercard.svg", width: 136, height: 22 },
  { id: "apple_pay", name: "Apple Pay", logoUrl: "/payment-logos/apple_pay.svg", width: 51, height: 21 },
  { id: "google_pay", name: "Google Pay", logoUrl: "/payment-logos/google_pay.svg", width: 53, height: 20 },
];

// 3 repetitions per group ensures seamless coverage across 4K displays
const GROUP_CYCLES = [0, 1, 2];

export default function MovingLogoStrip() {
  const [isPaused, setIsPaused] = useState(false);

  return (
    <div
      className={`moving-logo-strip${isPaused ? " is-paused" : ""}`}
      role="region"
      aria-label="Đối tác thanh toán minh họa"
    >
      {/* Keyboard-only skip/pause toggle for accessibility */}
      <button
        type="button"
        className="moving-logo-sr-control"
        onClick={() => setIsPaused((prev) => !prev)}
        aria-pressed={isPaused}
      >
        {isPaused ? "Tiếp tục chuyển động dải logo" : "Tạm dừng chuyển động dải logo"}
      </button>

      <div className="moving-logo-track">
        {/* Primary marquee group */}
        <div className="moving-logo-group">
          {GROUP_CYCLES.map((cycleIndex) =>
            PAYMENT_BRANDS.map((brand) => {
              const isFirstCycle = cycleIndex === 0;
              return (
                <div
                  key={`primary-${cycleIndex}-${brand.id}`}
                  className={`moving-logo-item${!isFirstCycle ? " moving-logo-repeat-extra" : ""}`}
                  data-brand={brand.id}
                  title={brand.name}
                  tabIndex={isFirstCycle ? 0 : -1}
                  role={isFirstCycle ? "img" : undefined}
                  aria-label={isFirstCycle ? brand.name : undefined}
                  aria-hidden={!isFirstCycle ? "true" : undefined}
                >
                  <img
                    src={brand.logoUrl}
                    alt={isFirstCycle ? brand.name : ""}
                    width={brand.width}
                    height={brand.height}
                    loading="lazy"
                  />
                </div>
              );
            })
          )}
        </div>

        {/* Clone marquee group for seamless infinite loop */}
        <div className="moving-logo-group" aria-hidden="true">
          {GROUP_CYCLES.map((cycleIndex) =>
            PAYMENT_BRANDS.map((brand) => (
              <div
                key={`clone-${cycleIndex}-${brand.id}`}
                className="moving-logo-item"
                data-brand={brand.id}
                tabIndex={-1}
              >
                <img
                  src={brand.logoUrl}
                  alt=""
                  width={brand.width}
                  height={brand.height}
                  loading="lazy"
                />
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
}
