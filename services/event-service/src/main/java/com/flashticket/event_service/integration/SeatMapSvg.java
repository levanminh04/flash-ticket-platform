package com.flashticket.event_service.integration;

import com.flashticket.event_service.web.EventProblem;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/** Accepts self-contained vector artwork and strips embedded editor metadata. */
final class SeatMapSvg {
  private static final String SVG = "http://www.w3.org/2000/svg";
  private static final Set<String> ELEMENTS =
      Set.of(
          "svg",
          "g",
          "path",
          "rect",
          "circle",
          "ellipse",
          "line",
          "polyline",
          "polygon",
          "text",
          "tspan",
          "title",
          "desc",
          "defs",
          "linearGradient",
          "radialGradient",
          "stop",
          "clipPath");
  private static final Set<String> ATTRIBUTES =
      Set.of(
          "id",
          "version",
          "viewBox",
          "width",
          "height",
          "x",
          "y",
          "x1",
          "x2",
          "y1",
          "y2",
          "cx",
          "cy",
          "r",
          "rx",
          "ry",
          "fx",
          "fy",
          "d",
          "points",
          "transform",
          "fill",
          "fill-opacity",
          "fill-rule",
          "stroke",
          "stroke-width",
          "stroke-opacity",
          "stroke-linecap",
          "stroke-linejoin",
          "stroke-miterlimit",
          "stroke-dasharray",
          "opacity",
          "font-family",
          "font-size",
          "font-weight",
          "text-anchor",
          "dominant-baseline",
          "preserveAspectRatio",
          "offset",
          "stop-color",
          "stop-opacity",
          "gradientUnits",
          "gradientTransform",
          "clip-path",
          "clip-rule",
          "clipPathUnits");

  private SeatMapSvg() {}

  static byte[] clean(byte[] bytes) {
    try {
      var factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      var builder = factory.newDocumentBuilder();
      builder.setErrorHandler(new DefaultHandler());
      var root = builder.parse(new ByteArrayInputStream(bytes)).getDocumentElement();
      if (!"svg".equals(root.getLocalName())) throw invalid();
      validate(root, 0);
      var output = new ByteArrayOutputStream();
      var transformers = TransformerFactory.newInstance();
      transformers.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      transformers.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
      transformers.newTransformer().transform(new DOMSource(root), new StreamResult(output));
      return output.toByteArray();
    } catch (IOException | ParserConfigurationException | SAXException | TransformerException ex) {
      throw invalid();
    }
  }

  private static void validate(Element element, int depth) {
    if (depth > 64
        || !SVG.equals(element.getNamespaceURI())
        || !ELEMENTS.contains(element.getLocalName())) throw invalid();
    var attributes = element.getAttributes();
    for (int i = attributes.getLength() - 1; i >= 0; i--) {
      var attribute = attributes.item(i);
      String name = attribute.getNodeName();
      String value = attribute.getNodeValue();
      if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(attribute.getNamespaceURI())) continue;
      if (attribute.getNamespaceURI() == null && name.startsWith("data-")) {
        element.removeAttribute(name);
        continue;
      }
      if (attribute.getNamespaceURI() != null || !ATTRIBUTES.contains(name)) throw invalid();
      if (Set.of("fill", "stroke", "clip-path").contains(name)
          && !value.matches(
              "[A-Za-z][A-Za-z0-9-]*|#[A-Fa-f0-9]+|(?:rgb|rgba|hsl|hsla)\\([0-9.,% /+\\-]+\\)|url\\(#[A-Za-z0-9_.:-]+\\)"))
        throw invalid();
    }
    for (Node child = element.getFirstChild(); child != null; ) {
      Node next = child.getNextSibling();
      if (child instanceof Element nested && !"metadata".equals(nested.getLocalName())) {
        validate(nested, depth + 1);
      } else if (child.getNodeType() != Node.TEXT_NODE
          && child.getNodeType() != Node.CDATA_SECTION_NODE) {
        element.removeChild(child);
      }
      child = next;
    }
  }

  private static EventProblem invalid() {
    return new EventProblem(422, "INVALID_IMAGE", "file");
  }
}
