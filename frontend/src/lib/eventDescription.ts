import { createElement } from "react";

const richTextTags = new Set(["p", "div", "br", "strong", "b", "em", "i", "u", "s", "del", "mark", "blockquote", "ol", "ul", "li", "h1", "h2", "h3", "h4", "h5", "h6", "pre", "code", "hr", "a"]);
const ignoredTags = new Set(["script", "style", "template", "iframe", "object", "svg", "math", "noscript"]);

export function descriptionAlignment(element: HTMLElement) {
  const value = element.style.textAlign;
  return value === "left" || value === "center" || value === "right" || value === "justify"
    ? value
    : undefined;
}

export function descriptionContent(description: string) {
  if (!/<\/?[a-z][^>]*>/i.test(description)) return description;
  const template = document.createElement("template");
  template.innerHTML = description;

  // Rebuild permitted HTML as React elements. Never copy source attributes or inject raw HTML.
  function render(node: Node, key: number): React.ReactNode {
    if (node.nodeType === Node.TEXT_NODE) return node.textContent;
    if (!(node instanceof HTMLElement)) return null;
    const tag = node.localName;
    if (ignoredTags.has(tag)) return null;
    const children = Array.from(node.childNodes, render);
    if (!richTextTags.has(tag)) return createElement("span", { key }, children);
    if (tag === "br" || tag === "hr") return createElement(tag, { key });
    if (tag === "a") {
      try {
        const href = new URL(node.getAttribute("href") || "", window.location.href);
        if (node.getAttribute("href") && ["http:", "https:", "mailto:"].includes(href.protocol)) {
          return createElement("a", { key, href: href.href }, children);
        }
      } catch {
        // Invalid links still retain their visible text.
      }
      return createElement("span", { key }, children);
    }
    const textAlign = tag === "p" || /^h[1-6]$/.test(tag) ? descriptionAlignment(node) : undefined;
    return createElement(tag, { key, style: textAlign ? { textAlign } : undefined }, children);
  }

  return Array.from(template.content.childNodes, render);
}

