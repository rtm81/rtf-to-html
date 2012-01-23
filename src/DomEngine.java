import java.awt.Color;
import java.util.Hashtable;
import java.util.Stack;
import java.util.Enumeration;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;

class StateStyle {
	Engine.State state;
	Engine.State defState;
	
	public StateStyle(Engine.State state, Engine.State defState) {
		this.state = state;
		this.defState = defState;
	}

	public String toString() {
		return toString(state, defState);
	}

	public static String toString(Engine.State state, Engine.State defState) {
		StringBuilder result = new StringBuilder();

		if (state.getFontSize() != defState.getFontSize()) {
			result.append("font-size: ");
			result.append(state.getFontSize());
			result.append("pt;");
		}

		if (state.isItalic()) {
			result.append("font-style: italic;");
		}

		if (state.isBold()) {
			result.append("font-weight: bold;");
		}

		if (state.getFont() != null) {
			result.append("font-family: " + state.getFont().getFontName() + ";");
		}

		if (!state.getForeColor().equals(defState.getForeColor())) {
			result.append("color: #" + colorString(state.getForeColor()) + ";");
		}

		return result.toString();
	}

	static String colorString(Color color) {
		return String.format("%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
	}
}

class ParaStyle {
	public static String toString(Engine.ParaState state, Engine.ParaState defParaState) {
		StringBuilder result = new StringBuilder();

		switch (state.getAlign()) {
			case CENTER:
				result.append("text-align: center;");
				break;
			case JUSTIFY:
				result.append("text-align: justify;");
				break;
			case RIGHT:
				result.append("text-align: right;");
				break;
		}

		if (state.getFirstLineIndent() != defParaState.getFirstLineIndent()) {
			result.append("text-indent: ");
			result.append(state.getFirstLineIndent() / 20);
			result.append("pt;");
		}

		if (state.getLeftIndent() != defParaState.getLeftIndent()) {
			result.append("padding-left: ");
			result.append(state.getLeftIndent() / 20);
			result.append("pt;");
		}

		return result.toString();
	}
}

public class DomEngine extends Engine {
	Document document;

	Element htmlNode;
	Element headNode;
	Element bodyNode;

	Element styleNode;

	Element currentPara;
	Element currentTarget;

	Element currentRow;
	Element currentCell;
	Element currentTable;

	int nextClass = 0;

	public DomEngine() throws Exception {
		// document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		document = DOMImplementationRegistry.newInstance().getDOMImplementation("XML 1.0").createDocument("http://www.w3.org/1999/xhtml", "html", null);
		if (document == null) {
			System.err.println("Couldn't find suitable DOM implementation!");
			throw new RuntimeException();
		}

		// htmlNode = document.createElement("html");
		// document.appendChild(getHtml());
		htmlNode = document.getDocumentElement();

		headNode = document.createElement("head");
		bodyNode = document.createElement("body");

		htmlNode.appendChild(document.createTextNode("\n"));
		htmlNode.appendChild(getHead());
		htmlNode.appendChild(document.createTextNode("\n"));
		htmlNode.appendChild(getBody());
	}
	
	public void end() {
		try {
			Element metaEncoding = document.createElement("meta");
			metaEncoding.setAttribute("http-equiv", "Content-Type");
			metaEncoding.setAttribute("content", "text/html; charset=utf-8");

			getHead().appendChild(document.createTextNode("\n"));
			getHead().appendChild(metaEncoding);

			styleNode = document.createElement("style");
			styleNode.setAttribute("type", "text/css");

			StringBuilder bodyStyle = new StringBuilder();
			bodyStyle.append("* { margin: 0; padding: 0; }\n");
			bodyStyle.append("html { width: ");
			bodyStyle.append(getProgramState().getPaperWidth() / 20);
			bodyStyle.append("pt; margin: 0 auto; background-color: lightgrey;");
			bodyStyle.append(" }\n");

			bodyStyle.append("body { padding-left: ");
			bodyStyle.append(getProgramState().getLeftMargin() / 20);
			bodyStyle.append("pt; padding-right: ");
			bodyStyle.append(getProgramState().getRightMargin() / 20);
			bodyStyle.append("pt; background-color: white;");
			bodyStyle.append("font-size: ");
			bodyStyle.append(getDefState().getFontSize());
			bodyStyle.append("pt;");
			bodyStyle.append(" }\n");

			bodyStyle.append("p { ");
			bodyStyle.append("text-indent: ");
			bodyStyle.append(getDefParaState().getFirstLineIndent() / 20);
			bodyStyle.append("pt;");
			bodyStyle.append(" }\n");

			bodyStyle.append("table { border-collapse: collapse; border-right: solid 1px black; border-bottom: solid 1px black; }\n");
			bodyStyle.append("td { border-left: solid 1px black; border-top: solid 1px black; }\n");
			styleNode.appendChild(document.createTextNode(bodyStyle.toString()));

			getHead().appendChild(document.createTextNode("\n"));
			getHead().appendChild(styleNode);

			// Transform inline styles to classes
			Hashtable<String, String> classes = new Hashtable<String, String>();

			NodeList spans = document.getElementsByTagName("span");
			for (int i = 0; i < spans.getLength(); ++i) {
				pullElementStyle((Element) spans.item(i), classes);
			}

			NodeList paras = document.getElementsByTagName("p");
			for (int i = 0; i < paras.getLength(); ++i) {
				pullElementStyle((Element) paras.item(i), classes);
			}

			for (Enumeration<String> keys = classes.keys(); keys.hasMoreElements();) {
				String style = keys.nextElement();
				String className = classes.get(style);
				StringBuilder styleText = new StringBuilder();
				styleText.append(".");
				styleText.append(className);
				styleText.append(" { ");
				styleText.append(style);
				styleText.append(" }\n");
				styleNode.appendChild(document.createTextNode(styleText.toString()));
			}

			TransformerFactory.newInstance().newTransformer().transform(new DOMSource(document), new StreamResult(System.out));
		} catch(Exception e) {}
		// System.out.print(document.getDocumentElement().toString());
		super.end();
	}

