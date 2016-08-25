package wialonips;

import java.io.IOException;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.TimeZone;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/*
 * Example cityguide message:
 * 
<?xml version="1.0" encoding="UTF-8"?><points id="353386061524845"><point isotime="2016-08-20T19:24:00+0000" lat="56.699923" lon="60.550068" speed="27"/></points>
<?xml version="1.0" encoding="UTF-8"?><points id="353386060341035"><point isotime="2016-08-20T20:02:00+0000" lat="55.763631" lon="37.629097" speed="5"/></points>
 */

/*
 * Example wialon ips message:
 * 
#L#353386062153263;NA
#D#230816;150237;5546.7603;N;03736.0574;E;0;337;147.000000;255;NA;NA;NA;NA;NA;accuracy:2:0.000000,csq1:2:80.000000,nsq1:2:7.000000,din1:2:1.000000,din2:2:0.000000,bat1:2:100.000000,pwr1:2:14.200000,rel1:2:0.000000
#D#230816;150305;5546.7592;N;03736.0579;E;5;337;148.300000;255;NA;NA;NA;NA;NA;accuracy:2:0.000000,csq1:2:90.000000,nsq1:2:7.000000,din1:2:1.000000,din2:2:0.000000,bat1:2:100.000000,pwr1:2:14.300000,rel1:2:0.000000
#D#230816;150306;5546.7602;N;03736.0562;E;10;318;148.600000;255;NA;NA;NA;NA;NA;accuracy:2:0.000000,csq1:2:90.000000,nsq1:2:7.000000,din1:2:1.000000,din2:2:0.000000,bat1:2:100.000000,pwr1:2:14.300000,rel1:2:0.000000
#D#230816;150312;5546.7866;N;03736.0310;E;36;334;149.000000;255;NA;NA;NA;NA;NA;accuracy:2:0.000000,csq1:2:87.000000,nsq1:2:7.000000,din1:2:1.000000,din2:2:0.000000,bat1:2:99.000000,pwr1:2:14.300000,rel1:2:0.000000
#D#230816;150311;5546.7813;N;03736.0350;E;35;333;148.800000;255;NA;NA;NA;NA;NA;accuracy:2:0.000000
#D#230816;150333;5546.8746;N;03735.9607;
 */

public class MessageEncoder {
	private static final Logger logger = LoggerFactory.getLogger(MessageEncoder.class);
	private static String CITY_GUIDE_URL = "http://service.probki.net/xmltrack/api/nytrack";

	public void encode(String imei, String[] lines) {
		try {
			Document doc = createDocument();
			Element pointsElement = createPointsElement(doc, imei);

			boolean empty = true;
			for (String line : lines) {
				switch (line.charAt(1)) {
				case 'D':
					String[] values = line.split(";");
					if (values.length < 6
							|| values[0].substring(3).equals("NA")
							|| values[1].equals("NA")
							|| values[2].equals("NA")
							|| values[4].equals("NA")
							|| values[6].equals("NA")) {
						continue;
					}

					ZonedDateTime zdt = converUtcDate(values[0].substring(3), values[1]);
					if (zdt.isBefore(ZonedDateTime.now(ZoneId.of("UTC")).minusMinutes(5))) {
						continue;
					}
					String isoTime = parseToIsoTime(Date.from(zdt.toInstant()));

					Element pointElement = doc.createElement("point");
					pointElement.setAttribute("speed", values[6]);
					pointElement.setAttribute("lat", Double.toString(convertGprmcCoord(values[2])));
					pointElement.setAttribute("lon", Double.toString(convertGprmcCoord(values[4])));
					pointElement.setAttribute("isotime", isoTime);
					pointsElement.appendChild(pointElement);

					empty = false;
					break;

				default:
					break;
				}
			}

			if (!empty) {
				sendCityGuideMessage(doc);
			}
		} catch (Exception e) {
			logger.error("encode error", e);
		}
	}

	private Document createDocument() throws ParserConfigurationException {
		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
		Document doc = docBuilder.newDocument();
		return doc;
	}

	private Element createPointsElement(Document doc, String imei) {
		Element pointsElement = doc.createElement("points");
		pointsElement.setAttribute("id", imei);
		doc.appendChild(pointsElement);
		return pointsElement;
	}

	private ZonedDateTime converUtcDate(String date, String time) {
		ZonedDateTime zdt = ZonedDateTime.of(
				Integer.parseInt(date.substring(4)) + 2000,
				Integer.parseInt(date.substring(2, 4)),
				Integer.parseInt(date.substring(0, 2)),
				Integer.parseInt(time.substring(0, 2)),
				Integer.parseInt(time.substring(2, 4)),
				Integer.parseInt(time.substring(4, 6)),
				0,
				ZoneId.of("UTC"));

		return zdt;
	}

	private String parseToIsoTime(Date time) {
		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
		df.setTimeZone(TimeZone.getTimeZone("UTC"));
		return df.format(time);
	}

	private double convertGprmcCoord(String coord) {
		double p = Double.parseDouble(coord);
		int p1 = (int) (p / 100);
		double p2 = (p - (p1 * 100)) / 60;
		return p1 + p2;
	}

	private void sendCityGuideMessage(Document doc) throws TransformerFactoryConfigurationError, TransformerException,
			IOException {
		String raw = nodeToString(doc.getFirstChild());
		logger.debug(raw);

		URL url = new URL(CITY_GUIDE_URL);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setRequestMethod("POST");
		connection.setRequestProperty("Content-Type", "text/plain");
		connection.setDoOutput(true);
		IOUtils.write(raw, connection.getOutputStream(), "UTF-8");
		connection.getOutputStream().flush();
		connection.getOutputStream().close();

		if (connection.getResponseCode() != 200) {
			String reason = IOUtils.toString(connection.getInputStream());
			logger.warn("CityGuideHandler error send point. Error code=" + connection.getResponseCode() + ", reason: "
					+ reason);
		}
		connection.disconnect();
	}

	private String nodeToString(Node node) throws TransformerFactoryConfigurationError, TransformerException {
		StringWriter writer = new StringWriter();
		Transformer transformer = TransformerFactory.newInstance().newTransformer();
		transformer.transform(new DOMSource(node), new StreamResult(writer));
		return writer.toString();
	}
}
