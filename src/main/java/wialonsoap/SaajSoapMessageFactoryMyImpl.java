package wialonsoap;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.Iterator;
import java.util.StringTokenizer;

import javax.xml.soap.MimeHeaders;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;

import org.springframework.util.StringUtils;
import org.springframework.ws.InvalidXmlException;
import org.springframework.ws.soap.SoapMessageCreationException;
import org.springframework.ws.soap.saaj.SaajSoapMessage;
import org.springframework.ws.soap.saaj.SaajSoapMessageFactory;
import org.springframework.ws.transport.TransportConstants;
import org.springframework.ws.transport.TransportInputStream;
import org.xml.sax.SAXParseException;

public class SaajSoapMessageFactoryMyImpl extends SaajSoapMessageFactory {

	private boolean langAttributeOnSoap11FaultString = true;

	@Override
	public SaajSoapMessage createWebServiceMessage(InputStream inputStream) throws IOException {
		MimeHeaders mimeHeaders = parseMimeHeaders(inputStream);
		try {
			inputStream = checkForUtf8ByteOrderMark(inputStream);
			SOAPMessage saajMessage = super.getMessageFactory().createMessage(mimeHeaders, inputStream);
			saajMessage.getSOAPPart().getEnvelope();
			postProcess(saajMessage);
			return new SaajSoapMessage(saajMessage, langAttributeOnSoap11FaultString, super.getMessageFactory());
		} catch (SOAPException ex) {
			// SAAJ 1.3 RI has a issue with handling multipart XOP content types which contain "startinfo" rather than
			// "start-info", so let's try and do something about it
			String contentType = StringUtils
					.arrayToCommaDelimitedString(mimeHeaders.getHeader(TransportConstants.HEADER_CONTENT_TYPE));
			if (contentType.contains("startinfo")) {
				contentType = contentType.replace("startinfo", "start-info");
				mimeHeaders.setHeader(TransportConstants.HEADER_CONTENT_TYPE, contentType);
				try {
					SOAPMessage saajMessage = super.getMessageFactory().createMessage(mimeHeaders, inputStream);
					postProcess(saajMessage);
					return new SaajSoapMessage(saajMessage,
							langAttributeOnSoap11FaultString);
				} catch (SOAPException e) {
					// fall-through
				}
			}
			SAXParseException parseException = getSAXParseException(ex);
			if (parseException != null) {
				throw new InvalidXmlException("Could not parse XML", parseException);
			} else {
				throw new SoapMessageCreationException(
						"Could not create message from InputStream: " + ex.getMessage(),
						ex);
			}
		}
	}

	private MimeHeaders parseMimeHeaders(InputStream inputStream) throws IOException {
		MimeHeaders mimeHeaders = new MimeHeaders();
		if (inputStream instanceof TransportInputStream) {
			TransportInputStream transportInputStream = (TransportInputStream) inputStream;
			for (Iterator<String> headerNames = transportInputStream.getHeaderNames(); headerNames.hasNext();) {
				String headerName = headerNames.next();

				if (headerName.toLowerCase().equals("content-type")) {
					StringTokenizer tokenizer = new StringTokenizer("text/xml", ",");
					while (tokenizer.hasMoreTokens()) {
						mimeHeaders.addHeader(headerName, tokenizer.nextToken().trim());
					}
				} else {
					for (Iterator<String> headerValues = transportInputStream.getHeaders(headerName); headerValues
							.hasNext();) {
						String headerValue = headerValues.next();
						StringTokenizer tokenizer = new StringTokenizer(headerValue, ",");
						while (tokenizer.hasMoreTokens()) {
							mimeHeaders.addHeader(headerName, tokenizer.nextToken().trim());
						}
					}
				}
			}
		}
		return mimeHeaders;
	}

	private InputStream checkForUtf8ByteOrderMark(InputStream inputStream) throws IOException {
		PushbackInputStream pushbackInputStream = new PushbackInputStream(new BufferedInputStream(inputStream), 3);
		byte[] bytes = new byte[3];
		int bytesRead = 0;
		while (bytesRead < bytes.length) {
			int n = pushbackInputStream.read(bytes, bytesRead, bytes.length - bytesRead);
			if (n > 0) {
				bytesRead += n;
			} else {
				break;
			}
		}
		if (bytesRead > 0) {
			// check for the UTF-8 BOM, and remove it if there. See SWS-393
			if (!isByteOrderMark(bytes)) {
				pushbackInputStream.unread(bytes, 0, bytesRead);
			}
		}
		return pushbackInputStream;
	}

	private boolean isByteOrderMark(byte[] bytes) {
		return bytes.length == 3 && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF;
	}

	private SAXParseException getSAXParseException(Throwable ex) {
		if (ex instanceof SAXParseException) {
			return (SAXParseException) ex;
		} else if (ex.getCause() != null) {
			return getSAXParseException(ex.getCause());
		} else {
			return null;
		}
	}
}