	public void title(String title) {
		Element titleNode = document.createElement("title");
		titleNode.appendChild(document.createTextNode(title));
		getHead().appendChild(titleNode);
	}

	public void line() {
		getCurrentTarget().appendChild(document.createElement("br"));
	}

	public void text(String text) {
		ensurePara();

		getCurrentTarget().appendChild(document.createTextNode(decode(text)));
	}

	public void endash() {
		outText("\u2013");
	}

	public void emdash() {
		outText("\u2014");
	}

	public void rquote() {
		outText("\u2019");
	}

	public void par() {
		if (getCurrentPara() == null) {
			addPara();
		}

		if (!getParaState().isInTable()) {
			setCurrentTable(null);
		}

		updateParaState();
		if (getCurrentPara().getFirstChild() == null) {
			getCurrentPara().appendChild(document.createTextNode("\u00a0"));
		}
		setCurrentPara(null);
	}

	public void tab() {
		ensurePara();
		Element span = document.createElement("span");
		span.appendChild(document.createTextNode("\u00a0"));
		span.setAttribute("style", "display: inline-block; width: " + (getProgramState().getTabWidth() / 20) + "pt;");
		getCurrentTarget().appendChild(span);
	}

	public void intbl() {
		super.intbl();

		ensurePara();
		ensureCell();
		getCurrentCell().appendChild(getCurrentPara());
	}

	public void cell() {
		super.cell();
		setCurrentPara(null);
		setCurrentCell(null);
	}

	public void row() {
		super.row();
		setCurrentPara(null);
		setCurrentCell(null);
		setCurrentRow(null);
	}

	private void ensureCell() {
		ensureRow();
		if (getCurrentCell() == null) {
			Element cell = document.createElement("td");
			setCurrentCell(cell);
			getCurrentRow().appendChild(cell);
		}
	}

	private void ensureRow() {
		ensureTable();
		if (getCurrentRow() == null) {
			Element row = document.createElement("tr");
			setCurrentRow(row);
			getCurrentTable().appendChild(row);
		}
	}

	private void ensureTable() {
		if (getCurrentTable() == null) {
			Element table = document.createElement("table");
			setCurrentTable(table);
			getBody().appendChild(table);
		}
	}

	private void pullElementStyle(Element element, Hashtable<String, String> classes) {
		String style = element.getAttribute("style");

		if (style.length() > 0) {
			element.removeAttribute("style");

			String className;
			if (classes.containsKey(style)) {
				className = classes.get(style);
			} else {
				StringBuffer buf = new StringBuffer();
				buf.append("c");
				buf.append(nextClass++);
				className = buf.toString();
				classes.put(style, className);
			}

			element.setAttribute("class", className);
		}
	}

	public void ensurePara() {
		if (getCurrentPara() == null) {
			addPara();

			updateTarget();
		}
	}

	public void addPara() {
		Element para = document.createElement("p");
		setCurrentPara(para);
		Element container = getBody();
		if (getCurrentCell() != null) {
			container = getCurrentCell();
		}
		container.appendChild(document.createTextNode("\n"));
		container.appendChild(para);
	}

	public void updateTarget() {
		String style = StateStyle.toString(getState(), getDefState());

		if (getCurrentTarget() != null) {
			Element currentTarget = getCurrentTarget();
			if (currentTarget.getTagName().equals("span") && currentTarget.getFirstChild() == null) {
				Node para = currentTarget.getParentNode();
				para.removeChild(currentTarget);
			}
		}

		if (style.length() > 0) {
			Element newTarget = document.createElement("span");
			newTarget.setAttribute("style", style);

			setCurrentTarget(newTarget);
			getCurrentPara().appendChild(getCurrentTarget());
		} else {
			setCurrentTarget(getCurrentPara());
		}
	}

	public void updateState() {
		if (getCurrentPara() == null) {
			ensurePara();
		} else {
			updateTarget();
		}
	}

	public void updateParaState() {
		ensurePara();

		String style = ParaStyle.toString(getParaState(), getDefParaState());
		if (style.length() > 0) {
			getCurrentPara().setAttribute("style", style);
		}
	}

	public void outText(String text) {
		ensurePara();

		getCurrentTarget().appendChild(document.createTextNode(text));
	}

	Element getHtml() {
		return htmlNode;
	}

	Element getHead() {
		return headNode;
	}

	Element getBody() {
		return bodyNode;
	}

	Element getCurrentPara() {
		return currentPara;
	}

	void setCurrentPara(Element para) {
		currentPara = para;
	}

	Element getCurrentTarget() {
		return currentTarget;
	}

	void setCurrentTarget(Element target) {
		currentTarget = target;
	}

	Element getCurrentCell() {
		return currentCell;
	}

	void setCurrentCell(Element cell) {
		currentCell = cell;
	}

	Element getCurrentRow() {
		return currentRow;
	}

	void setCurrentRow(Element row) {
		currentRow = row;
	}

	Element getCurrentTable() {
		return currentTable;
	}

	void setCurrentTable(Element table) {
		currentTable = table;
	}
}
