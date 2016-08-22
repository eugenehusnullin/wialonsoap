package ru.fors.udo.telemetry.webservice;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

import cityguide.SendManager;

@Endpoint
public class TelemetryService {
	private static final String NAMESPACE_URI = "http://webservice.telemetry.udo.fors.ru/";
	//private static final Logger logger = LoggerFactory.getLogger(TelemetryService.class);

	@Autowired
	private SendManager sendManager;

	@PayloadRoot(namespace = NAMESPACE_URI, localPart = "storeTelemetryList")
	@ResponsePayload
	public StoreTelemetryListResponse storeTelemetryList(@RequestPayload StoreTelemetryList list) {
		sendManager.addTelemetryWithDetails(list.getTelemetryWithDetails());
		return new StoreTelemetryListResponse();
	}

	@PayloadRoot(namespace = NAMESPACE_URI, localPart = "storeTelemetry")
	@ResponsePayload
	public StoreTelemetryResponse storeTelemetry(@RequestPayload StoreTelemetry telemetry) {
		sendManager.addTelemetry(telemetry.getTelemetry());
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
