import { useEffect, useRef, useState } from "react";
import {
  Armchair,
  ChevronDown,
  Copy,
  Download,
  Eye,
  EyeOff,
  FolderOpen,
  Grid2X2,
  Grid3X3,
  LayoutGrid,
  Maximize2,
  MousePointer2,
  MousePointerClick,
  Redo2,
  RotateCcw,
  Shapes,
  Trash2,
  Undo2,
  Upload,
} from "lucide-react";
import type { SeatMapEditorTool } from "./seatMapEditorTypes";

export type SeatMapShapeMenuKey =
  | "rectangle"
  | "standing_rect"
  | "trapezoid"
  | "fan"
  | "ring"
  | "radial_sectors"
  | "curved_left"
  | "curved_right"
  | "stage"
  | "t_stage"
  | "foh"
  | "stadium_bowl"
  | "concert_t_stage";

interface SeatMapTopToolbarProps {
  activeTool: SeatMapEditorTool;
  canUndo: boolean;
  canRedo: boolean;
  hasSelectedShape: boolean;
  selectedSeatCount: number;
  selectedShapeHiddenSeatCount: number;
  onSelectTool: (tool: SeatMapEditorTool) => void;
  onOpenOrCreateVenueShape?: () => void;
  isVenueShapeSelected?: boolean;
  onAddShapeMenu: (key: SeatMapShapeMenuKey) => void;
  onDuplicateSelected: () => void;
  onResetView?: () => void;
  showGrid?: boolean;
  onToggleGrid?: () => void;
  onUndo: () => void;
  onRedo: () => void;
  onDeleteSelected: () => void;
  onHideSelectedSeats: () => void;
  onRestoreHiddenSeats: () => void;
  onImportJson: () => void;
  onExportJson: () => void;
  onImportSvg: () => void;
  onExportSvg: () => void;
  onResetDraft: () => void;
}

const SHAPE_MENU_GROUPS: Array<{
  title: string;
  items: Array<{ key: SeatMapShapeMenuKey; label: string; hint?: string }>;
}> = [
  {
    title: "Mẫu sơ đồ chuẩn (1-click)",
    items: [
      { key: "stadium_bowl", label: "Sơ đồ SVĐ (Bowl)" },
      { key: "concert_t_stage", label: "Sơ đồ Concert Chữ T" },
    ],
  },
  {
    title: "Phân khu khán đài & Khu vé",
    items: [
      { key: "rectangle", label: "Khu chữ nhật (Ngồi)" },
      { key: "standing_rect", label: "Khu Fanzone (Đứng)" },
      { key: "trapezoid", label: "Khán đài hình thang" },
      { key: "fan", label: "Khán đài rẻ quạt" },
      { key: "ring", label: "Khán đài vòng cung" },
      { key: "radial_sectors", label: "Vành chia 6 múi" },
      { key: "curved_left", label: "Cánh cung trái" },
      { key: "curved_right", label: "Cánh cung phải" },
    ],
  },
  {
    title: "Sân khấu & Kỹ thuật",
    items: [
      { key: "stage", label: "Sân khấu ngang" },
      { key: "t_stage", label: "Sân khấu chữ T" },
      { key: "foh", label: "Trạm kỹ thuật FOH" },
    ],
  },
];

