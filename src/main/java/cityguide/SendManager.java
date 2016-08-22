package cityguide;

import java.io.IOException;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Queue;
import java.util.TimeZone;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
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
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import ru.fors.udo.telemetry.webservice.TelemetryBa;
import ru.fors.udo.telemetry.webservice.TelemetryWithDetails;

@Service
public class SendManager {
	private static final Logger logger = LoggerFactory.getLogger(SendManager.class);

	private Queue<TelemetryBa> items = new ArrayDeque<>();
	private Object lock1 = new Object();

	private int CITY_GUIDE_SENDERS = 10;
	private int CITY_GUIDE_SIZE = 30;
	private String CITY_GUIDE_URL = "http://service.probki.net/xmltrack/api/nytrack";

	private List<Thread> threads;
	private volatile boolean processing = true;

	// private EvictingQueue<TelemetryBa> its = EvictingQueue.create(30000);

	public void addTelemetry(TelemetryBa telemetry) {
		synchronized (lock1) {
			items.add(telemetry);
			lock1.notifyAll();
		}

	}

	public void addTelemetryWithDetails(List<TelemetryWithDetails> listTelemetryWithDetails) {
		synchronized (lock1) {
			for (TelemetryWithDetails telemetryWithDetails : listTelemetryWithDetails) {
				items.add(telemetryWithDetails.getTelemetry());
			}
			lock1.notifyAll();
		}
	}

	@PostConstruct
	public void startSender() {
		logger.debug("start threads");
		processing = true;
		threads = new ArrayList<>(CITY_GUIDE_SENDERS);
		for (int i = 0; i < CITY_GUIDE_SENDERS; i++) {
			Runnable senderRunnable = new SenderRunnableSimple();
			Thread thread = new Thread(senderRunnable);
			thread.start();
			threads.add(thread);
		}
	}

	@PreDestroy
	public void stopSender() {
		processing = false;
		for (Thread thread : threads) {
			try {
				thread.join();
			} catch (InterruptedException e) {
			}
		}
		logger.debug("threads stoped");
	}

	class SenderRunnable implements Runnable {

		@Override
		public void run() {
			try {

				while (processing) {
					List<TelemetryBa> list = new ArrayList<>();
					synchronized (lock1) {
						logger.debug("SenderRunnable in lock1");

						for (int i = 0; i < CITY_GUIDE_SIZE; i++) {
							TelemetryBa item = items.poll();
							if (item == null) {
								lock1.notifyAll();
								break;
							}
							list.add(item);
						}
						lock1.notifyAll();
					}

					if (list.size() > 0) {
						try {
							Document doc = createCityGuideMessage(list);
							sendCityGuideMessage(doc);
						} catch (ParserConfigurationException | TransformerFactoryConfigurationError
								| TransformerException
								| IOException e) {
							logger.error("SendManager", e);
						}
					} else {
						try {
							Thread.sleep(1000);
						} catch (InterruptedException e) {
						}
					}
				}
			} catch (Exception e) {
				logger.error("ROOT SendManager", e);
			}
		}

	}

	class SenderRunnableSimple implements Runnable {

		@Override
		public void run() {
			try {

				while (processing) {
					TelemetryBa item = null;
					synchronized (lock1) {
						item = items.poll();
						lock1.notifyAll();
					}

					if (item != null) {
						try {
							Document doc = createCityGuideMessage(item);
							sendCityGuideMessage(doc);
						} catch (ParserConfigurationException | TransformerFactoryConfigurationError
								| TransformerException
								| IOException e) {
							logger.error("SendManager", e);
						}
					} else {
						try {
							Thread.sleep(1000);
						} catch (InterruptedException e) {
						}
					}
				}
			} catch (Exception e) {
				logger.error("ROOT SendManager", e);
			}
		}

	}

	private Document createCityGuideMessage(TelemetryBa telemetry) throws ParserConfigurationException {
		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
		Document doc = docBuilder.newDocument();

		Element pointsElement = doc.createElement("points");
		pointsElement.setAttribute("id", telemetry.getGpsCode());
		doc.appendChild(pointsElement);

		Element pointElement = doc.createElement("point");
		pointElement.setAttribute("speed", Integer.toString(Double.valueOf(telemetry.getSpeed()).intValue()));
		pointElement.setAttribute("lat", Double.toString(telemetry.getCoordX()));
		pointElement.setAttribute("lon", Double.toString(telemetry.getCoordY()));
		pointElement.setAttribute("isotime",
				parseToIsoTime(telemetry.getDate().toGregorianCalendar().getTime()));
		pointsElement.appendChild(pointElement);

		return doc;
	}

	private Document createCityGuideMessage(List<TelemetryBa> list) throws ParserConfigurationException {
		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
		Document doc = docBuilder.newDocument();

		for (TelemetryBa telemetry : list) {
			Element pointsElement = doc.createElement("points");
			pointsElement.setAttribute("id", telemetry.getGpsCode());
			doc.appendChild(pointsElement);

			Element pointElement = doc.createElement("point");
			pointElement.setAttribute("speed", Integer.toString(Double.valueOf(telemetry.getSpeed()).intValue()));
			pointElement.setAttribute("lat", Double.toString(telemetry.getCoordX()));
			pointElement.setAttribute("lon", Double.toString(telemetry.getCoordY()));
			pointElement.setAttribute("isotime",
					parseToIsoTime(telemetry.getDate().toGregorianCalendar().getTime()));
			pointsElement.appendChild(pointElement);
		}

		return doc;
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
	}

	private String parseToIsoTime(Date time) {
		// 2014-10-23T11:01:57+0300
		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
		df.setTimeZone(TimeZone.getTimeZone("UTC"));
		return df.format(time);
	}

	private String nodeToString(Node node) throws TransformerFactoryConfigurationError, TransformerException {
		StringWriter writer = new StringWriter();
		Transformer transformer = TransformerFactory.newInstance().newTransformer();
		transformer.transform(new DOMSource(node), new StreamResult(writer));
		return writer.toString();
	}

}
