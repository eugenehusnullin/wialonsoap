package ru.fors.udo.telemetry.webservice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

@Endpoint
public class TelemetryService {
	private static final Logger logger = LoggerFactory.getLogger(TelemetryService.class);

	private static final String NAMESPACE_URI = "http://webservice.telemetry.udo.fors.ru/";

	@PayloadRoot(namespace = NAMESPACE_URI, localPart = "storeTelemetryList")
	@ResponsePayload
	public StoreTelemetryListResponse storeTelemetryList(@RequestPayload StoreTelemetryList list) {
		logger.debug("enter storeTelemetryList");
		return new StoreTelemetryListResponse();
	}

	@PayloadRoot(namespace = NAMESPACE_URI, localPart = "storeTelemetry")
	@ResponsePayload
	public StoreTelemetryResponse storeTelemetry(@RequestPayload StoreTelemetry telemetry) {
		return new StoreTelemetryResponse();
	}

	@PayloadRoot(namespace = NAMESPACE_URI, localPart = "testService")
	@ResponsePayload
	public TestServiceResponse testService(@RequestPayload TestService test) {
		TestServiceResponse resp = new TestServiceResponse();
		resp.setResult(test.getA() + test.getB());
		return resp;
	}
}