export default function SeatMapTopToolbar({
  activeTool,
  canUndo,
  canRedo,
  hasSelectedShape,
  selectedSeatCount,
  selectedShapeHiddenSeatCount,
  onSelectTool,
  onOpenOrCreateVenueShape,
  isVenueShapeSelected = false,
  onAddShapeMenu,
  onDuplicateSelected,
  onResetView,
  showGrid = true,
  onToggleGrid,
  onUndo,
  onRedo,
  onDeleteSelected,
  onHideSelectedSeats,
  onRestoreHiddenSeats,
  onImportJson,
  onExportJson,
  onImportSvg,
  onExportSvg,
  onResetDraft,
}: SeatMapTopToolbarProps) {
  const [shapeMenuOpen, setShapeMenuOpen] = useState(false);
  const [utilityMenuOpen, setUtilityMenuOpen] = useState(false);
  const shapeMenuRef = useRef<HTMLDivElement | null>(null);
  const utilityMenuRef = useRef<HTMLDivElement | null>(null);
  const isEditSeatsMode = activeTool === "editSeats";

  useEffect(() => {
    if (!shapeMenuOpen && !utilityMenuOpen) {
      return;
    }
    const handleOutsideClick = (event: MouseEvent) => {
      if (shapeMenuRef.current && !shapeMenuRef.current.contains(event.target as Node)) {
        setShapeMenuOpen(false);
      }
      if (utilityMenuRef.current && !utilityMenuRef.current.contains(event.target as Node)) {
        setUtilityMenuOpen(false);
      }
    };
    window.addEventListener("mousedown", handleOutsideClick);
    return () => window.removeEventListener("mousedown", handleOutsideClick);
  }, [shapeMenuOpen, utilityMenuOpen]);

  return (
    <div className="smp-topbar" role="toolbar" aria-label="Thanh công cụ thiết kế sơ đồ ghế">
      {/* MODE */}
      <div className="smp-topbar-section">
        <span className="smp-topbar-rail-label">MODE</span>
        <div className="smp-topbar-group">
          <button
            type="button"
            className={`smp-tool-btn ${!isEditSeatsMode ? "is-mode-active" : ""}`}
            onClick={() => {
              setShapeMenuOpen(false);
              onSelectTool("select");
            }}
            title="Chỉnh sửa bố cục phân khu, sân khấu và hình dạng"
          >
            <LayoutGrid size={18} strokeWidth={1.85} />
            <span>Sơ đồ</span>
          </button>

          <button
            type="button"
            className={`smp-tool-btn ${isEditSeatsMode ? "is-mode-active" : ""}`}
            onClick={() => {
              setShapeMenuOpen(false);
              onSelectTool("editSeats");
            }}
            title="Chỉnh sửa từng ghế, tạo lối đi, ẩn/hiện ghế"
          >
            <Armchair size={18} strokeWidth={1.85} />
            <span>Chỉnh ghế</span>
          </button>
        </div>
      </div>

      <div className="smp-topbar-divider" />

      {/* TOOLS */}
      <div className="smp-topbar-section smp-topbar-tools">
        <span className="smp-topbar-rail-label">TOOLS</span>
        <div className="smp-topbar-group">
          {!isEditSeatsMode ? (
            <>
              <button
                type="button"
                className={`smp-tool-btn ${activeTool === "select" ? "is-tool-active" : ""}`}
                onClick={() => {
                  setShapeMenuOpen(false);
                  onSelectTool("select");
                }}
                title="Chọn, di chuyển và biến đổi phân khu (V)"
              >
                <MousePointer2 size={18} strokeWidth={1.85} />
                <span>Chọn</span>
              </button>

              <button
                type="button"
                className={`smp-tool-btn ${activeTool === "seatRows" ? "is-tool-active" : ""}`}
                onClick={() => {
                  setShapeMenuOpen(false);
                  onSelectTool("seatRows");
                }}
                title="Kéo thả trên lưới để vẽ nhanh phân khu hàng ghế (R)"
              >
                <Grid2X2 size={18} strokeWidth={1.85} />
                <span>Hàng ghế</span>
              </button>

              {onOpenOrCreateVenueShape ? (
                <button
                  type="button"
                  className={`smp-tool-btn ${isVenueShapeSelected ? "is-tool-active" : ""}`}
                  onClick={() => {
                    setShapeMenuOpen(false);
                    onOpenOrCreateVenueShape();
                  }}
                  title="Tạo hoặc chỉnh sửa khung địa điểm / sân khấu (Venue Shape)"
                >
                  <svg
                    width="18"
                    height="18"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    aria-hidden="true"
                  >
                    <rect x="3" y="5" width="18" height="14" rx="6" />
                  </svg>
                  <span>Venue Shape</span>
                </button>
              ) : null}

              <div className="smp-shape-dropdown-wrap" ref={shapeMenuRef}>
                <button
                  type="button"
                  className={`smp-tool-btn ${shapeMenuOpen ? "is-tool-active" : ""}`}
                  onClick={() => setShapeMenuOpen((prev) => !prev)}
                  title="Thêm hình khối phân khu, sân khấu hoặc mẫu sơ đồ"
                >
                  <Shapes size={18} strokeWidth={1.85} />
                  <span className="smp-tool-label-row">
                    Hình khối <ChevronDown size={12} />
                  </span>
                </button>

                {shapeMenuOpen && (
                  <div className="smp-shape-dropdown-menu" role="menu">
                    {SHAPE_MENU_GROUPS.map((group) => (
                      <div key={group.title} className="smp-shape-dropdown-group">
                        <div className="smp-shape-dropdown-title">{group.title}</div>
                        {group.items.map((item) => (
                          <button
                            key={item.key}
                            type="button"
                            className="smp-shape-dropdown-item"
                            onClick={() => {
                              setShapeMenuOpen(false);
                              onAddShapeMenu(item.key);
                            }}
                          >
                            <span>{item.label}</span>
                            {item.hint && <small>{item.hint}</small>}
                          </button>
                        ))}
                      </div>
                    ))}
                  </div>
                )}
              </div>

              <div className="smp-topbar-subdivider" />

              {onResetView ? (
                <button
                  type="button"
                  className="smp-tool-btn"
                  onClick={onResetView}
                  title="Căn giữa & đặt lại góc nhìn sơ đồ"
                >
                  <Maximize2 size={17} strokeWidth={1.85} />
                  <span>Reset view</span>
                </button>
              ) : null}

              {onToggleGrid ? (
                <button
                  type="button"
                  className={`smp-tool-btn ${showGrid ? "is-tool-active" : ""}`}
                  onClick={onToggleGrid}
                  title={showGrid ? "Ẩn lưới kỹ thuật" : "Hiện lưới kỹ thuật"}
                >
                  <Grid3X3 size={17} strokeWidth={1.85} />
                  <span>Lưới</span>
                </button>
              ) : null}

              <button
                type="button"
                className="smp-tool-btn"
                disabled={!hasSelectedShape}
                onClick={onDuplicateSelected}
                title="Nhân bản phân khu đang chọn"
              >
                <Copy size={17} strokeWidth={1.85} />
                <span>Nhân bản</span>
              </button>

              <button
                type="button"
                className="smp-tool-btn"
                disabled={!canUndo}
                onClick={onUndo}
                title="Hoàn tác (Ctrl+Z)"
              >
                <Undo2 size={17} strokeWidth={1.85} />
                <span>Hoàn tác</span>
              </button>

              <button
                type="button"
                className="smp-tool-btn"
                disabled={!canRedo}
                onClick={onRedo}
                title="Làm lại (Ctrl+Y)"
              >
                <Redo2 size={17} strokeWidth={1.85} />
                <span>Làm lại</span>
              </button>

              <button
                type="button"
                className="smp-tool-btn is-danger"
                disabled={!hasSelectedShape}
                onClick={onDeleteSelected}
                title="Xóa phân khu đang chọn (Delete)"
              >
                <Trash2 size={17} strokeWidth={1.85} />
                <span>Xóa</span>
              </button>

              <div className="smp-shape-dropdown-wrap" ref={utilityMenuRef}>
                <button
                  type="button"
                  className={`smp-tool-btn ${utilityMenuOpen ? "is-tool-active" : ""}`}
                  onClick={() => {
                    setShapeMenuOpen(false);
                    setUtilityMenuOpen((prev) => !prev);
                  }}
                  title="Xuất/Nhập SVG, JSON và khôi phục sơ đồ"
                >
                  <FolderOpen size={17} strokeWidth={1.85} />
                  <span className="smp-tool-label-row">
                    Tệp <ChevronDown size={12} />
                  </span>
                </button>

                {utilityMenuOpen && (
                  <div
                    className="smp-shape-dropdown-menu"
                    style={{ left: 0, right: "auto", width: "max-content", minWidth: 184 }}
                  >
                    <div className="smp-shape-dropdown-group">
                      <div className="smp-shape-dropdown-title">Ảnh sơ đồ (SVG)</div>
                      <button
                        type="button"
                        className="smp-shape-dropdown-item"
                        onClick={() => {
                          setUtilityMenuOpen(false);
                          onExportSvg();
                        }}
                      >
                        <span style={{ display: "inline-flex", alignItems: "center", gap: 8 }}>
                          <Download size={14} />
                          <span>Export SVG</span>
                        </span>
                      </button>
                      <button
                        type="button"
                        className="smp-shape-dropdown-item"
                        onClick={() => {
                          setUtilityMenuOpen(false);
                          onImportSvg();
                        }}
                      >
                        <span style={{ display: "inline-flex", alignItems: "center", gap: 8 }}>
                          <Upload size={14} />
                          <span>Upload SVG</span>
                        </span>
                      </button>
                    </div>

                    <div className="smp-shape-dropdown-group">
                      <div className="smp-shape-dropdown-title">Dữ liệu & Khôi phục</div>
                      <button
                        type="button"
                        className="smp-shape-dropdown-item"
                        onClick={() => {
                          setUtilityMenuOpen(false);
                          onImportJson();
                        }}
                      >
                        <span style={{ display: "inline-flex", alignItems: "center", gap: 8 }}>
                          <Upload size={14} />
                          <span>Nhập JSON</span>
                        </span>
                      </button>
                      <button
                        type="button"
                        className="smp-shape-dropdown-item"
                        onClick={() => {
                          setUtilityMenuOpen(false);
                          onExportJson();
                        }}
                      >
                        <span style={{ display: "inline-flex", alignItems: "center", gap: 8 }}>
                          <Download size={14} />
                          <span>Xuất JSON</span>
                        </span>
                      </button>
                      <button
                        type="button"
                        className="smp-shape-dropdown-item"
                        onClick={() => {
                          setUtilityMenuOpen(false);
                          onResetDraft();
                        }}
                      >
                        <span style={{ display: "inline-flex", alignItems: "center", gap: 8 }}>
                          <RotateCcw size={14} />
                          <span>Khôi phục</span>
                        </span>
                      </button>
                    </div>
                  </div>
                )}
              </div>
            </>
          ) : (
            <>
              <button
                type="button"
                className="smp-tool-btn is-tool-active"
                onClick={() => onSelectTool("editSeats")}
                title="Click hoặc quét khung chọn nhiều ghế trong phân khu"
              >
                <MousePointerClick size={18} strokeWidth={1.85} />
                <span>Chọn ghế</span>
              </button>

              <button
                type="button"
                className="smp-tool-btn"
                disabled={selectedSeatCount === 0}
                onClick={onHideSelectedSeats}
                title="Ẩn các ghế đang chọn để tạo lối đi hoặc khoảng trống"
              >
                <EyeOff size={17} strokeWidth={1.85} />
                <span>Ẩn ghế</span>
              </button>

              <button
                type="button"
                className="smp-tool-btn"
                disabled={selectedShapeHiddenSeatCount === 0}
                onClick={onRestoreHiddenSeats}
                title="Khôi phục toàn bộ ghế đã ẩn trong phân khu đang chọn"
              >
                <Eye size={17} strokeWidth={1.85} />
                <span>Khôi phục</span>
              </button>

              <div className="smp-topbar-subdivider" />

              <button
                type="button"
                className="smp-tool-btn"
                disabled={!canUndo}
                onClick={onUndo}
                title="Hoàn tác (Ctrl+Z)"
              >
                <Undo2 size={17} strokeWidth={1.85} />
                <span>Hoàn tác</span>
              </button>

              <button
                type="button"
                className="smp-tool-btn"
                disabled={!canRedo}
                onClick={onRedo}
                title="Làm lại (Ctrl+Y)"
              >
                <Redo2 size={17} strokeWidth={1.85} />
                <span>Làm lại</span>
              </button>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
