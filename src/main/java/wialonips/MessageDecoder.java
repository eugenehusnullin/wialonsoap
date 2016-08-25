package wialonips;

import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.AttributeKey;

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

@Sharable
public class MessageDecoder extends ChannelInboundHandlerAdapter {
	private static final Logger logger = LoggerFactory.getLogger(MessageDecoder.class);
	private Charset asciiCharset = Charset.forName("ASCII");
	private MessageEncoder messageEncoder = new MessageEncoder();

	public final static AttributeKey<String> AK_ID = AttributeKey.valueOf("id");
	public final static AttributeKey<String> AK_REMAINS = AttributeKey.valueOf("remains");
	public final static AttributeKey<LocalDateTime> AK_LASTSEND = AttributeKey.valueOf("lastsend");

	private ExecutorService executor;

	public MessageDecoder(ExecutorService executor) {
		this.executor = executor;
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		ByteBuf buf = (ByteBuf) msg;
		byte[] messageBytes = new byte[buf.writerIndex()];
		buf.readBytes(messageBytes);
		buf.release();
		String str = new String(messageBytes, asciiCharset);
		logger.debug(str);

		LocalDateTime lastSend = ctx.channel().attr(AK_LASTSEND).get();
		if (lastSend != null && lastSend.isAfter(LocalDateTime.now().minusMinutes(1))) {
			logger.debug("decided NOT to send");
			return;
		} else {
			logger.debug("decided to send");
		}

		String remains = ctx.channel().attr(AK_REMAINS).get();
		if (remains != null) {
			str = remains.concat(str);
			ctx.channel().attr(AK_REMAINS).set(null);
		}

		if (!str.endsWith("\r\n")) {
			ctx.channel().attr(AK_REMAINS).set(str);
			return;
		}

		String imei = ctx.channel().attr(AK_ID).get();
		String[] lines = str.split("\r\n");
		if (lines[0].charAt(1) == 'L') {
			String[] values = lines[0].split(";");
			imei = values[0].substring(3);

			if (!validImei(imei)) {
				ctx.disconnect();
				return;
			}
			ctx.channel().attr(AK_ID).set(imei);
		}

		final String imei2 = imei;
		executor.submit(() -> {
			messageEncoder.encode(imei2, lines);
		});

		ctx.channel().attr(AK_LASTSEND).set(LocalDateTime.now());
	}

	private boolean validImei(String imei) {
		return !imei.equals("NA");
	}

	// @Override
	// public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
	// UUID uuid = UUID.randomUUID();
	// ctx.channel().attr(AK_UUID).set(uuid);
	//
	// logger.debug(" channelRegistered - " + uuid.toString());
	// super.channelRegistered(ctx);
	// }
	//
	// @Override
	// public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
	// UUID uuid = ctx.channel().attr(AK_UUID).get();
	// logger.debug("channelUnregistered - " + uuid.toString());
	// super.channelUnregistered(ctx);
	// }

}
