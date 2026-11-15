import { useEffect, useState } from "react";
import { EditorContent, useEditor, useEditorState } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import { AlignCenter, AlignJustify, AlignLeft, AlignRight, Bold, Italic, List, ListOrdered, Underline } from "lucide-react";
import { useTranslation } from "react-i18next";
import TixSelect from "./TixSelect";
import { descriptionAlignment } from "../../lib/eventDescription";
import "./organizer-description-editor.css";

const DescriptionKit = StarterKit.extend({
  addGlobalAttributes() {
    return [{
      types: ["paragraph", "heading"],
      attributes: {
        textAlign: {
          default: null,
          parseHTML: descriptionAlignment,
          renderHTML: attributes => attributes.textAlign ? { style: `text-align: ${attributes.textAlign}` } : {},
        },
      },
    }];
  },
});

export default function OrganizerDescriptionEditor({ initialValue, disabled, onChange }: {
  initialValue: string;
  disabled: boolean;
  onChange: () => void;
}) {
  const { t, i18n } = useTranslation();
  const vi = i18n.resolvedLanguage?.startsWith("vi");
  const label = t("organizerEventCreate.fieldDescription");
  const template = vi
    ? "<h2>Giới thiệu sự kiện</h2><p>[Tóm tắt nội dung và điểm nổi bật]</p><h2>Chi tiết sự kiện</h2><ul><li><strong>Chương trình chính:</strong> [Hoạt động và lịch trình]</li><li><strong>Khách mời:</strong> [Nghệ sĩ hoặc diễn giả]</li><li><strong>Trải nghiệm đặc biệt:</strong> [Hoạt động dành cho người tham dự]</li></ul><h2>Điều khoản và điều kiện</h2><p>[Quy định vé, check-in và lưu ý về độ tuổi]</p>"
    : "<h2>Event introduction</h2><p>[Summarize the event and its highlights]</p><h2>Event details</h2><ul><li><strong>Program:</strong> [Activities and schedule]</li><li><strong>Guests:</strong> [Artists or speakers]</li><li><strong>Special experiences:</strong> [Attendee activities]</li></ul><h2>Terms and conditions</h2><p>[Ticket rules, check-in and age requirements]</p>";
  const [value, setValue] = useState(() => initialValue.trim() ? initialValue : template);
  const editor = useEditor({
    extensions: [DescriptionKit.configure({ heading: { levels: [2, 3] } })],
    content: !initialValue.trim() ? template : /<\/?[a-z][^>]*>/i.test(initialValue) ? initialValue : {
      type: "doc",
      content: initialValue.split(/\r?\n/).map(line => ({
        type: "paragraph",
        content: line ? [{ type: "text", text: line }] : [],
      })),
    },
    editorProps: { attributes: { role: "textbox", "aria-multiline": "true", "aria-label": label } },
    onUpdate: ({ editor }) => {
      setValue(editor.getText().trim() ? editor.getHTML() : "");
      onChange();
    },
  });
  const format = useEditorState({
    editor,
    selector: ({ editor }) => ({
      heading: editor?.isActive("heading", { level: 2 }) ? "2" : editor?.isActive("heading", { level: 3 }) ? "3" : "p",
      alignment: editor?.getAttributes("heading").textAlign
        ?? editor?.getAttributes("paragraph").textAlign ?? "left",
      bold: editor?.isActive("bold"),
      italic: editor?.isActive("italic"),
      underline: editor?.isActive("underline"),
      bulletList: editor?.isActive("bulletList"),
      orderedList: editor?.isActive("orderedList"),
    }),
  });
  useEffect(() => { editor?.setEditable(!disabled, false); }, [editor, disabled]);

  const buttons = [
    { label: vi ? "In đậm" : "Bold", icon: Bold, active: format?.bold, run: () => editor?.chain().focus().toggleBold().run() },
    { label: vi ? "In nghiêng" : "Italic", icon: Italic, active: format?.italic, run: () => editor?.chain().focus().toggleItalic().run() },
    { label: vi ? "Gạch chân" : "Underline", icon: Underline, active: format?.underline, run: () => editor?.chain().focus().toggleUnderline().run() },
    ...([
      ["left", "Căn trái", "Align left", AlignLeft],
      ["center", "Căn giữa", "Align center", AlignCenter],
      ["right", "Căn phải", "Align right", AlignRight],
      ["justify", "Căn đều", "Justify", AlignJustify],
    ] as const).map(([textAlign, vietnamese, english, icon]) => ({
      label: vi ? vietnamese : english, icon, active: format?.alignment === textAlign,
      run: () => editor?.chain().focus().updateAttributes("paragraph", { textAlign })
        .updateAttributes("heading", { textAlign }).run(),
    })),
    { label: vi ? "Danh sách gạch đầu dòng" : "Bullet list", icon: List, active: format?.bulletList, run: () => editor?.chain().focus().toggleBulletList().run() },
    { label: vi ? "Danh sách đánh số" : "Numbered list", icon: ListOrdered, active: format?.orderedList, run: () => editor?.chain().focus().toggleOrderedList().run() },
  ];

  return <div className="tix-description-editor">
    <input type="hidden" name="description" value={value} />
    <div className="tix-description-toolbar" role="group" aria-label={label}>
      <TixSelect ariaLabel={vi ? "Kiểu đoạn văn" : "Paragraph style"} disabled={disabled || !editor}
        value={format?.heading ?? "p"} options={[
          { value: "p", label: vi ? "Đoạn văn" : "Paragraph" },
          { value: "2", label: vi ? "Tiêu đề" : "Heading" },
          { value: "3", label: vi ? "Tiêu đề phụ" : "Subheading" },
        ]} onChange={style => {
          const chain = editor?.chain().focus();
          if (style === "p") chain?.setParagraph().run();
          else chain?.setHeading({ level: style === "2" ? 2 : 3 }).run();
        }} />
      {buttons.map(({ label, icon: Icon, active, run }) =>
        <button key={label} className="tix-description-action" type="button" title={label} aria-label={label} aria-pressed={Boolean(active)}
          disabled={disabled || !editor} onClick={run}><Icon size={17} aria-hidden="true" /></button>
      )}
    </div>
    <EditorContent editor={editor} className="tix-description-content" />
  </div>;
}
